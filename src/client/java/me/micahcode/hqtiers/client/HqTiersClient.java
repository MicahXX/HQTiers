package me.micahcode.hqtiers.client;

import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;

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

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (message.getString().contains("SR Change")) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    UUID uuid = client.player.getUuid();
                    CompletableFuture.delayedExecutor(15, TimeUnit.SECONDS)
                            .execute(() -> {
                                cache.invalidate(uuid);
                                cache.fetch(uuid);
                            });
                }
            }
        });
    }
}
