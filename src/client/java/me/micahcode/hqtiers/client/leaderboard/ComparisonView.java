package me.micahcode.hqtiers.client.leaderboard;

import java.util.Locale;
import me.micahcode.hqtiers.client.model.HqTiersStats.LadderStats;

enum ComparisonView {
    STANDINGS("Standings", 15), RECORD("Match record", 26), RATING("Rating", 26);

    final String label;
    final int rowHeight;

    ComparisonView(String label, int rowHeight) {
        this.label = label;
        this.rowHeight = rowHeight;
    }

    Cell cell(LadderStats stats) {
        boolean global = stats.ladder().equals("GLOBAL");
        String rank = stats.hasPosition() ? "#" + stats.position() : "—";
        return switch (this) {
            case STANDINGS -> {
                if (global) yield new Cell(rank + "  " + record(stats), "", 0xFFFFD86B);
                if (stats.tier() == null || stats.placementGames() < stats.placementTarget()) {
                    yield new Cell("Unranked", "", 0xFF96876A);
                }
                yield new Cell(stats.tierName() + "  " + stats.progressText(true) + "  " + rank, "",
                        0xFF000000 | stats.tierColorInt());
            }
            case RECORD -> {
                long decided = (long) stats.wins() + stats.losses();
                long games = Math.max(decided, stats.gamesPlayed());
                String rate = decided == 0 ? "—" : String.format(Locale.ROOT, "%.1f%%", 100.0 * stats.wins() / decided);
                yield new Cell(record(stats), rate + "  ·  " + games + (games == 1 ? " game" : " games"), 0xFFFFF8E8);
            }
            case RATING -> global ? new Cell("—", "", 0xFF96876A)
                    : new Cell("Current " + rating(stats.totalRating()), "Peak " + rating(stats.peakRating()), 0xFFFFF8E8);
        };
    }

    private static String record(LadderStats stats) {
        return stats.wins() + "W / " + stats.losses() + "L";
    }

    private static String rating(int value) {
        return value == 0 ? "—" : Integer.toString(value);
    }

    record Cell(String first, String second, int color) {}
}
