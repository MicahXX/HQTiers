package me.micahcode.hqtiers.client.leaderboard;

import java.util.Locale;
import me.micahcode.hqtiers.client.model.HqTiersStats.LadderStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

enum ComparisonView {
    STANDINGS("Standings", 26), RECORD("Match record", 26), RATING("Rating", 26);

    static final int AHEAD = 0x4ADE80;
    static final int BEHIND = 0xF87171;
    static final int NEUTRAL = 0xB9AA8B;
    final String label;
    final int rowHeight;

    ComparisonView(String label, int rowHeight) {
        this.label = label;
        this.rowHeight = rowHeight;
    }

    Cell cell(LadderStats stats) {
        return cell(stats, null);
    }

    Cell cell(LadderStats stats, LadderStats opponent) {
        boolean global = stats.ladder().equals("GLOBAL");
        return switch (this) {
            case STANDINGS -> {
                if (global) yield new Cell(rank(stats, opponent), record(stats));
                if (stats.tier() == null || stats.placementGames() < stats.placementTarget()) {
                    yield new Cell(text("Unranked", NEUTRAL), rank(stats, opponent));
                }
                yield new Cell(text(stats.tierName() + "  " + stats.progressText(true), stats.tierColorInt()),
                        rank(stats, opponent));
            }
            case RECORD -> {
                long decided = (long) stats.wins() + stats.losses();
                long games = Math.max(decided, stats.gamesPlayed());
                long otherDecided = opponent == null ? 0 : (long) opponent.wins() + opponent.losses();
                String rate = decided == 0 ? "—" : String.format(Locale.ROOT, "%.1f%%", 100.0 * stats.wins() / decided);
                int color = decided == 0 || otherDecided == 0 ? NEUTRAL
                        : comparison((long) stats.wins() * otherDecided, (long) opponent.wins() * decided);
                yield new Cell(record(stats), text(rate, color)
                        .append(text("  ·  " + games + (games == 1 ? " game" : " games"), NEUTRAL)));
            }
            case RATING -> global ? new Cell(text("—", NEUTRAL), Component.empty())
                    : new Cell(rating("Current ", stats.totalRating(), opponent == null ? 0 : opponent.totalRating()),
                            rating("Peak ", stats.peakRating(), opponent == null ? 0 : opponent.peakRating()));
        };
    }

    private static Component rank(LadderStats stats, LadderStats opponent) {
        if (!stats.hasPosition()) return text("Rank —", NEUTRAL);
        if (opponent == null || !opponent.hasPosition()) return text("#" + stats.position(), NEUTRAL);
        long gap = (long) opponent.position() - stats.position();
        String detail = gap == 0 ? " · tied" : " · " + Math.abs(gap) + (gap > 0 ? " ahead" : " behind");
        return text("#" + stats.position() + detail, comparison(gap, 0));
    }

    private static Component record(LadderStats stats) {
        return text(stats.wins() + "W", AHEAD).append(text(" / ", NEUTRAL)).append(text(stats.losses() + "L", BEHIND));
    }

    private static Component rating(String label, int value, int other) {
        return text(label, NEUTRAL).append(text(value == 0 ? "—" : Integer.toString(value),
                value == 0 || other == 0 ? NEUTRAL : comparison(value, other)));
    }

    private static int comparison(long value, long other) {
        return value == other ? NEUTRAL : value > other ? AHEAD : BEHIND;
    }

    private static MutableComponent text(String value, int color) {
        return Component.literal(value).withColor(color);
    }

    record Cell(Component first, Component second) {}
}
