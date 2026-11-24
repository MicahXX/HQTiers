package me.micahcode.hqtiers.client.nametag;

import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public record NametagParts(Component left, Component right) {
    // responsible for checking if it should make an " " or not
    public static NametagParts empty() {
        return new NametagParts(Component.empty(), Component.empty());
    }

    public boolean isEmpty() {
        return left.getString().isEmpty() && right.getString().isEmpty();
    }

    public Component combined() {
        MutableComponent result = left.copy();
        if (!left.getString().isEmpty() && !right.getString().isEmpty()) result.append(" ");
        return result.append(right);
    }

    public boolean surrounds(Component name) {
        String plain = name.getString();
        String prefix = left.getString();
        String suffix = right.getString();
        return (!prefix.isEmpty() || !suffix.isEmpty())
                && (prefix.isEmpty() || plain.startsWith(prefix + boundary(HqTiersClientConfig.nameSeparator).getString()))
                && (suffix.isEmpty() || plain.endsWith(boundary(HqTiersClientConfig.nameSeparator).getString() + suffix));
    }

    public Component around(Component name) {
        return around(name, HqTiersClientConfig.nameSeparator);
    }

    public Component around(Component name, boolean nameDivider) {
        MutableComponent result = Component.empty();
        if (!left.getString().isEmpty()) result.append(left).append(boundary(nameDivider));
        result.append(name);
        if (!right.getString().isEmpty()) result.append(boundary(nameDivider)).append(right);
        return result;
    }

    private static Component boundary(boolean nameDivider) {
        return Component.literal(nameDivider ? " | " : " ")
                .withStyle(ChatFormatting.GRAY);
    }
}
