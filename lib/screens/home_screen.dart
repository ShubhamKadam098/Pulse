import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../services/native_bridge.dart';
import 'stats_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  int _minutes = 5;
  int _seconds = 0;
  bool _accessibilityEnabled = false;
  Map<String, dynamic> _currentState = {
    'state': 'IDLE',
    'timeRemaining': 0,
    'acknowledgements': 0,
  };

  late FixedExtentScrollController _minController;
  late FixedExtentScrollController _secController;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _minController = FixedExtentScrollController(initialItem: _minutes);
    _secController = FixedExtentScrollController(initialItem: _seconds);
    _init();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _minController.dispose();
    _secController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkPermissions();
      _syncState();
    }
  }

  void _init() async {
    await _checkPermissions();
    await _syncState();

    // Load last used duration
    final prefs = await SharedPreferences.getInstance();
    final savedMins = prefs.getInt('last_minutes');
    final savedSecs = prefs.getInt('last_seconds');

    if (mounted && (savedMins != null || savedSecs != null)) {
      setState(() {
        if (savedMins != null) _minutes = savedMins;
        if (savedSecs != null) _seconds = savedSecs;

        // Update controllers to match loaded values
        _minController.jumpToItem(_minutes);
        _secController.jumpToItem(_seconds);
      });
    }

    NativeBridge.stateStream.listen((state) {
      if (mounted) {
        setState(() => _currentState = state);
      }
    });

    // Poll stats occasionally or on load
  }

  Future<void> _checkPermissions() async {
    final acc = await NativeBridge.checkAccessibility();
    if (mounted) {
      setState(() {
        _accessibilityEnabled = acc;
      });
    }
  }

  Future<void> _syncState() async {
    final status = await NativeBridge.getStatus();
    if (mounted) setState(() => _currentState = status);
  }

  void _start() async {
    int totalSeconds = (_minutes * 60) + _seconds;
    if (totalSeconds > 0) {
      // Save duration for next time
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt('last_minutes', _minutes);
      await prefs.setInt('last_seconds', _seconds);

      NativeBridge.startTimer(totalSeconds);
    }
  }

  void _stop() {
    NativeBridge.stopTimer();
  }

  void _togglePause() {
    NativeBridge.togglePause();
  }

  @override
  Widget build(BuildContext context) {
    final state = _currentState['state'] as String;
    final timeRem = _currentState['timeRemaining'] as int;
    final isRunning = state != 'IDLE';

    // Determine color based on state
    Color statusColor = Theme.of(context).colorScheme.primary;
    if (state == 'VIBRATING') statusColor = Colors.redAccent;
    if (state == 'PAUSED') statusColor = Colors.orangeAccent;

    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            children: [
              // Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    "PULSE",
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      letterSpacing: 2,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.bar_chart),
                    onPressed: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const StatsScreen()),
                    ),
                  ),
                ],
              ),

              const Spacer(),

              // Timer Display
              if (isRunning) ...[
                Text(
                  _formatTime(timeRem),
                  style: GoogleFonts.outfit(
                    fontSize: 80,
                    fontWeight: FontWeight.w200,
                    color: statusColor,
                    fontFeatures: [const FontFeature.tabularFigures()],
                  ),
                ),
                Text(
                  state,
                  style: TextStyle(
                    color: statusColor.withOpacity(0.7),
                    letterSpacing: 1.5,
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: statusColor.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(
                        "${_currentState['acknowledgements'] ?? 0} ACKNOWLEDGED",
                        style: TextStyle(
                          color: statusColor,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 1,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.redAccent.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(
                        "${_currentState['distractions'] ?? 0} DISTRACTED",
                        style: const TextStyle(
                          color: Colors.redAccent,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 1,
                        ),
                      ),
                    ),
                  ],
                ),
              ] else ...[
                // Unified Apple-style Picker Container
                Stack(
                  alignment: Alignment.center,
                  children: [
                    // Shared selection pill background
                    Container(
                      height: 54,
                      width: 260,
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.08),
                        borderRadius: BorderRadius.circular(16),
                      ),
                    ),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        _buildWheelPicker(
                          _minController,
                          60,
                          (v) => setState(() => _minutes = v),
                          "MIN",
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 8),
                          child: Text(
                            ":",
                            style: GoogleFonts.outfit(
                              fontSize: 32,
                              color: Colors.white24,
                              fontWeight: FontWeight.w300,
                            ),
                          ),
                        ),
                        _buildWheelPicker(
                          _secController,
                          60,
                          (v) => setState(() => _seconds = v),
                          "SEC",
                        ),
                      ],
                    ),
                  ],
                ),
              ],

              const Spacer(),

              // Warning
              if (!_accessibilityEnabled)
                GestureDetector(
                  onTap: () => NativeBridge.openAccessibilitySettings(),
                  child: _buildWarning(
                    "Hardware control requires Accessibility. Tap to enable.",
                  ),
                ),

              const SizedBox(height: 32),

              // Buttons
              if (isRunning) ...[
                Row(
                  children: [
                    Expanded(
                      flex: 2,
                      child: SizedBox(
                        height: 60,
                        child: ElevatedButton(
                          onPressed: _togglePause,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: state == 'PAUSED'
                                ? const Color(0xFF00E070)
                                : Colors.orangeAccent.withOpacity(0.2),
                            foregroundColor: state == 'PAUSED'
                                ? Colors.black
                                : Colors.orangeAccent,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          child: Text(
                            state == 'PAUSED' ? "RESUME" : "PAUSE",
                            style: const TextStyle(
                              fontSize: 16,
                              letterSpacing: 1,
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      flex: 1,
                      child: SizedBox(
                        height: 60,
                        child: ElevatedButton(
                          onPressed: _stop,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.grey[900],
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          child: const Text(
                            "STOP",
                            style: TextStyle(fontSize: 14, letterSpacing: 1),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ] else ...[
                SizedBox(
                  width: double.infinity,
                  height: 64,
                  child: ElevatedButton(
                    onPressed: _start,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF00E070), // Focus Green
                      foregroundColor: Colors.black,
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(18),
                      ),
                    ),
                    child: Text(
                      "START FOCUS",
                      style: GoogleFonts.outfit(
                        fontSize: 18,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 0.5,
                      ),
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildWheelPicker(
    FixedExtentScrollController controller,
    int itemCount,
    Function(int) onChanged,
    String label,
  ) {
    return Column(
      children: [
        Stack(
          alignment: Alignment.center,
          children: [
            GestureDetector(
              onTap: () async {
                final result = await showDialog<int>(
                  context: context,
                  builder: (context) {
                    int val = controller.selectedItem;
                    return AlertDialog(
                      backgroundColor: const Color(0xFF1C1C1E),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                      title: Text(
                        "Set $label",
                        style: GoogleFonts.outfit(
                          color: Colors.white,
                          fontSize: 18,
                        ),
                      ),
                      content: TextField(
                        autofocus: true,
                        keyboardType: TextInputType.number,
                        textAlign: TextAlign.center,
                        style: GoogleFonts.outfit(
                          color: Colors.white,
                          fontSize: 32,
                        ),
                        decoration: const InputDecoration(
                          hintText: "00",
                          hintStyle: TextStyle(color: Colors.white12),
                          border: InputBorder.none,
                        ),
                        textInputAction: TextInputAction.done,
                        onChanged: (v) => val = int.tryParse(v) ?? val,
                        onSubmitted: (v) {
                          val = int.tryParse(v) ?? val;
                          Navigator.pop(context, val);
                        },
                      ),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.pop(context),
                          child: const Text(
                            "CANCEL",
                            style: TextStyle(color: Colors.grey),
                          ),
                        ),
                        TextButton(
                          onPressed: () => Navigator.pop(context, val),
                          child: const Text(
                            "SET",
                            style: TextStyle(color: Color(0xFF00E070)),
                          ),
                        ),
                      ],
                    );
                  },
                );
                if (result != null) {
                  final finalVal = result.clamp(0, itemCount - 1);
                  controller.animateToItem(
                    finalVal,
                    duration: const Duration(milliseconds: 500),
                    curve: Curves.easeOutQuart,
                  );
                  onChanged(finalVal);
                }
              },
              child: SizedBox(
                height: 200,
                width: 100,
                child: ListWheelScrollView.useDelegate(
                  controller: controller,
                  itemExtent: 48,
                  perspective: 0.004,
                  diameterRatio: 1.2,
                  physics: const FixedExtentScrollPhysics(),
                  useMagnifier: true,
                  magnification: 1.15,
                  onSelectedItemChanged: (index) {
                    HapticFeedback.selectionClick();
                    onChanged(index);
                  },
                  childDelegate: ListWheelChildBuilderDelegate(
                    childCount: itemCount,
                    builder: (context, index) {
                      return Center(
                        child: Text(
                          index.toString().padLeft(2, '0'),
                          style: GoogleFonts.outfit(
                            fontSize: 36,
                            fontWeight: FontWeight.w300,
                            color: Colors.white.withOpacity(0.9),
                            fontFeatures: [const FontFeature.tabularFigures()],
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ),
            ),
            // Masking Gradients
            IgnorePointer(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    height: 70,
                    width: 100,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [Colors.black, Colors.black.withOpacity(0)],
                      ),
                    ),
                  ),
                  const SizedBox(height: 60),
                  Container(
                    height: 70,
                    width: 100,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.bottomCenter,
                        end: Alignment.topCenter,
                        colors: [Colors.black, Colors.black.withOpacity(0)],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildWarning(String text) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.red.withOpacity(0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.red.withOpacity(0.3)),
      ),
      child: Text(
        text,
        style: const TextStyle(color: Colors.redAccent, fontSize: 12),
        textAlign: TextAlign.center,
      ),
    );
  }

  String _formatTime(int seconds) {
    final m = seconds ~/ 60;
    final s = seconds % 60;
    return '${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
  }
}
