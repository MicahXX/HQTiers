package me.micahcode.hqtiers.client.model;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;

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

        return ladder(HqTiersClientConfig.preferredLadder).or(this::bestLadder);
    }

    public record LadderStats(
            String ladder,
            int totalRating,
            int peakRating,
            int rd,
            int wins,
            int losses,
            int gamesPlayed,
            double winRate,
            String tierName,
            String tierColorHex,
            int tierProgress,
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
        public static LadderStats minimal(String ladder, int rating, int wins, int losses,
                                          int placementGames, String tierName, int position) {
            return new LadderStats(
                    ladder, rating, rating, 350, wins, losses, wins + losses, 0.0,
                    tierName, null, 0, tierName == null, false,
                    placementGames, 10, 0L, 0, 0,
                    null, null, false, false, 0, false, 0, position
            );
        }

        public boolean hasPlayedRanked() {
            return ladder.equals("GLOBAL") || wins > 0 || losses > 0 || placementGames > 0
                    || (tierName != null && !tierName.isBlank());
        }

        public boolean hasPosition() {
            return position > 0;
        }

        public int currentStreak() {
            return 0;
        }

        public int placementMatchesPlayed() {
            return placementGames;
        }

        public HqTiersRanks tier() {
            HqTiersRanks fromApi = HqTiersRankSystem.normalizeRank(tierName);
            if (fromApi != null) return fromApi;
            return hasPlayedRanked() ? HqTiersRankSystem.fallbackTier(totalRating) : null;
        }

        public String tierLabel() {
            if (tierName != null && !tierName.isBlank()) return tierName;
            HqTiersRanks tier = tier();
            return tier != null ? tier.getDisplayName() : "Unranked";
        }

        public int tierColorInt() {
            if (tierColorHex != null && !tierColorHex.isBlank()) {
                return HqTiersRankSystem.hexToColor(tierColorHex);
            }
            HqTiersRanks tier = tier();
            return tier != null ? HqTiersRankSystem.tierColor(tier) : HqTiersRankSystem.ratingColor(totalRating);
        }
    }
}