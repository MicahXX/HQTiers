package me.micahcode.hqtiers.client;

import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import me.micahcode.hqtiers.client.fetching.HqTiersCache;
import me.micahcode.hqtiers.client.input.HqTiersCommands;
import me.micahcode.hqtiers.client.input.HqTiersKeybinds;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.util.Comparator;

public class HqTiersClient implements ClientModInitializer {
    private int nearbyRefreshTicks;

    @Override
    public void onInitializeClient() {
        HqTiersClientConfig.load();
        HqTiersCache cache = HqTiersClientState.cache();
        HqTiersCommands.register(cache);
        HqTiersKeybinds.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++nearbyRefreshTicks % 10 != 0 || client.player == null || client.level == null) return;
            if (!HqTiersClientConfig.nametagEnabled && !HqTiersClientConfig.tabListEnabled) return;
            client.level.players().stream()
                    .filter(player -> player.distanceToSqr(client.player) <= 32 * 32)
                    .sorted(Comparator.comparingDouble(player -> player.distanceToSqr(client.player)))
                    .forEach(player -> cache.get(player.getUUID(), true));
        });
    }
}
