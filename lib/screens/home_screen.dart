import 'dart:async';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../services/native_bridge.dart';
import 'stats_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  int _minutes = 15;
  int _seconds = 0;
  bool _accessibilityEnabled = false;
  Map<String, dynamic> _currentState = {'state': 'IDLE', 'timeRemaining': 0};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _init();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
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
    NativeBridge.stateStream.listen((state) {
      if (mounted) {
        setState(() => _currentState = state);
      }
    });

    // Poll stats occasionally or on load
  }

  Future<void> _checkPermissions() async {
    final enabled = await NativeBridge.checkAccessibility();
    if (mounted) setState(() => _accessibilityEnabled = enabled);
  }

  Future<void> _syncState() async {
    final status = await NativeBridge.getStatus();
    if (mounted) setState(() => _currentState = status);
  }

  void _start() {
    int totalSeconds = (_minutes * 60) + _seconds;
    if (totalSeconds > 0) {
      NativeBridge.startTimer(totalSeconds);
    }
  }

  void _stop() {
    NativeBridge.stopTimer();
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
              ] else ...[
                // Input
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    _buildNumberPicker(
                      _minutes,
                      (v) => setState(() => _minutes = v),
                      "MIN",
                    ),
                    const SizedBox(width: 20),
                    Text(
                      ":",
                      style: TextStyle(fontSize: 40, color: Colors.grey[800]),
                    ),
                    const SizedBox(width: 20),
                    _buildNumberPicker(
                      _seconds,
                      (v) => setState(() => _seconds = v),
                      "SEC",
                    ),
                  ],
                ),
              ],

              const Spacer(),

              // Warning
              if (!_accessibilityEnabled)
                GestureDetector(
                  onTap: () => NativeBridge.openAccessibilitySettings(),
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 8,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.red.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: Colors.red.withOpacity(0.3)),
                    ),
                    child: const Text(
                      "Hardware control requires Accessibility. Tap to enable.",
                      style: TextStyle(color: Colors.redAccent, fontSize: 12),
                    ),
                  ),
                ),

              const SizedBox(height: 32),

              // Button
              SizedBox(
                width: double.infinity,
                height: 60,
                child: ElevatedButton(
                  onPressed: isRunning ? _stop : _start,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: isRunning
                        ? Colors.grey[900]
                        : const Color(0xFF00E070),
                    foregroundColor: isRunning ? Colors.white : Colors.black,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  child: Text(
                    isRunning ? "STOP" : "START FOCUS",
                    style: const TextStyle(fontSize: 18, letterSpacing: 1),
                  ),
                ),
              ),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildNumberPicker(int value, Function(int) onChanged, String label) {
    return Column(
      children: [
        IconButton(
          icon: const Icon(Icons.keyboard_arrow_up),
          onPressed: () => onChanged((value + 1) % 60),
        ),
        Text(
          value.toString().padLeft(2, '0'),
          style: GoogleFonts.outfit(fontSize: 60, fontWeight: FontWeight.w300),
        ),
        IconButton(
          icon: const Icon(Icons.keyboard_arrow_down),
          onPressed: () => onChanged((value - 1 + 60) % 60),
        ),
        Text(label, style: const TextStyle(color: Colors.grey, fontSize: 10)),
      ],
    );
  }

  String _formatTime(int seconds) {
    final m = seconds ~/ 60;
    final s = seconds % 60;
    return '${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
  }
}
