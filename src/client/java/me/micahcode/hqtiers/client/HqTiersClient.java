package me.micahcode.hqtiers.client;

import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import net.fabricmc.api.ClientModInitializer;

public class HqTiersClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HqTiersClientConfig.load();

        HqTiersCache cache = HqTiersClientState.cache();

        HqTiersCommands.register(cache);
        HqTiersKeybinds.register();
    }
}