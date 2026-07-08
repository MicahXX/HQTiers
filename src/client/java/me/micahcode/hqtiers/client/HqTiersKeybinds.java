package me.micahcode.hqtiers.client;

import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.leaderboard.HqTiersLeaderboardScreen;
import me.micahcode.hqtiers.client.leaderboard.HqTiersPlayerStatsScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class HqTiersKeybinds {
	private HqTiersKeybinds() {
	}

	// todo: rename
	public static void register() {
		KeyBinding leaderboard = KeyBindingHelper.registerKeyBinding(HqTiersMinecraftCompat.keyBinding(
				"key.hqtiers.open_leaderboard",
				InputUtil.GLFW_KEY_L,
				"category.flowtiers"
		));

		KeyBinding cycleForward = KeyBindingHelper.registerKeyBinding(HqTiersMinecraftCompat.keyBinding(
				"key.hqtiers.cycle_mode",
				-1,
				"category.flowtiers"
		));

		KeyBinding cycleBack = KeyBindingHelper.registerKeyBinding(HqTiersMinecraftCompat.keyBinding(
				"key.hqtiers.cycle_mode_back",
				-1,
				"category.flowtiers"
		));

        KeyBinding viewStats = KeyBindingHelper.registerKeyBinding(HqTiersMinecraftCompat.keyBinding(
                "key.hqtiers.view_stats",
                InputUtil.GLFW_KEY_K,
                "category.flowtiers"
        ));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			unbindAdvancementsIfConflicting(client.options, leaderboard);

			while (leaderboard.wasPressed()) {
				if (client.currentScreen instanceof HqTiersPlayerStatsScreen) {
					client.setScreen(null);
				} else {
					client.setScreen(new HqTiersLeaderboardScreen(HqTiersClientState.leaderboardClient()));
				}
			}

			while (cycleForward.wasPressed()) {
				cycleLadder(client, 1);
			}

			while (cycleBack.wasPressed()) {
				cycleLadder(client, -1);
			}

            while (viewStats.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new me.micahcode.hqtiers.client.leaderboard.HqTiersPlayerStatsScreen(
                            null,
                            client.player.getUuid().toString(),
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

	private static void cycleLadder(net.minecraft.client.MinecraftClient client, int direction) {
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

			net.minecraft.text.MutableText msg = net.minecraft.text.Text.literal("FlowTiers: " + label + " ")
					.formatted(net.minecraft.util.Formatting.GREEN);

			if (next.equals("MODE:GLOBAL") || next.equals("MODE:HIGHEST_TIER")) {
				msg.append(HqTiersFormatter.icon("GLOBAL"));
			} else {
				msg.append(HqTiersFormatter.icon(next));
			}

			client.player.sendMessage(msg, true);
		}
	}

	private static void unbindAdvancementsIfConflicting(GameOptions options, KeyBinding leaderboardKey) {
		KeyBinding advancementsKey = options.advancementsKey;
		if (leaderboardKey.getBoundKeyTranslationKey().equals("key.keyboard.l")
				&& advancementsKey.getBoundKeyTranslationKey().equals("key.keyboard.l")) {
			advancementsKey.setBoundKey(InputUtil.UNKNOWN_KEY);
			KeyBinding.updateKeysByCode();
			options.write();
		}
	}
}
