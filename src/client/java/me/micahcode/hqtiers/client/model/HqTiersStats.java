package me.micahcode.hqtiers.client.model;

import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import me.micahcode.hqtiers.client.nametag.HqTiersFormatter;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record HqTiersStats(UUID uuid, String name, Map<String, LadderStats> ladders, long lastUpdated) {
    public Optional<LadderStats> ladder(String ladder) {
        return Optional.ofNullable(ladders.get(HqTiersClientConfig.normalizeLadder(ladder)))
                .filter(LadderStats::hasPlayedRanked);
    }

    public Optional<LadderStats> bestLadder() {
        return HqTiersFormatter.bestLadder(ladders);
    }

    public Optional<LadderStats> displayLadder() {
        if (HqTiersClientConfig.displayMode == HqTiersClientConfig.DisplayMode.GLOBAL) {
            return ladder("GLOBAL");
        }

        if (HqTiersClientConfig.displayMode == HqTiersClientConfig.DisplayMode.HIGHEST_TIER) {
            return bestLadder();
        }

        Optional<LadderStats> preferred = ladder(HqTiersClientConfig.preferredLadder)
                .filter(l -> l.placementGames() >= l.placementTarget());

        return preferred
                .or(this::bestLadder)
                .or(() -> ladder(HqTiersClientConfig.preferredLadder));
    }

    // used for the lb and playerstatsscreen etc.
    public record LadderStats(
            String ladder,
            int totalRating,
            int peakRating,
            int tr,
            int rd,
            int wins,
            int losses,
            int gamesPlayed,
            double winRate,
            String tierName,
            String tierColorHex,
            Integer tierProgress,
            boolean unranked,
            boolean inactive,
            int placementGames,
            int placementTarget,
            long lastPlayedAt,
            int tierFloor,
            int tierCeiling,
            String nextTier,
            String nextTierColorHex,
            boolean nextIsTournamentGated,
            boolean atRatingCap,
            int ratingAboveCap,
            boolean fromTournament,
            int promoProgress,
            int position
    ) {
        public static final Comparator<LadderStats> STANDINGS_ORDER = Comparator.comparing((LadderStats ladder) ->
                        ladder.placementGames() < ladder.placementTarget() ? null : ladder.tier(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LadderStats::progress, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingInt(ladder -> ladder.hasPosition() ? ladder.position() : Integer.MAX_VALUE)
                .thenComparing(LadderStats::ladder);

        public static LadderStats minimal(String ladder, int rating, int wins, int losses,
                                          int placementGames, String tierName, int position) {
            return new LadderStats(
                    ladder, rating, rating, rating, 350, wins, losses, wins + losses, 0.0,
                    tierName, null, 0, tierName == null, false,
                    placementGames, 10, 0L, 0, 0,
                    null, null, false, false, 0, false, 0, position
            );
        }

        public Integer progress() {
            if (HqTiersRankSystem.usesPercent(tierName)) return tierProgress;
            return tr;
        }

        public String progressText(boolean label) {
            return HqTiersRankSystem.progressText(progress(), HqTiersRankSystem.usesPercent(tierName), label);
        }

        public LadderStats withTierProgress(int progress) {
            return new LadderStats(ladder, totalRating, peakRating, tr, rd, wins, losses, gamesPlayed,
                    winRate, tierName, tierColorHex, progress, unranked, inactive, placementGames,
                    placementTarget, lastPlayedAt, tierFloor, tierCeiling, nextTier, nextTierColorHex,
                    nextIsTournamentGated, atRatingCap, ratingAboveCap, fromTournament, promoProgress, position);
        }

        public boolean hasPlayedRanked() {
            if (ladder.equals("GLOBAL")) return gamesPlayed > 0 || wins > 0 || losses > 0;
            return gamesPlayed > 0
                    || wins > 0
                    || losses > 0
                    || placementGames >= 10
                    || !unranked;
        }

        public boolean hasPosition() {
            return position > 0;
        }

        public HqTiersRanks tier() {
            return HqTiersRankSystem.normalizeRank(tierName);
        }

        public String tierLabel() {
            if (placementGames < placementTarget) {
                return HqTiersClientConfig.showUnranked ? "Unranked" : "";
            }

            if (tierName != null
                    && !tierName.isBlank()
                    && !tierName.equalsIgnoreCase("Unranked")) {
                return tierName;
            }

            HqTiersRanks tier = tier();

            return tier != null
                    ? tier.getDisplayName()
                    : (HqTiersClientConfig.showUnranked ? "Unranked" : "");
        }

        public int tierColorInt() {
            return HqTiersRankSystem.displayColor(HqTiersRankSystem.normalizeRank(tierName), tierColorHex);
        }
    }
}