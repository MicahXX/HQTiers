package me.micahcode.hqtiers.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes standalone tier labels supplied by a server or another mod. */
public final class NametagDuplicateDetector {
    private static final Pattern TIER_LABEL = Pattern.compile(
            "(?<![\\p{L}\\p{M}\\p{N}_])[HML]T[1-5](?![\\p{L}\\p{M}\\p{N}_])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_FORMATTING = Pattern.compile(
            "\u00a7[0-9A-FK-ORX]", Pattern.CASE_INSENSITIVE);

    private NametagDuplicateDetector() {
    }

    /**
     * Finds complete tier labels outside words and usernames, ignoring legacy color codes.
     * Tokens identical to the player's account name are deliberately treated as ambiguous:
     * keeping a possible duplicate is preferable to hiding stats for a player named HT3.
     */
    public static boolean hasTierLabel(String displayName, String playerName) {
        if (displayName == null || displayName.isBlank()) return false;

        String plain = displayName.indexOf('\u00a7') >= 0
                ? LEGACY_FORMATTING.matcher(displayName).replaceAll("") : displayName;
        Matcher matcher = TIER_LABEL.matcher(plain);
        while (matcher.find()) {
            if (!matcher.group().equalsIgnoreCase(playerName)) return true;
        }
        return false;
    }
}
