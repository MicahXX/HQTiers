package me.micahcode.hqtiers.client.leaderboard;

import java.time.Duration;
import java.util.List;

enum HistoryRange {
    WEEK("7d", 7), MONTH("30d", 30), QUARTER("90d", 90), YEAR("365d", 365), ALL_TIME("All time", 0);

    final String label;
    private final long durationMillis;

    HistoryRange(String label, int days) {
        this.label = label;
        this.durationMillis = Duration.ofDays(days).toMillis();
    }

    long cutoff(long now) {
        return this == ALL_TIME ? Long.MIN_VALUE : now - durationMillis;
    }

    List<RatingHistoryClient.Point> filter(List<RatingHistoryClient.Point> points, long now) {
        if (this == ALL_TIME) return points;
        long cutoff = now - durationMillis;
        return points.stream().filter(point -> {
            long time = epochMillis(point.timestamp());
            return time >= cutoff && time <= now;
        }).toList();
    }

    static long epochMillis(long timestamp) {
        return timestamp < 10_000_000_000L ? timestamp * 1000L : timestamp;
    }
}
