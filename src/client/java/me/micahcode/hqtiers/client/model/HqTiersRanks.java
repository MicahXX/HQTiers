package me.micahcode.hqtiers.client.model;

public enum HqTiersRanks {

    // tbh idk what to do with this
    // could be removed
    LT5("#8A8A8A"),
    MT5("#A0A0A0"),
    HT5("#C2C2C2"),

    LT4("#4C9A4C"),
    MT4("#5FBF5F"),
    HT4("#79E079"),

    LT3("#3A7BD5"),
    MT3("#4C97F0"),
    HT3("#6FB4FF"),

    LT2("#8B4CD5"),
    MT2("#A362E8"),
    HT2("#C08CFF"),

    LT1("#D5A93A"),
    MT1("#E8C24C"),
    HT1("#FFD966");

    private final String hexColor;

    HqTiersRanks(String hexColor) {
        this.hexColor = hexColor;
    }

    public String getDisplayName() {
        return name();
    }

    public String getHexColor() {
        return hexColor;
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