package me.micahcode.hqtiers.client;

import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import me.micahcode.hqtiers.client.fetching.HqTiersCache;
import me.micahcode.hqtiers.client.input.HqTiersCommands;
import me.micahcode.hqtiers.client.input.HqTiersKeybinds;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class HqTiersClient implements ClientModInitializer {
    private int prefetchTicks;
    private boolean leaderboardWarmed;

    @Override
    public void onInitializeClient() {
        HqTiersClientConfig.load();
        HqTiersCache cache = HqTiersClientState.cache();
        HqTiersCommands.register(cache);
        HqTiersKeybinds.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++prefetchTicks % 5 != 0 || client.player == null || client.level == null) return;
            if (!leaderboardWarmed) {
                HqTiersClientState.leaderboardClient().load("GLOBAL");
                leaderboardWarmed = true;
            }
            cache.get(client.player.getUUID());
            if (!HqTiersClientConfig.nametagEnabled && !HqTiersClientConfig.tabListEnabled) return;
            client.level.players().forEach(player -> cache.get(player.getUUID()));
        });
    }
}
