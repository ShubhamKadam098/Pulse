import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class NativeBridge {
  static const _channel = MethodChannel('com.pulse.app/channel');

  // Stream for updates
  static final _streamController =
      StreamController<Map<String, dynamic>>.broadcast();
  static Stream<Map<String, dynamic>> get stateStream =>
      _streamController.stream;

  static void init() {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onStateChanged') {
        final Map<String, dynamic> args = Map<String, dynamic>.from(
          call.arguments,
        );
        _streamController.add(args);
      }
    });
  }

  static Future<bool> startTimer(int seconds) async {
    try {
      await _channel.invokeMethod('startTimer', {'seconds': seconds});
      return true;
    } catch (e) {
      debugPrint(e.toString());
      return false;
    }
  }

  static Future<void> stopTimer() async {
    try {
      await _channel.invokeMethod('stopTimer');
    } catch (e) {
      debugPrint(e.toString());
    }
  }

  static Future<Map<String, dynamic>> getStatus() async {
    try {
      final result = await _channel.invokeMethod('getStatus');
      return Map<String, dynamic>.from(result);
    } catch (e) {
      return {'state': 'IDLE', 'timeRemaining': 0};
    }
  }

  static Future<bool> checkAccessibility() async {
    try {
      return await _channel.invokeMethod('checkAccessibility');
    } catch (e) {
      return false;
    }
  }

  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibilitySettings');
    } catch (e) {
      debugPrint(e.toString());
    }
  }

  static Future<Map<String, dynamic>> getStats() async {
    try {
      final result = await _channel.invokeMethod('getStats');
      return Map<String, dynamic>.from(result);
    } catch (e) {
      return {'total': 0};
    }
  }
}
