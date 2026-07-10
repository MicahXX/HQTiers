package me.micahcode.hqtiers.client;

import java.util.Locale;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;

import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class HqTiersCommands {
	private HqTiersCommands() {
	}

	public static void register(HqTiersCache cache) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("assets")
						.executes(context -> showSelf(context.getSource(), cache))
						.then(ClientCommandManager.argument("player", StringArgumentType.word())
								.executes(context -> showPlayer(context.getSource(), cache, StringArgumentType.getString(context, "player"))))
						.then(ClientCommandManager.literal("nametag")
								.executes(context -> toggle(context.getSource(), "nametags", !HqTiersClientConfig.nametagEnabled, value -> HqTiersClientConfig.nametagEnabled = value)))
						.then(ClientCommandManager.literal("tab")
								.executes(context -> toggle(context.getSource(), "tab list", !HqTiersClientConfig.tabListEnabled, value -> HqTiersClientConfig.tabListEnabled = value)))
						.then(ClientCommandManager.literal("ladder")
								.then(ClientCommandManager.argument("ladder", StringArgumentType.word())
										.executes(context -> setLadder(context.getSource(), StringArgumentType.getString(context, "ladder")))))
						.then(ClientCommandManager.literal("refresh")
								.executes(context -> {
									FabricClientCommandSource source = context.getSource();
									MinecraftClient client = MinecraftClient.getInstance();
									if (client.player == null) {
										source.sendError(Text.literal("You need to be in-game."));
										return 0;
									}
									UUID uuid = client.player.getUuid();
									cache.invalidate(uuid);
									cache.fetch(uuid);
									source.sendFeedback(Text.literal("HQTiers cache refreshed.").formatted(Formatting.GREEN));
									return 1;
								}))
		));
	}

	private static int showSelf(FabricClientCommandSource source, HqTiersCache cache) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			source.sendError(Text.literal("You need to be in-game to use HQTiers."));
			return 0;
		}

		return showUuid(source, cache, client.player.getUuid());
	}

	private static int showPlayer(FabricClientCommandSource source, HqTiersCache cache, String player) {
		UUID uuid = resolveOnlineUuid(player);
		if (uuid != null) {
			return showUuid(source, cache, uuid);
		}

		if (looksLikeUuid(player)) {
			try {
				return showUuid(source, cache, parseUuid(player));
			} catch (IllegalArgumentException ignored) {
				source.sendError(Text.literal("That UUID is invalid."));
				return 0;
			}
		}

		source.sendFeedback(Text.literal("Resolving Minecraft username...").formatted(Formatting.GRAY));
		HqTiersClientState.profileResolver().resolve(player).thenAccept(result -> MinecraftClient.getInstance().execute(() -> {
			if (result.status() == MojangProfileResolver.Status.NOT_FOUND) {
				source.sendError(Text.literal("Minecraft player '" + player + "' does not exist."));
				return;
			}

			if (result.status() == MojangProfileResolver.Status.ERROR) {
				source.sendError(Text.literal("Could not contact Mojang to resolve '" + player + "'. Try again later."));
				return;
			}

			source.sendFeedback(Text.literal("Resolved " + result.profile().name() + ".").formatted(Formatting.GRAY));
			showUuid(source, cache, result.profile().uuid());
		}));
		return 1;
	}

	private static int showUuid(FabricClientCommandSource source, HqTiersCache cache, UUID uuid) {
		source.sendFeedback(Text.literal("Fetching HQPvP stats...").formatted(Formatting.GRAY));
		cache.fetch(uuid).thenAccept(stats -> MinecraftClient.getInstance().execute(() -> {
			if (stats == null) {
				source.sendFeedback(Text.literal("No HQPvP ranked stats found.").formatted(Formatting.YELLOW));
				return;
			}

			source.sendFeedback(HqTiersFormatter.details(stats));
			for (Text line : HqTiersFormatter.ladderDetails(stats)) {
				source.sendFeedback(line);
			}
		}));
		return 1;
	}

	private static int toggle(FabricClientCommandSource source, String label, boolean value, BooleanSetter setter) {
		setter.set(value);
		HqTiersClientConfig.save();
		source.sendFeedback(Text.literal("HQTiers " + label + " " + (value ? "enabled" : "disabled") + ".")
				.formatted(value ? Formatting.GREEN : Formatting.RED));
		return 1;
	}

	private static int setLadder(FabricClientCommandSource source, String ladder) {
		HqTiersClientConfig.preferredLadder = HqTiersClientConfig.normalizeLadder(ladder);
		HqTiersClientConfig.save();
		source.sendFeedback(Text.literal("HQTiers preferred ladder set to " + HqTiersClientConfig.preferredLadder + ".")
				.formatted(Formatting.GREEN));
		return 1;
	}

	private static UUID resolveOnlineUuid(String name) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getNetworkHandler() == null) {
			return null;
		}

		String lowerName = name.toLowerCase(Locale.ROOT);
		for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
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
