package me.micahcode.hqtiers.client.nametag;

import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import me.micahcode.hqtiers.client.config.HqTiersClientConfig.NametagAlignment;
import me.micahcode.hqtiers.client.config.HqTiersClientConfig.NametagComponent;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class NametagComposer {
    private NametagComposer() {}

    public static NametagParts compose(HqTiersStats.LadderStats ladder) {
        return compose(ladder, false);
    }

    public static NametagParts compose(HqTiersStats.LadderStats ladder, boolean alwaysLeft) {
        return compose(ladder, null, alwaysLeft);
    }

    public static NametagParts compose(HqTiersStats.LadderStats ladder, HqTiersStats.LadderStats globalRank, boolean alwaysLeft) {
        if (ladder != null && ladder.ladder().equals("GLOBAL") && (!ladder.hasPlayedRanked() || !ladder.hasPosition() || ladder.position() == 100000)) {
            return NametagParts.empty();
        }
        return new NametagParts(side(ladder, globalRank, NametagAlignment.LEFT, alwaysLeft),
                alwaysLeft ? Component.empty() : side(ladder, globalRank, NametagAlignment.RIGHT, false));
    }

    private static Component side(HqTiersStats.LadderStats ladder, HqTiersStats.LadderStats globalRank, NametagAlignment side, boolean alwaysLeft) {
        Line line = new Line(HqTiersClientConfig.iconSpacing);
        int separator = -1;
        for (NametagComponent component : HqTiersClientConfig.nametagOrder) {
            if (component == NametagComponent.SEPARATOR) separator++;
            if (!alwaysLeft && HqTiersClientConfig.side(component, separator) != side) continue;
            if (component == NametagComponent.SEPARATOR) {
                if (HqTiersClientConfig.isSeparatorEnabled(separator)) line.divider();
            } else {
                line.append(component, element(ladder, globalRank, component));
            }
        }
        return line.text();
    }

    public static final class Line {
        private final MutableComponent text = Component.empty();
        private final boolean iconSpacing;
        private NametagComponent previous;
        private boolean pendingDivider;

        public Line(boolean iconSpacing) {
            this.iconSpacing = iconSpacing;
        }

        public void divider() {
            pendingDivider = previous != null;
        }

        public void append(NametagComponent kind, Component value) {
            if (value.getString().isEmpty()) return;
            if (previous != null) {
                if (pendingDivider) text.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
                else if (iconSpacing || (previous != NametagComponent.GAMEMODE_ICON && kind != NametagComponent.GAMEMODE_ICON)) {
                    text.append(" ");
                }
            }
            text.append(value);
            pendingDivider = false;
            previous = kind;
        }

        public Component text() {
            return text;
        }
    }

    private static Component element(HqTiersStats.LadderStats ladder, HqTiersStats.LadderStats globalRank, NametagComponent component) {
        if (component == NametagComponent.GLOBAL_RANK) {
            if (!HqTiersClientConfig.globalRankEnabled || globalRank == null || !globalRank.hasPosition()
                    || globalRank.position() == 100000 || !globalRank.hasPlayedRanked() || (ladder != null && ladder.ladder().equals("GLOBAL"))) return Component.empty();
            return Component.empty().append(HqTiersFormatter.icon("GLOBAL")).append(" ")
                    .append(position(globalRank, HqTiersClientConfig.globalRankLabelEnabled));
        }
        if (ladder == null) return component == NametagComponent.TIER && HqTiersClientConfig.showUnranked
                ? Component.literal("Unranked").withStyle(ChatFormatting.GRAY) : Component.empty();
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
                    : Component.literal(ladder.progressText(HqTiersClientConfig.eloLabelEnabled))
                            .withColor(HqTiersClientConfig.coloredElo ? ladder.tierColorInt() : 0xFFFFFF);
            case POSITION -> !HqTiersClientConfig.positionEnabled || !ladder.hasPosition()
                    || (global && HqTiersClientConfig.tierEnabled) ? Component.empty()
                    : position(ladder, global || HqTiersClientConfig.positionLabelEnabled);
            case SEPARATOR, GLOBAL_RANK -> Component.empty();
        };
    }

    private static Component position(HqTiersStats.LadderStats ladder, boolean label) {
        return Component.literal((label ? "#" : "") + ladder.position())
                .withColor(HqTiersClientConfig.coloredPosition ? ladder.tierColorInt() : 0xFFFFFF);
    }
}
