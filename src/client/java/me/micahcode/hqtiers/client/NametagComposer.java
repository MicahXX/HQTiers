package me.micahcode.hqtiers.client;

import me.micahcode.hqtiers.client.HqTiersClientConfig.NametagAlignment;
import me.micahcode.hqtiers.client.HqTiersClientConfig.NametagComponent;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Formats ordered elements on each side and avoids dangling or doubled separators. */
public final class NametagComposer {
    private NametagComposer() {}

    /** Builds both sides using a single shared set of visibility and spacing rules. */
    public static NametagParts compose(HqTiersStats.LadderStats ladder) {
        if (ladder.ladder().equals("GLOBAL") && (!ladder.hasPlayedRanked() || !ladder.hasPosition())) {
            return NametagParts.empty();
        }
        return new NametagParts(side(ladder, NametagAlignment.LEFT), side(ladder, NametagAlignment.RIGHT));
    }

    /** Defers separator output until another visible element exists on the same side. */
    private static Component side(HqTiersStats.LadderStats ladder, NametagAlignment side) {
        MutableComponent result = Component.empty();
        NametagComponent previous = null;
        boolean pendingSeparator = false;
        int separator = -1;
        for (NametagComponent component : HqTiersClientConfig.nametagOrder) {
            if (component == NametagComponent.SEPARATOR) separator++;
            if (HqTiersClientConfig.side(component, separator) != side) continue;
            if (component == NametagComponent.SEPARATOR) {
                pendingSeparator |= previous != null && HqTiersClientConfig.isSeparatorEnabled(separator);
                continue;
            }
            Component value = element(ladder, component);
            if (value.getString().isEmpty()) continue;
            if (previous != null) {
                if (pendingSeparator) result.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
                else if (HqTiersClientConfig.iconSpacing
                        || (previous != NametagComponent.GAMEMODE_ICON && component != NametagComponent.GAMEMODE_ICON)) {
                    result.append(" ");
                }
            }
            result.append(value);
            previous = component;
            pendingSeparator = false;
        }
        return result;
    }

    /** Uses the tier slot for global placement; a second position slot is then redundant. */
    private static Component element(HqTiersStats.LadderStats ladder, NametagComponent component) {
        boolean global = ladder.ladder().equals("GLOBAL");
        if (!global && !HqTiersClientConfig.showUnranked && ladder.tierLabel().isEmpty()) return Component.empty();
        return switch (component) {
            case GAMEMODE_ICON -> HqTiersClientConfig.gamemodeIconEnabled
                    ? HqTiersFormatter.icon(ladder.ladder()) : Component.empty();
            case TIER -> !HqTiersClientConfig.tierEnabled ? Component.empty()
                    : global ? position(ladder, true)
                    : Component.literal(HqTiersFormatter.tierLabel(ladder))
                            .withColor(HqTiersClientConfig.coloredTier ? ladder.tierColorInt() : 0xFFFFFF);
            case ELO -> !HqTiersClientConfig.eloEnabled || global ? Component.empty()
                    : Component.literal(ladder.tr() + (HqTiersClientConfig.eloLabelEnabled ? " TR" : ""))
                            .withColor(HqTiersClientConfig.coloredElo ? ladder.tierColorInt() : 0xFFFFFF);
            case POSITION -> !HqTiersClientConfig.positionEnabled || !ladder.hasPosition()
                    || (global && HqTiersClientConfig.tierEnabled) ? Component.empty()
                    : position(ladder, global || HqTiersClientConfig.positionLabelEnabled);
            case SEPARATOR -> Component.empty();
        };
    }

    /** Formats a leaderboard position using the configured color preference. */
    private static Component position(HqTiersStats.LadderStats ladder, boolean label) {
        return Component.literal((label ? "#" : "") + ladder.position())
                .withColor(HqTiersClientConfig.coloredPosition ? ladder.tierColorInt() : 0xFFFFFF);
    }
}
