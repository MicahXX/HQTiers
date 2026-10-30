package me.micahcode.hqtiers.client;

import com.mojang.authlib.GameProfile;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityTypes;

public final class ChatMessageDecorator {
    // this regex is crazy tbh (I did not make this)
    private static final Pattern USERNAME = Pattern.compile("(?<![A-Za-z0-9_])[A-Za-z0-9_]{1,16}(?![A-Za-z0-9_])");
    private static final Pattern CHAT_HEADER = Pattern.compile(
            "^(?>\\[[^\\]\\r\\n]*]|\\([^()\\r\\n]*\\)|[^:»›>→➜➤➥⏵❯⟫|\\r\\n])*?"
                    + "(?=[:»›>→➜➤➥⏵❯⟫|]|\\h[-–—]\\h)");
    private static final Pattern TAGS = Pattern.compile("\\[[^\\]\\r\\n]*]|\\([^()\\r\\n]*\\)");
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern NAME_SUFFIX = Pattern.compile("[\\h\\p{So}*~<>\\])]*");

    private ChatMessageDecorator() {}

    public static Component decorate(Component message, Collection<GameProfile> profiles,
                                     Function<UUID, Optional<HqTiersStats>> statsFor) {
        if (!HqTiersClientConfig.chatEnabled) return message;
        Map<String, UUID> players = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (GameProfile profile : profiles) {
            String name = HqTiersMinecraftCompat.profileName(profile);
            UUID uuid = HqTiersMinecraftCompat.profileId(profile);
            if (name != null && uuid != null) players.put(name, uuid);
        }
        UUID sender = findSender(message, players);
        if (sender == null) return message;
        var stats = statsFor.apply(sender);
        // if there are no stats (tiers)
        if (stats.isEmpty()) return message;
        Component prefix = HqTiersFormatter.compact(stats.get());
        if (prefix.getString().isEmpty()) return message;
        var parts = new NametagParts(prefix, Component.empty());
        return parts.surrounds(message) ? message : parts.around(message);
    }

    private static UUID findSender(Component message, Map<String, UUID> players) {
        if (message.getContents() instanceof TranslatableContents translated) {
            // this looks for what type the message is so it knows if we apply a tier (the stats or not)
            int senderIndex = switch (translated.getKey()) {
                case "chat.type.text", "chat.type.announcement", "commands.message.display.incoming",
                        "commands.message.display.outgoing", "chat.type.emote" -> 0;
                case "chat.type.team.text", "chat.type.team.sent" -> 1;
                default -> -1;
            };
            Object[] args = translated.getArgs();
            if (senderIndex >= 0) {
                if (senderIndex >= args.length) return null;
                if (args[senderIndex] instanceof Component component) {
                    UUID sender = hoverSender(component, component.getString().length());
                    return sender != null ? sender : findSender(component.getString(), players);
                }
                return findSender(String.valueOf(args[senderIndex]), players);
            }
            if (!translated.getKey().startsWith("chat.")) return null;
        }

        if (message.getContents().equals(Component.empty().getContents())) {
            for (Component child : message.getSiblings()) {
                if (child.getString().isBlank()) continue;
                if (child.getContents() instanceof TranslatableContents
                        || child.getContents().equals(Component.empty().getContents())) {
                    UUID sender = findSender(child, players);
                    if (sender != null) return sender;
                }
                break;
            }
        }

        String text = message.getString();
        var header = CHAT_HEADER.matcher(text);
        if (!header.find() || header.end() == 0) return null;
        UUID sender = hoverSender(message, header.end());
        if (sender != null) return sender;
        String name = FORMATTING.matcher(header.group()).replaceAll("");
        var names = USERNAME.matcher(name);
        while (names.find()) {
            UUID candidate = players.get(names.group());
            if (candidate != null && NAME_SUFFIX.matcher(TAGS.matcher(name.substring(names.end())).replaceAll(" ")).matches()) return candidate;
        }
        return null;
    }

    private static UUID hoverSender(Component message, int headerEnd) {
        UUID[] sender = {null};
        boolean[] ambiguous = {false};
        int[] offset = {0};
        message.visit((style, text) -> {
            if (offset[0] >= headerEnd) return Optional.of(Boolean.TRUE);
            offset[0] += text.length();
            if (!text.isBlank() && style.getHoverEvent() instanceof HoverEvent.ShowEntity hover
                    && hover.entity().type == EntityTypes.PLAYER) {
                UUID candidate = hover.entity().uuid;
                if (sender[0] != null && !sender[0].equals(candidate)) ambiguous[0] = true;
                sender[0] = candidate;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return ambiguous[0] ? null : sender[0];
    }

    private static UUID findSender(String name, Map<String, UUID> players) {
        name = cleanName(name).strip();
        if (players.containsKey(name)) return players.get(name);
        UUID sender = null;
        var names = USERNAME.matcher(name);
        while (names.find()) {
            UUID candidate = players.get(names.group());
            if (candidate == null) continue;
            if (sender != null && !sender.equals(candidate)) return null;
            sender = candidate;
        }
        return sender;
    }

    private static String cleanName(String name) {
        return TAGS.matcher(FORMATTING.matcher(name).replaceAll("")).replaceAll(" ");
    }
}
