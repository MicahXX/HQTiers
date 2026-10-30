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
import net.minecraft.network.chat.contents.TranslatableContents;

public final class ChatMessageDecorator {
    // this regex is crazy tbh (I did not make this)
    private static final Pattern USERNAME = Pattern.compile("(?<![A-Za-z0-9_])[A-Za-z0-9_]{1,16}(?![A-Za-z0-9_])");
    private static final Pattern CHAT_HEADER = Pattern.compile(
            "^\\h*(?:\\[[^\\]\\r\\n]*]\\h*)*<?([A-Za-z0-9_]{1,16})(?![A-Za-z0-9_])"
                    + "\\h*(?:\\[[^\\]\\r\\n]*]\\h*)*[:»›>→➜➤➥⏵❯⟫|]");

    private ChatMessageDecorator() {}

    public static Component decorate(Component message, Collection<GameProfile> profiles,
                                     Function<UUID, Optional<HqTiersStats>> statsFor) {
        if (!HqTiersClientConfig.chatEnabled) return message;
        Map<String, UUID> players = new HashMap<>();
        for (GameProfile profile : profiles) {
            players.put(HqTiersMinecraftCompat.profileName(profile), HqTiersMinecraftCompat.profileId(profile));
        }
        UUID sender = findSender(message, players);
        if (sender == null) return message;
        var stats = statsFor.apply(sender);
        // if there are no stats (tiers)
        if (stats.isEmpty()) return message;
        Component prefix = HqTiersFormatter.compact(stats.get());
        if (prefix.getString().isEmpty()) return message;
        return new NametagParts(prefix, Component.empty()).around(message);
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
            if (senderIndex < 0 || senderIndex >= args.length) return null;
            String name = args[senderIndex] instanceof Component component
                    ? component.getString() : String.valueOf(args[senderIndex]);
            return findSender(name, players);
        }

        if (message.getContents().equals(Component.empty().getContents()) && message.getSiblings().size() == 1) {
            return findSender(message.getSiblings().getFirst(), players);
        }

        var header = CHAT_HEADER.matcher(message.getString());
        return header.find() ? players.get(header.group(1)) : null;
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
}
