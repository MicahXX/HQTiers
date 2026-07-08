package me.micahcode.hqtiers.client.model;

public enum HqTiersRanks {

    // tiers on pvphq (Some random colors for now will prob be changed) this will be used
    LT5("LT5", "#8A8A8A"),
    MT5("MT5", "#A0A0A0"),
    HT5("HT5", "#C2C2C2"),

    LT4("LT4", "#4C9A4C"),
    MT4("MT4", "#5FBF5F"),
    HT4("HT4", "#79E079"),

    LT3("LT3", "#3A7BD5"),
    MT3("MT3", "#4C97F0"),
    HT3("HT3", "#6FB4FF"),

    LT2("LT2", "#8B4CD5"),
    MT2("MT2", "#A362E8"),
    HT2("HT2", "#C08CFF"),

    LT1("LT1", "#D5A93A"),
    MT1("MT1", "#E8C24C"),
    HT1("HT1", "#FFD966");

    private final String displayName;
    private final String hexColor;

    HqTiersRanks(String displayName, String hexColor) {
        this.displayName = displayName;
        this.hexColor = hexColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getHexColor() {
        return hexColor;
    }

    // 1, 2, 3, 4, 5
    public int getTierNumber() {
        return Character.getNumericValue(name().charAt(name().length() - 1));
    }

    // L, M, H
    public char getTierName() {
        return name().charAt(0);
    }

    public static HqTiersRanks fromString(String raw) {
        if (raw == null) {
            return null;
        }

        try {
            return HqTiersRanks.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}