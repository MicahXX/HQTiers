package me.micahcode.hqtiers.client;

import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
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

    private record CompactCacheEntry(int configVersion, Component component) {}

    private static final Map<HqTiersStats, CompactCacheEntry> COMPACT_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static Component compact(HqTiersStats stats) {
        int version = HqTiersClientConfig.configVersion();
        CompactCacheEntry cached = COMPACT_CACHE.get(stats);
        if (cached != null && cached.configVersion() == version) {
            return cached.component();
        }

        Component computed = computeCompact(stats);
        COMPACT_CACHE.put(stats, new CompactCacheEntry(version, computed));
        return computed;
    }

    private static Component computeCompact(HqTiersStats stats) {
        HqTiersStats.LadderStats ladder = stats.displayLadder().orElse(null);

        if (ladder == null) {
            return HqTiersClientConfig.showUnranked
                    ? Component.literal("Unranked").withStyle(ChatFormatting.GRAY)
                    : Component.empty();
        }

        if (!HqTiersClientConfig.showUnranked && !ladder.hasPlayedRanked()) {
            return Component.empty();
        }

        return decorated(ladder);
    }

    public static Component previewCompact() {
        Component preview = decorated(HqTiersStats.LadderStats.minimal(
                HqTiersClientConfig.preferredLadder,
                800,
                10,
                5,
                10,
                "MT4",
                123
        ));

        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.player != null) {
            var real = HqTiersClientState.cache()
                    .getIfFresh(client.player.getUUID())
                    .map(HqTiersFormatter::compact)
                    .orElse(Component.empty());

            if (!real.getString().isEmpty()) {
                preview = real;
            }
        }

        return preview;
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
                .sorted(Comparator.comparingInt(HqTiersStats.LadderStats::totalRating).reversed())
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
                .append(Component.literal(ratingText(ladder.totalRating())).setStyle(Style.EMPTY.withColor(ladder.tierColorInt())))
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

    private static Component decorated(HqTiersStats.LadderStats ladder) {
        MutableComponent text = Component.empty();
        boolean wrotePart = false;
        int separatorOccurrence = -1;

        for (HqTiersClientConfig.NametagComponent component : HqTiersClientConfig.nametagOrder) {
            if (!HqTiersClientConfig.showUnranked && ladder.tierLabel().isEmpty()) {
                return Component.empty();
            }

            switch (component) {
                case GAMEMODE_ICON -> {
                    if (!HqTiersClientConfig.gamemodeIconEnabled) continue;
                    if (wrotePart) text.append(Component.literal(" "));
                    text.append(icon(ladder.ladder()));
                    wrotePart = true;
                }
                case TIER -> {
                    if (!HqTiersClientConfig.tierEnabled) continue;
                    if (wrotePart) text.append(Component.literal(" "));
                    if (HqTiersClientConfig.coloredTier) {
                        text.append(Component.literal(tierLabel(ladder)).setStyle(Style.EMPTY.withColor(ladder.tierColorInt())));
                    } else {
                        text.append(Component.literal(tierLabel(ladder)).withStyle(ChatFormatting.WHITE));
                    }
                    wrotePart = true;
                }
                case SEPARATOR -> {
                    separatorOccurrence++;
                    if (!HqTiersClientConfig.isSeparatorEnabled(separatorOccurrence) || !wrotePart) continue;
                    text.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
                }
                case ELO -> {
                    if (!HqTiersClientConfig.eloEnabled) continue;
                    Style eloStyle = Style.EMPTY.withColor(HqTiersClientConfig.coloredElo ? ladder.tierColorInt() : 0xFFFFFF);
                    text.append(Component.literal(Integer.toString(ladder.totalRating())).setStyle(eloStyle));
                    if (HqTiersClientConfig.eloLabelEnabled)
                        text.append(Component.literal(" " + HqTiersRankSystem.RATING_LABEL).setStyle(eloStyle));
                    wrotePart = true;
                }
                case POSITION -> {
                    if (!HqTiersClientConfig.positionEnabled || !ladder.hasPosition()) continue;
                    int posColor = HqTiersClientConfig.coloredPosition ? ladder.tierColorInt() : 0xFFFFFF;
                    if (HqTiersClientConfig.positionLabelEnabled)
                        text.append(Component.literal("#").setStyle(Style.EMPTY.withColor(posColor)));
                    text.append(Component.literal(Integer.toString(ladder.position())).setStyle(Style.EMPTY.withColor(posColor)));
                    wrotePart = true;
                }
            }
        }
        return text;
    }

    private static String tierLabel(HqTiersStats.LadderStats ladder) {
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
                .max(Comparator.comparingInt(HqTiersStats.LadderStats::totalRating));
    }
}