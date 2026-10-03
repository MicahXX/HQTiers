package me.micahcode.hqtiers.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Keeps the independently positioned stats separate from the player's styled name. */
public record NametagParts(Component left, Component right) {
    /** Creates an invisible tag. */
    public static NametagParts empty() {
        return new NametagParts(Component.empty(), Component.empty());
    }

    /** Joins the visible stats for callers that do not display a player name. */
    public Component combined() {
        MutableComponent result = left.copy();
        if (!left.getString().isEmpty() && !right.getString().isEmpty()) result.append(" ");
        return result.append(right);
    }

    /** Detects a previously decorated name even when stats are split across both sides. */
    public boolean surrounds(Component name) {
        String plain = name.getString();
        String prefix = left.getString();
        String suffix = right.getString();
        return (!prefix.isEmpty() || !suffix.isEmpty())
                && (prefix.isEmpty() || plain.startsWith(prefix + boundary().getString()))
                && (suffix.isEmpty() || plain.endsWith(boundary().getString() + suffix));
    }

    /** Adds a boundary only on sides that contain visible stats. */
    public Component around(Component name) {
        MutableComponent result = Component.empty();
        if (!left.getString().isEmpty()) result.append(left).append(boundary());
        result.append(name);
        if (!right.getString().isEmpty()) result.append(boundary()).append(right);
        return result;
    }

    /** Keeps names readable when the optional vertical bar is disabled. */
    private static Component boundary() {
        return Component.literal(HqTiersClientConfig.nameSeparator ? " | " : " ")
                .withStyle(ChatFormatting.GRAY);
    }
}
