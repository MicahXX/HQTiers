package me.micahcode.hqtiers.client;

import com.mojang.authlib.GameProfile;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class ChatMessageDecorator {
    // this regex is crazy tbh (I did not make this)
    private static final Pattern USERNAME = Pattern.compile("(?<![A-Za-z0-9_])[A-Za-z0-9_]{1,16}(?![A-Za-z0-9_])");
    private static final Pattern CHAT_SEPARATOR = Pattern.compile("^\\s*[:»›>→➜➤➥⏵❯⟫|]");
    private static final Pattern EARLIER_MESSAGE = Pattern.compile("[:»›>→➜➤➥⏵❯⟫\\r\\n]");

    private ChatMessageDecorator() {}

    public static Component decorate(Component message, Collection<GameProfile> profiles,
                                     Function<UUID, Optional<HqTiersStats>> statsFor) {
        if (!HqTiersClientConfig.chatEnabled) return message;
        Map<String, UUID> players = new HashMap<>();
        for (GameProfile profile : profiles) {
            players.put(HqTiersMinecraftCompat.profileName(profile), HqTiersMinecraftCompat.profileId(profile));
        }
        return decorate(message, players, statsFor);
    }

    private static Component decorate(Component message, Map<String, UUID> players,
                                      Function<UUID, Optional<HqTiersStats>> statsFor) {
        if (message.getContents() instanceof TranslatableContents translated) {
            // this looks for what type the message is so it knows if we apply a tier (the stats or not)
            int senderIndex = switch (translated.getKey()) {
                case "chat.type.text", "chat.type.announcement", "commands.message.display.incoming",
                        "commands.message.display.outgoing", "chat.type.emote" -> 0;
                case "chat.type.team.text", "chat.type.team.sent" -> 1;
                default -> -1;
            };
            Object[] args = translated.getArgs();
            if (senderIndex < 0 || senderIndex >= args.length) return message;
            Component name = args[senderIndex] instanceof Component component
                    ? component : Component.literal(String.valueOf(args[senderIndex]));
            UUID sender = findSender(name.getString(), players);
            if (sender == null) return message;
            var stats = statsFor.apply(sender);
            // if there are no stats (tiers)
            if (stats.isEmpty() || HqTiersFormatter.compact(stats.get()).getString().isEmpty()) return message;
            // after all checks here it actually parses the message with the extra tier info
            Object[] decoratedArgs = args.clone();
            decoratedArgs[senderIndex] = HqTiersFormatter.decorateName(stats.get(), name);
            MutableComponent result = Component.translatableWithFallback(
                    translated.getKey(), translated.getFallback(), decoratedArgs).setStyle(message.getStyle());
            message.getSiblings().forEach(result::append);
            return result;
        }

        if (message.getContents().equals(Component.empty().getContents()) && message.getSiblings().size() == 1) {
            Component child = message.getSiblings().getFirst();
            Component decorated = decorate(child, players, statsFor);
            return decorated == child ? message : message.plainCopy().setStyle(message.getStyle()).append(decorated);
        }

        String text = message.getString();
        var names = USERNAME.matcher(text);
        while (names.find() && names.start() <= 128) {
            UUID sender = players.get(names.group());
            if (sender == null || !CHAT_SEPARATOR.matcher(text.substring(names.end())).find()) continue;
            String prefix = text.substring(0, names.start()).replaceAll("\\[[^\\]\\r\\n]*]", "");
            if (EARLIER_MESSAGE.matcher(prefix).find()) return message;
            var stats = statsFor.apply(sender);
            if (stats.isEmpty() || HqTiersFormatter.compact(stats.get()).getString().isEmpty()) return message;
            Component name = slice(message, names.start(), names.end());
            return Component.empty()
                    .append(slice(message, 0, names.start()))
                    .append(HqTiersFormatter.decorateName(stats.get(), name))
                    .append(slice(message, names.end(), text.length()));
        }
        return message;
    }

    private static UUID findSender(String name, Map<String, UUID> players) {
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

    private static Component slice(Component message, int start, int end) {
        MutableComponent result = Component.empty();
        int[] offset = {0};
        message.visit((style, text) -> {
            int from = Math.max(0, start - offset[0]);
            int to = Math.min(text.length(), end - offset[0]);
            if (from < to) result.append(Component.literal(text.substring(from, to)).setStyle(style));
            offset[0] += text.length();
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}
