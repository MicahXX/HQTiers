package me.micahcode.hqtiers.client;

import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.mojang.brigadier.arguments.StringArgumentType;

import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.leaderboard.HqTiersPlayerStatsScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class HqTiersCommands {
    private HqTiersCommands() {
    }

    public static void register(HqTiersCache cache) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("hqtiers")
                        .executes(context -> showSelf(context.getSource(), cache))
                        .then(ClientCommands.argument("player", StringArgumentType.word())
                                .executes(context -> showPlayer(context.getSource(), cache, StringArgumentType.getString(context, "player"))))
                        .then(ClientCommands.literal("stats")
                                .executes(context -> showSelfGui(context.getSource()))
                                .then(ClientCommands.argument("player", StringArgumentType.word())
                                        .executes(context -> showPlayerGui(context.getSource(), StringArgumentType.getString(context, "player")))))
                        .then(ClientCommands.literal("nametag")
                                .executes(context -> toggle(context.getSource(), "nametags", !HqTiersClientConfig.nametagEnabled, value -> HqTiersClientConfig.nametagEnabled = value)))
                        .then(ClientCommands.literal("tab")
                                .executes(context -> toggle(context.getSource(), "tab list", !HqTiersClientConfig.tabListEnabled, value -> HqTiersClientConfig.tabListEnabled = value)))
                        .then(ClientCommands.literal("ladder")
                                .then(ClientCommands.argument("ladder", StringArgumentType.word())
                                        .executes(context -> setLadder(context.getSource(), StringArgumentType.getString(context, "ladder")))))
                        .then(ClientCommands.literal("refresh")
                                .executes(context -> refresh(context.getSource(), cache)))
        ));
    }

    private static int showSelf(FabricClientCommandSource source, HqTiersCache cache) {
        LocalPlayer player = requireLocalPlayer(source);
        return player == null ? 0 : showUuid(source, cache, player.getUUID());
    }

    private static int showPlayer(FabricClientCommandSource source, HqTiersCache cache, String player) {
        return resolvePlayer(source, player, (uuid, name) -> showUuid(source, cache, uuid));
    }

    private static int showSelfGui(FabricClientCommandSource source) {
        LocalPlayer player = requireLocalPlayer(source);
        return player == null ? 0 : openStatsScreen(source, player.getUUID(), player.getName().getString());
    }

    private static int showPlayerGui(FabricClientCommandSource source, String player) {
        return resolvePlayer(source, player, (uuid, name) -> openStatsScreen(source, uuid, name));
    }

    private static int refresh(FabricClientCommandSource source, HqTiersCache cache) {
        LocalPlayer player = requireLocalPlayer(source);
        if (player == null) {
            return 0;
        }

        UUID uuid = player.getUUID();
        cache.invalidate(uuid);
        cache.fetch(uuid);
        source.sendFeedback(Component.literal("HQTiers cache refreshed.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int resolvePlayer(FabricClientCommandSource source, String player, BiConsumer<UUID, String> onResolved) {
        UUID onlineUuid = resolveOnlineUuid(player);
        if (onlineUuid != null) {
            onResolved.accept(onlineUuid, player);
            return 1;
        }

        if (looksLikeUuid(player)) {
            try {
                onResolved.accept(parseUuid(player), player);
            } catch (IllegalArgumentException ignored) {
                source.sendError(Component.literal("That UUID is invalid."));
                return 0;
            }
            return 1;
        }

        source.sendFeedback(Component.literal("Resolving Minecraft username...").withStyle(ChatFormatting.GRAY));
        HqTiersClientState.profileResolver().resolve(player).thenAccept(result -> Minecraft.getInstance().execute(() -> {
            if (result.status() == MojangProfileResolver.Status.NOT_FOUND) {
                source.sendError(Component.literal("Minecraft player '" + player + "' does not exist."));
                return;
            }

            if (result.status() == MojangProfileResolver.Status.ERROR) {
                source.sendError(Component.literal("Could not contact Mojang to resolve '" + player + "'. Try again later."));
                return;
            }

            source.sendFeedback(Component.literal("Resolved " + result.profile().name() + ".").withStyle(ChatFormatting.GRAY));
            onResolved.accept(result.profile().uuid(), result.profile().name());
        }));
        return 1;
    }

    private static int showUuid(FabricClientCommandSource source, HqTiersCache cache, UUID uuid) {
        source.sendFeedback(Component.literal("Fetching HQPvP stats...").withStyle(ChatFormatting.GRAY));
        cache.fetch(uuid).thenAccept(stats -> Minecraft.getInstance().execute(() -> {
            if (stats == null) {
                source.sendFeedback(Component.literal("No HQPvP ranked stats found.").withStyle(ChatFormatting.YELLOW));
                return;
            }

            source.sendFeedback(HqTiersFormatter.details(stats));
            for (Component line : HqTiersFormatter.ladderDetails(stats)) {
                source.sendFeedback(line);
            }
        }));
        return 1;
    }

    private static int openStatsScreen(FabricClientCommandSource source, UUID uuid, String name) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.gui.setScreen(new HqTiersPlayerStatsScreen(client.gui.screen(), uuid.toString(), name)));
        return 1;
    }

    private static int toggle(FabricClientCommandSource source, String label, boolean value, BooleanSetter setter) {
        setter.set(value);
        HqTiersClientConfig.save();
        source.sendFeedback(Component.literal("HQTiers " + label + " " + (value ? "enabled" : "disabled") + ".")
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
        return 1;
    }

    private static int setLadder(FabricClientCommandSource source, String ladder) {
        HqTiersClientConfig.preferredLadder = HqTiersClientConfig.normalizeLadder(ladder);
        HqTiersClientConfig.save();
        source.sendFeedback(Component.literal("HQTiers preferred ladder set to " + HqTiersClientConfig.preferredLadder + ".")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static LocalPlayer requireLocalPlayer(FabricClientCommandSource source) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            source.sendError(Component.literal("You need to be in-game to use HQTiers."));
        }
        return player;
    }

    private static UUID resolveOnlineUuid(String name) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) {
            return null;
        }

        String lowerName = name.toLowerCase(Locale.ROOT);
        for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
            if (HqTiersMinecraftCompat.profileName(entry.getProfile()).toLowerCase(Locale.ROOT).equals(lowerName)) {
                return HqTiersMinecraftCompat.profileId(entry.getProfile());
            }
        }

        return null;
    }

    private static boolean looksLikeUuid(String player) {
        return player.indexOf('-') >= 0 || player.length() == 32 || player.length() == 36;
    }

    private static UUID parseUuid(String player) {
        if (player.length() != 32) {
            return UUID.fromString(player);
        }

        return UUID.fromString(player.replaceFirst(
                "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                "$1-$2-$3-$4-$5"
        ));
    }

    @FunctionalInterface
    private interface BooleanSetter {
        void set(boolean value);
    }
}