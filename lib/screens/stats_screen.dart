import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import '../services/native_bridge.dart';
import 'package:google_fonts/google_fonts.dart';

class StatsScreen extends StatelessWidget {
  const StatsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Statistics"),
        backgroundColor: Colors.transparent,
        elevation: 0,
      ),
      body: FutureBuilder<Map<String, dynamic>>(
        future: NativeBridge.getStats(),
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }

          final data = snapshot.data ?? {};
          final total = data['total'] ?? 0;
          final today = data['today'] ?? 0;
          final streak = data['streak'] ?? 0;
          final longest = data['longestStreak'] ?? 0;
          // weekly: [0, 2, 5, 1, ...] for last 7 days
          final weekly =
              (data['weekly'] as List<dynamic>?)
                  ?.map((e) => e as int)
                  .toList() ??
              [0, 0, 0, 0, 0, 0, 0];

          return SingleChildScrollView(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildGrid(context, total, today, streak, longest),
                const SizedBox(height: 32),
                Text(
                  "Last 7 Days",
                  style: GoogleFonts.outfit(fontSize: 18, color: Colors.grey),
                ),
                const SizedBox(height: 16),
                _buildChart(weekly),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildGrid(
    BuildContext context,
    int total,
    int today,
    int streak,
    int longest,
  ) {
    return GridView.count(
      crossAxisCount: 2,
      crossAxisSpacing: 16,
      mainAxisSpacing: 16,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      children: [
        _buildCard("Today", today.toString()),
        _buildCard("Streak", "$streak days"),
        _buildCard("Longest", "$longest days"),
        _buildCard("Total", total.toString()),
      ],
    );
  }

  Widget _buildCard(String title, String value) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF1E1E1E),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(title, style: const TextStyle(color: Colors.grey, fontSize: 14)),
          const SizedBox(height: 8),
          Text(
            value,
            style: GoogleFonts.outfit(
              fontSize: 32,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildChart(List<int> data) {
    return Container(
      height: 200,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF1E1E1E),
        borderRadius: BorderRadius.circular(16),
      ),
      child: BarChart(
        BarChartData(
          alignment: BarChartAlignment.spaceAround,
          maxY: (data.reduce((a, b) => a > b ? a : b) + 1).toDouble(),
          titlesData: FlTitlesData(
            show: true,
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                getTitlesWidget: (value, meta) {
                  return Text(
                    ['M', 'T', 'W', 'T', 'F', 'S', 'S'][value.toInt() % 7],
                    style: const TextStyle(color: Colors.grey, fontSize: 10),
                  );
                },
              ),
            ),
            leftTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false),
            ),
            topTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false),
            ),
            rightTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false),
            ),
          ),
          gridData: const FlGridData(show: false),
          borderData: FlBorderData(show: false),
          barGroups: List.generate(data.length, (index) {
            return BarChartGroupData(
              x: index,
              barRods: [
                BarChartRodData(
                  toY: data[index].toDouble(),
                  color: const Color(0xFF00E070),
                  width: 16,
                  borderRadius: BorderRadius.circular(4),
                  backDrawRodData: BackgroundBarChartRodData(show: false),
                ),
              ],
            );
          }),
        ),
      ),
    );
  }
}
