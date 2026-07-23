package me.micahcode.hqtiers.client.model;

public final class HqTiersRankSystem {
    public static final String RATING_LABEL = "SR";

    private HqTiersRankSystem() {
    }

    public static HqTiersRanks fallbackTier(int rating) {
        if (rating >= 2175) return HqTiersRanks.HT1;
        if (rating >= 1900) return HqTiersRanks.MT1;
        if (rating >= 1770) return HqTiersRanks.LT1;
        if (rating >= 1650) return HqTiersRanks.HT2;
        if (rating >= 1525) return HqTiersRanks.MT2;
        if (rating >= 1400) return HqTiersRanks.LT2;
        if (rating >= 1275) return HqTiersRanks.HT3;
        if (rating >= 1125) return HqTiersRanks.MT3;
        if (rating >= 1025) return HqTiersRanks.LT3;
        if (rating >= 900) return HqTiersRanks.HT4;
        if (rating >= 800) return HqTiersRanks.MT4;
        if (rating >= 700) return HqTiersRanks.LT4;
        if (rating >= 600) return HqTiersRanks.HT5;
        if (rating >= 400) return HqTiersRanks.MT5;
        return HqTiersRanks.LT5;
    }

    public static HqTiersRanks normalizeRank(String rank) {
        if (rank == null || rank.isBlank()) {
            return null;
        }

        String compact = rank.trim()
                .toUpperCase()
                .replace('-', '_')
                .replace(' ', '_');
        compact = compact.replace("LOW_TIER_", "LT")
                .replace("MID_TIER_", "MT")
                .replace("HIGH_TIER_", "HT")
                .replace("LOWTIER_", "LT")
                .replace("MIDTIER_", "MT")
                .replace("HIGHTIER_", "HT")
                .replace("_", "");

        return HqTiersRanks.fromString(compact);
    }

    public static int tierColor(HqTiersRanks tier) {
        if (tier == null) return 0xFFFFFF;
        return hexToColor(tier.getHexColor());
    }

    public static int ratingColor(int rating) {
        return tierColor(fallbackTier(rating));
    }

    public static int hexToColor(String hex) {
        if (hex == null || hex.isBlank()) return 0xFFFFFF;
        String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Integer.parseInt(cleaned, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }
}