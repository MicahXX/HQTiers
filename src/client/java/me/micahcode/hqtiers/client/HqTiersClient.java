package me.micahcode.hqtiers.client;

import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class HqTiersClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HqTiersClientConfig.load();
        HqTiersCache cache = HqTiersClientState.cache();
        HqTiersCommands.register(cache);
        HqTiersKeybinds.register();
    }
}
