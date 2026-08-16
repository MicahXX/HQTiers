package me.micahcode.hqtiers.client.model;

public enum HqTiersRanks {

    // this actually uses real colors now type ahh
    LT5("#4C3822"),
    MT5("#705332"),
    HT5("#936D42"),

    LT4("#64686B"),
    MT4("#83888C"),
    HT4("#A2A9AD"),

    LT3("#9E5A32"),
    MT3("#BF6C3D"),
    HT3("#DD7E46"),

    LT2("#64728C"),
    MT2("#7C8DAD"),
    HT2("#92A5CC"),

    LT1("#BF942F"),
    MT1("#DDAB37"),
    HT1("#FFC53F");

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