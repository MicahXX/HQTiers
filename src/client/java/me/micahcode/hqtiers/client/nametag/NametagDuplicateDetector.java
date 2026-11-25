package me.micahcode.hqtiers.client.nametag;

import me.micahcode.hqtiers.client.mixin.TextDisplayAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class NametagDuplicateDetector {
    private static final Pattern TIER_LABEL = Pattern.compile(
            "(?<![\\p{L}\\p{M}\\p{N}_])(?:[HML]T|(?:high|mid|low)[\\h_-]*tier)[\\h_-]*[1-5](?![\\p{L}\\p{M}\\p{N}_])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FORMATTING = Pattern.compile("§[0-9A-FK-ORX]|[\\u200B-\\u200D\\uFEFF]", Pattern.CASE_INSENSITIVE);

    private NametagDuplicateDetector() {}

    public static boolean hasTierLabel(String text, String playerName) {
        if (text == null || text.isBlank()) return false;
        String plain = FORMATTING.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC)).replaceAll("")
                .replace('ʜ', 'H').replace('ᴍ', 'M').replace('ʟ', 'L').replace('ᴛ', 'T');
        var matcher = TIER_LABEL.matcher(plain);
        boolean skippedName = false;
        while (matcher.find()) {
            if (skippedName || !matcher.group().equalsIgnoreCase(playerName)) return true;
            skippedName = true;
        }
        return false;
    }

    public static boolean hasPassengerTier(Player player) {
        for (var passenger : player.getPassengers()) {
            if (passenger instanceof Display.TextDisplay display) {
                var text = display.getEntityData().get(TextDisplayAccessor.hqtiers$textId());
                if (hasTierLabel(text.getString(), player.getScoreboardName())) return true;
            }
        }
        return false;
    }
}
