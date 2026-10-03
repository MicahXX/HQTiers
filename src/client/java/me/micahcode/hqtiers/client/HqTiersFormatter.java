package me.micahcode.hqtiers.client;

import me.micahcode.hqtiers.client.model.HqTiersLadder;
import me.micahcode.hqtiers.client.model.HqTiersRankSystem;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class HqTiersFormatter {
    private HqTiersFormatter() {
    }

    public static final List<String> KNOWN_LADDERS = HqTiersLadder.ranked().stream()
            .map(Enum::name)
            .toList();

    private record CompactCacheEntry(int configVersion, NametagParts parts) {}

    private static final Map<HqTiersStats, CompactCacheEntry> COMPACT_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Returns both sides of the nametag, cached until settings or player data change. */
    private static NametagParts parts(HqTiersStats stats) {
        int version = HqTiersClientConfig.configVersion();
        CompactCacheEntry cached = COMPACT_CACHE.get(stats);
        if (cached != null && cached.configVersion() == version) return cached.parts();
        HqTiersStats.LadderStats ladder = stats.displayLadder().orElse(null);
        NametagParts parts;
        if (HqTiersClientConfig.displayMode == HqTiersClientConfig.DisplayMode.GLOBAL
                && (ladder == null || !ladder.hasPlayedRanked() || !ladder.hasPosition())) {
            parts = NametagParts.empty();
        } else if (ladder == null) {
            Component label = HqTiersClientConfig.showUnranked
                    ? Component.literal("Unranked").withStyle(ChatFormatting.GRAY) : Component.empty();
            parts = HqTiersClientConfig.side(HqTiersClientConfig.NametagComponent.TIER, -1)
                    == HqTiersClientConfig.NametagAlignment.LEFT
                    ? new NametagParts(label, Component.empty()) : new NametagParts(Component.empty(), label);
        } else {
            parts = NametagComposer.compose(ladder);
        }
        COMPACT_CACHE.put(stats, new CompactCacheEntry(version, parts));
        return parts;
    }

    /** Returns the visible stats without a player name. */
    public static Component compact(HqTiersStats stats) {
        return parts(stats).combined();
    }

    /** Inserts stats around the original name while preserving its styles and events. */
    public static Component decorateName(HqTiersStats stats, Component name) {
        NametagParts parts = parts(stats);
        return parts.surrounds(name) ? name : parts.around(name);
    }

    /** Renders example stats around the signed-in player's actual Minecraft username. */
    public static Component previewCompact() {
        String ladder = HqTiersClientConfig.displayMode == HqTiersClientConfig.DisplayMode.GLOBAL
                ? "GLOBAL" : HqTiersClientConfig.preferredLadder;
        String username = net.minecraft.client.Minecraft.getInstance().getUser().getName();
        return NametagComposer.compose(HqTiersStats.LadderStats.minimal(ladder, 125, 10, 5, 10, "MT4", 123))
                .around(Component.literal(username).withStyle(ChatFormatting.WHITE));
    }

    public static Component details(HqTiersStats stats) {
        return Component.literal("PvPHQ stats for ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(stats.name()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(":").withStyle(ChatFormatting.GRAY));
    }

    public static List<Component> ladderDetails(HqTiersStats stats) {
        List<Component> lines = new ArrayList<>();
        stats.ladder("GLOBAL").ifPresent(global -> lines.add(ladderDetailLine(global)));
        stats.ladders().values().stream()
                .filter(HqTiersStats.LadderStats::hasPlayedRanked)
                .filter(ladder -> !ladder.ladder().equals("GLOBAL"))
                .sorted(Comparator.comparingInt(HqTiersStats.LadderStats::tr).reversed())
                .forEach(ladder -> lines.add(ladderDetailLine(ladder)));
        return lines;
    }

    private static Component ladderDetailLine(HqTiersStats.LadderStats ladder) {
        return Component.literal("  ")
                .append(icon(ladder.ladder()))
                .append(Component.literal(" "))
                .append(Component.literal(displayName(ladder.ladder())).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(ladder.tierLabel()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(ratingText(ladder.tr())).setStyle(Style.EMPTY.withColor(ladder.tierColorInt())))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(ladder.wins() + "W/" + ladder.losses() + "L").withStyle(ChatFormatting.WHITE))
                .append(positionDetails(ladder));
    }

    private static Component positionDetails(HqTiersStats.LadderStats ladder) {
        if (!ladder.hasPosition()) {
            return Component.empty();
        }

        return Component.literal(" | #").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(Integer.toString(ladder.position())).withStyle(ChatFormatting.WHITE));
    }

    /** Formats a tier according to the configured abbreviation preference. */
    public static String tierLabel(HqTiersStats.LadderStats ladder) {
        if (!HqTiersClientConfig.shortTierNames) {
            return ladder.tierLabel();
        }

        String tag = ladder.tierLabel();
        if (tag.matches("(?i)[LMH]T[1-5]")) {
            return tag.toUpperCase();
        }

        String[] words = tag.split("\\s+");
        if (words.length < 2) {
            return tag;
        }

        return words[0].substring(0, 1).toUpperCase() + shortDivision(words[1]);
    }

    private static String shortDivision(String division) {
        return switch (division.toUpperCase()) {
            case "I" -> "1";
            case "II" -> "2";
            case "III" -> "3";
            case "IV" -> "4";
            case "V" -> "5";
            default -> division;
        };
    }

    public static Component icon(String ladder) {
        return Component.literal(String.valueOf(iconGlyph(ladder)))
                .setStyle(HqTiersMinecraftCompat.fontStyle(Identifier.fromNamespaceAndPath("hqtiers", "default"))
                        .withColor(0xFFFFFF));
    }

    public static String ratingText(int rating) {
        return rating + " " + HqTiersRankSystem.RATING_LABEL;
    }

    private static char iconGlyph(String ladder) {
        HqTiersLadder resolved = HqTiersLadder.fromString(HqTiersClientConfig.normalizeLadder(ladder));
        return resolved != null ? resolved.glyph() : HqTiersLadder.GLOBAL.glyph();
    }

    public static String displayName(String ladder) {
        String normalized = HqTiersClientConfig.normalizeLadder(ladder);
        HqTiersLadder resolved = HqTiersLadder.fromString(normalized);
        return resolved != null ? resolved.displayName() : normalized;
    }

    public static Optional<HqTiersStats.LadderStats> bestLadder(Map<String, HqTiersStats.LadderStats> ladders) {
        return ladders.values().stream()
                .filter(HqTiersStats.LadderStats::hasPlayedRanked)
                .filter(ladder -> !ladder.ladder().equals("GLOBAL"))
                .filter(ladder -> !ladder.unranked())
                .filter(ladder -> ladder.placementGames() >= ladder.placementTarget())
                .max(Comparator.comparingInt((HqTiersStats.LadderStats ladder) ->
                        ladder.tier() == null ? -1 : ladder.tier().ordinal())
                        .thenComparingInt(HqTiersStats.LadderStats::totalRating));
    }
}