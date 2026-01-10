import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'screens/home_screen.dart';
import 'services/native_bridge.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  NativeBridge.init();
  runApp(const PulseApp());
}

class PulseApp extends StatelessWidget {
  const PulseApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Pulse',
      debugShowCheckedModeBanner: false,
      theme: _buildTheme(),
      home: const HomeScreen(),
    );
  }

  ThemeData _buildTheme() {
    final base = ThemeData.dark();
    return base.copyWith(
      scaffoldBackgroundColor: const Color(0xFF050505),
      colorScheme: const ColorScheme.dark(
        primary: Color(0xFF00E070),
        secondary: Color(0xFF202020),
        surface: Color(0xFF121212),
      ),
      textTheme: GoogleFonts.outfitTextTheme(
        base.textTheme,
      ).apply(bodyColor: Colors.white, displayColor: Colors.white),
    );
  }
}
