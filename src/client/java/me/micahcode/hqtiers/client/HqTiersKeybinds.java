package me.micahcode.hqtiers.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.leaderboard.HqTiersLeaderboardScreen;
import me.micahcode.hqtiers.client.leaderboard.HqTiersPlayerStatsScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Overlay;

public final class HqTiersKeybinds {
	private HqTiersKeybinds() {
	}

	public static void register() {
		KeyMapping leaderboard = KeyMappingHelper.registerKeyMapping(HqTiersMinecraftCompat.keyBinding(
				"key.hqtiers.open_leaderboard",
				InputConstants.KEY_L,
				"category.hqtiers"
		));

		KeyMapping cycleForward = KeyMappingHelper.registerKeyMapping(HqTiersMinecraftCompat.keyBinding(
				"key.hqtiers.cycle_mode",
				-1,
				"category.hqtiers"
		));

		KeyMapping cycleBack = KeyMappingHelper.registerKeyMapping(HqTiersMinecraftCompat.keyBinding(
				"key.hqtiers.cycle_mode_back",
				-1,
				"category.hqtiers"
		));

        KeyMapping viewStats = KeyMappingHelper.registerKeyMapping(HqTiersMinecraftCompat.keyBinding(
                "key.hqtiers.view_stats",
                InputConstants.KEY_K,
                "category.hqtiers"
        ));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			unbindAdvancementsIfConflicting(client.options, leaderboard);

			while (leaderboard.consumeClick()) {
				if (client.gui.screen() instanceof HqTiersPlayerStatsScreen) {
					client.gui.setScreen(null);
				} else {
					client.gui.setScreen(new HqTiersLeaderboardScreen(HqTiersClientState.leaderboardClient()));
				}
			}

			while (cycleForward.consumeClick()) {
				cycleLadder(client, 1);
			}

			while (cycleBack.consumeClick()) {
				cycleLadder(client, -1);
			}

            while (viewStats.consumeClick()) {
                if (client.player != null) {
                    client.gui.setScreen(new me.micahcode.hqtiers.client.leaderboard.HqTiersPlayerStatsScreen(
                            null,
                            client.player.getUUID().toString(),
                            client.player.getName().getString()
                    ));
                }
            }
		});
	}

	private static final String[] CYCLE = {
			"MODE:GLOBAL",
			"MODE:HIGHEST_TIER",
			"SWORD", "AXE", "UHC", "VANILLA", "MACE", "SPEAR_MACE", "CART",
			"DIAMOND_POT", "NETHERITE_OP", "SMP", "DIAMOND_SMP"
	};

	private static void cycleLadder(net.minecraft.client.Minecraft client, int direction) {
		String current;
		if (HqTiersClientConfig.displayMode == HqTiersClientConfig.DisplayMode.GLOBAL) {
			current = "MODE:GLOBAL";
		} else if (HqTiersClientConfig.displayMode == HqTiersClientConfig.DisplayMode.HIGHEST_TIER) {
			current = "MODE:HIGHEST_TIER";
		} else {
			current = HqTiersClientConfig.preferredLadder;
		}

		int idx = 0;
		for (int i = 0; i < CYCLE.length; i++) {
			if (CYCLE[i].equals(current)) { idx = i; break; }
		}

		String next = CYCLE[((idx + direction) % CYCLE.length + CYCLE.length) % CYCLE.length];

		if (next.equals("MODE:GLOBAL")) {
			HqTiersClientConfig.displayMode = HqTiersClientConfig.DisplayMode.GLOBAL;
		} else if (next.equals("MODE:HIGHEST_TIER")) {
			HqTiersClientConfig.displayMode = HqTiersClientConfig.DisplayMode.HIGHEST_TIER;
		} else {
			HqTiersClientConfig.displayMode = HqTiersClientConfig.DisplayMode.PREFERRED_LADDER;
			HqTiersClientConfig.preferredLadder = next;
		}

		HqTiersClientConfig.save();

		if (client.player != null) {
			String label = next.equals("MODE:GLOBAL") ? "Global"
					: next.equals("MODE:HIGHEST_TIER") ? "Highest Tier"
					: HqTiersFormatter.displayName(next);

			net.minecraft.network.chat.MutableComponent msg = net.minecraft.network.chat.Component.literal("HQTiers: " + label + " ")
					.withStyle(ChatFormatting.GOLD);

			if (next.equals("MODE:GLOBAL") || next.equals("MODE:HIGHEST_TIER")) {
				msg.append(HqTiersFormatter.icon("GLOBAL"));
			} else {
				msg.append(HqTiersFormatter.icon(next));
			}

			client.gui.hud.setOverlayMessage(msg, false);
		}
	}

	private static void unbindAdvancementsIfConflicting(Options options, KeyMapping leaderboardKey) {
		KeyMapping advancementsKey = options.keyAdvancements;
		if (leaderboardKey.saveString().equals("key.keyboard.l")
				&& advancementsKey.saveString().equals("key.keyboard.l")) {
			advancementsKey.setKey(InputConstants.UNKNOWN);
			KeyMapping.resetMapping();
			options.save();
		}
	}
}
