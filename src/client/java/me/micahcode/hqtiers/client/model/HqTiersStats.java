package me.micahcode.hqtiers.client.model;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record HqTiersStats(UUID uuid, String name, Map<String, LadderStats> ladders, long lastUpdated) {
    // todo: change stuff for it to work
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
            int wins,
            int losses,
            int currentStreak,
            int placementMatchesPlayed,
            String currentRank,
            int position
    ) {
        public boolean hasPlayedRanked() {
            return ladder.equals("GLOBAL") || wins > 0 || losses > 0 || placementMatchesPlayed > 0 || currentRank != null;
        }

        public boolean hasPosition() {
            return position > 0;
        }

        public HqTiersRanks tier() {
            if (position == 1) {
                return HqTiersRanks.HT1;
            }

            if (currentRank != null && !currentRank.isBlank()) {
                return HqTiersRankSystem.normalizeRank(currentRank);
            }

            return hasPlayedRanked() ? HqTiersRankSystem.fallbackTier(totalRating) : null;
        }

        public String tierLabel() {
            HqTiersRanks tier = tier();
            return tier != null ? tier.getDisplayName() : "Unranked";
        }
    }
}