package me.micahcode.hqtiers.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum HqTiersLadder {
    SWORD('\uE001', "Sword"),
    AXE('\uE002', "Axe"),
    UHC('\uE004', "UHC"),
    VANILLA('\uE003', "Vanilla", "CRYSTAL"),
    MACE('\uE005', "Mace"),
    SPEAR_MACE('\uE00B', "Spear Mace", "SPEAR", "SPEARMACE"),
    CART('\uE00C', "Cart", "HT_CART", "CARTS", "MINECART", "MINECARTS"),
    DIAMOND_POT('\uE007', "Pot", "POT"),
    NETHERITE_POT('\uE006', "NethPot", "NETHERITE_OP"),
    SMP('\uE008', "SMP", "NETHERITE_SMP"),
    DIAMOND_SMP('\uE009', "DiamondSMP"),
    GLOBAL('\uE00A', "Global");

    private final char glyph;
    private final String displayName;
    private final Set<String> aliases;

    HqTiersLadder(char glyph, String displayName, String... aliases) {
        this.glyph = glyph;
        this.displayName = displayName;
        this.aliases = Set.of(aliases);
    }

    public char glyph() {
        return glyph;
    }

    public String displayName() {
        return displayName;
    }

    public static List<HqTiersLadder> ranked() {
        List<HqTiersLadder> result = new ArrayList<>();
        for (HqTiersLadder ladder : values()) {
            if (ladder != GLOBAL) result.add(ladder);
        }
        return result;
    }

    public static HqTiersLadder fromString(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');

        try {
            return HqTiersLadder.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            for (HqTiersLadder ladder : values()) {
                if (ladder.aliases.contains(normalized)) {
                    return ladder;
                }
            }
            return null;
        }
    }
}