package me.micahcode.hqtiers.client.model;

public final class HqTiersRankSystem {
    public static final String RATING_LABEL = "TR";

    private static final int DEFAULT_COLOR = 0xFFFFFF;

    private HqTiersRankSystem() {
    }

    public static HqTiersRanks normalizeRank(String rank) {
        if (rank == null || rank.isBlank()) {
            return null;
        }

        String compact = rank.trim()
                .toUpperCase()
                .replace('-', '_')
                .replace(' ', '_')
                .replace("LOW_TIER_", "LT")
                .replace("MID_TIER_", "MT")
                .replace("HIGH_TIER_", "HT")
                .replace("LOWTIER_", "LT")
                .replace("MIDTIER_", "MT")
                .replace("HIGHTIER_", "HT")
                .replace("_", "");

        return HqTiersRanks.fromString(compact);
    }

    public static int tierColor(HqTiersRanks tier) {
        return tier != null ? hexToColor(tier.getHexColor()) : DEFAULT_COLOR;
    }

    public static int hexToColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return DEFAULT_COLOR;
        }

        String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Integer.parseInt(cleaned, 16);
        } catch (NumberFormatException e) {
            return DEFAULT_COLOR;
        }
    }
}