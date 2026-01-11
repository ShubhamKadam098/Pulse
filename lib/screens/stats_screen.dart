import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import '../services/native_bridge.dart';
import 'package:google_fonts/google_fonts.dart';

class StatsScreen extends StatelessWidget {
  const StatsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: Text(
          "STATISTICS",
          style: GoogleFonts.outfit(
            fontWeight: FontWeight.bold,
            letterSpacing: 2,
            fontSize: 16,
          ),
        ),
        centerTitle: true,
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 20),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: FutureBuilder<Map<String, dynamic>>(
        future: NativeBridge.getStats(),
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(
              child: CircularProgressIndicator(color: Color(0xFF00E070)),
            );
          }

          final data = snapshot.data ?? {};
          final totalMins = data['total'] ?? 0;
          final todayMins = data['today'] ?? 0;
          final streak = data['streak'] ?? 0;
          final longest = data['longestStreak'] ?? 0;
          final weekly =
              (data['weekly'] as List<dynamic>?)
                  ?.map((e) => e as int)
                  .toList() ??
              [0, 0, 0, 0, 0, 0, 0];

          return SingleChildScrollView(
            physics: const BouncingScrollPhysics(),
            padding: const EdgeInsets.symmetric(
              horizontal: 24.0,
              vertical: 16.0,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildSummaryCards(totalMins, todayMins, streak, longest),
                const SizedBox(height: 48),
                Text(
                  "FOCUS ACTIVITY",
                  style: GoogleFonts.outfit(
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    color: Colors.white24,
                    letterSpacing: 2,
                  ),
                ),
                const SizedBox(height: 16),
                _buildChartContainer(weekly),
                const SizedBox(height: 40),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildSummaryCards(int total, int today, int streak, int longest) {
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: _buildStatCard(
                "TODAY",
                "$today",
                "MINS",
                const Color(0xFF00E070),
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: _buildStatCard(
                "STREAK",
                "$streak",
                "DAYS",
                const Color(0xFF007AFF),
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: _buildStatCard(
                "FOCUS TIME",
                "$total",
                "TOTAL MINS",
                Colors.orangeAccent,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: _buildStatCard(
                "LONGEST",
                "$longest",
                "DAYS",
                Colors.purpleAccent,
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildStatCard(String title, String value, String unit, Color color) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.04),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.white.withOpacity(0.05)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: GoogleFonts.outfit(
              color: Colors.white24,
              fontSize: 11,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.5,
            ),
          ),
          const SizedBox(height: 12),
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Text(
                value,
                style: GoogleFonts.outfit(
                  fontSize: 32,
                  fontWeight: FontWeight.w600,
                  color: Colors.white.withOpacity(0.9),
                ),
              ),
              const SizedBox(width: 4),
              Text(
                unit,
                style: GoogleFonts.outfit(
                  fontSize: 10,
                  color: color.withOpacity(0.7),
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildChartContainer(List<int> data) {
    return Container(
      height: 240,
      padding: const EdgeInsets.fromLTRB(16, 32, 16, 16),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.04),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: Colors.white.withOpacity(0.05)),
      ),
      child: BarChart(
        BarChartData(
          alignment: BarChartAlignment.spaceAround,
          maxY: (data.reduce((a, b) => a > b ? a : b) + 5).toDouble().clamp(
            30.0,
            1000.0,
          ),
          barTouchData: BarTouchData(
            touchTooltipData: BarTouchTooltipData(
              tooltipBgColor: const Color(0xFF1C1C1E),
              getTooltipItem: (group, groupIndex, rod, rodIndex) {
                return BarTooltipItem(
                  "${rod.toY.toInt()} min",
                  GoogleFonts.outfit(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                  ),
                );
              },
            ),
          ),
          titlesData: FlTitlesData(
            show: true,
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                getTitlesWidget: (value, meta) {
                  const days = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
                  return Padding(
                    padding: const EdgeInsets.only(top: 10.0),
                    child: Text(
                      days[value.toInt() % 7],
                      style: GoogleFonts.outfit(
                        color: Colors.white24,
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
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
                  gradient: const LinearGradient(
                    colors: [Color(0xFF00E070), Color(0xFF007AFF)],
                    begin: Alignment.bottomCenter,
                    end: Alignment.topCenter,
                  ),
                  width: 14,
                  borderRadius: const BorderRadius.vertical(
                    top: Radius.circular(6),
                  ),
                  backDrawRodData: BackgroundBarChartRodData(
                    show: true,
                    toY: 1000,
                    color: Colors.white.withOpacity(0.02),
                  ),
                ),
              ],
            );
          }),
        ),
      ),
    );
  }
}
