package me.micahcode.hqtiers.client.nametag;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Optional;

public enum StatPosition {
    INLINE("Inline"), ABOVE("Above"), BELOW("Below");

    public final String label;

    StatPosition(String label) { this.label = label; }

    public List<Component> lines(NametagParts parts, Component name, boolean divider) {
        if (parts.isEmpty()) return List.of(name);
        return switch (this) {
            case INLINE -> List.of(parts.around(name, divider));
            case ABOVE -> List.of(parts.combined(), name);
            case BELOW -> List.of(name, parts.combined());
        };
    }

    public Component insertIntoDisplay(Component text, String playerName, Component stats) {
        String plain = text.getString();
        int match = plain.indexOf(playerName);
        if (this == INLINE || match < 0 || stats.getString().isEmpty()) return text;
        int offset = this == ABOVE ? plain.lastIndexOf('\n', match) + 1 : plain.indexOf('\n', match + playerName.length());
        int split = offset < 0 ? plain.length() : offset;
        MutableComponent before = Component.empty(), after = Component.empty();
        int[] remaining = {split};
        text.visit((style, value) -> {
            int cut = Math.min(remaining[0], value.length());
            if (cut > 0) before.append(Component.literal(value.substring(0, cut)).setStyle(style));
            if (cut < value.length()) after.append(Component.literal(value.substring(cut)).setStyle(style));
            remaining[0] -= cut;
            return Optional.empty();
        }, Style.EMPTY);
        return this == ABOVE ? before.append(stats).append("\n").append(after) : before.append("\n").append(stats).append(after);
    }
}
