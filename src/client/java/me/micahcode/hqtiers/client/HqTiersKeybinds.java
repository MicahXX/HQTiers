package me.micahcode.hqtiers.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.leaderboard.HqTiersLeaderboardScreen;
import me.micahcode.hqtiers.client.leaderboard.HqTiersPlayerStatsScreen;
import me.micahcode.hqtiers.client.model.HqTiersLadder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

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
                InputConstants.KEY_RIGHT,
                "category.hqtiers"
        ));

        KeyMapping cycleBack = KeyMappingHelper.registerKeyMapping(HqTiersMinecraftCompat.keyBinding(
                "key.hqtiers.cycle_mode_back",
                InputConstants.KEY_LEFT,
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
                if (client.screen instanceof HqTiersPlayerStatsScreen) {
                    client.setScreen(null);
                } else {
                    client.setScreen(new HqTiersLeaderboardScreen(HqTiersClientState.leaderboardClient()));
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
                    client.setScreen(new HqTiersPlayerStatsScreen(
                            null,
                            client.player.getUUID().toString(),
                            client.player.getName().getString()
                    ));
                }
            }
        });
    }

    private static final List<Object> CYCLE = buildCycle();

    private static List<Object> buildCycle() {
        List<Object> cycle = new ArrayList<>();
        cycle.add(HqTiersClientConfig.DisplayMode.GLOBAL);
        cycle.add(HqTiersClientConfig.DisplayMode.HIGHEST_TIER);
        cycle.addAll(HqTiersLadder.ranked());
        return cycle;
    }

    private static void cycleLadder(net.minecraft.client.Minecraft client, int direction) {
        Object current = switch (HqTiersClientConfig.displayMode) {
            case GLOBAL -> HqTiersClientConfig.DisplayMode.GLOBAL;
            case HIGHEST_TIER -> HqTiersClientConfig.DisplayMode.HIGHEST_TIER;
            case PREFERRED_LADDER -> HqTiersLadder.fromString(HqTiersClientConfig.preferredLadder);
        };

        int idx = Math.max(0, CYCLE.indexOf(current));
        Object next = CYCLE.get(((idx + direction) % CYCLE.size() + CYCLE.size()) % CYCLE.size());

        String label;
        if (next == HqTiersClientConfig.DisplayMode.GLOBAL) {
            HqTiersClientConfig.displayMode = HqTiersClientConfig.DisplayMode.GLOBAL;
            label = "Global";
        } else if (next == HqTiersClientConfig.DisplayMode.HIGHEST_TIER) {
            HqTiersClientConfig.displayMode = HqTiersClientConfig.DisplayMode.HIGHEST_TIER;
            label = "Highest Tier";
        } else {
            HqTiersLadder nextLadder = (HqTiersLadder) next;
            HqTiersClientConfig.displayMode = HqTiersClientConfig.DisplayMode.PREFERRED_LADDER;
            HqTiersClientConfig.preferredLadder = nextLadder.name();
            label = nextLadder.displayName();
        }

        HqTiersClientConfig.save();

        if (client.player != null) {
            net.minecraft.network.chat.MutableComponent msg = net.minecraft.network.chat.Component.literal("HQTiers: " + label + " ")
                    .withStyle(ChatFormatting.GOLD);

            String iconLadderName = next instanceof HqTiersLadder ladder ? ladder.name() : HqTiersLadder.GLOBAL.name();
            msg.append(HqTiersFormatter.icon(iconLadderName));

            client.gui.setOverlayMessage(msg, false);
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