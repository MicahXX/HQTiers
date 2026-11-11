package me.micahcode.hqtiers.client.leaderboard;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

final class LeaderboardHeads {
    private static final LinkedHashMap<UUID, CompletableFuture<Optional<PlayerSkin>>> CACHE = new LinkedHashMap<>(128, 0.75f, true);
    private static final java.util.concurrent.ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "hqtiers-heads");
        thread.setDaemon(true);
        return thread;
    });

    private LeaderboardHeads() {}

    static void draw(GuiGraphicsExtractor ctx, String value, int x, int y, int size) {
        UUID uuid;
        try { uuid = UUID.fromString(value); }
        catch (IllegalArgumentException error) { return; }
        var mc = Minecraft.getInstance();
        var online = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(uuid);
        PlayerSkin skin = online == null ? cached(mc, uuid) : online.getSkin();
        PlayerFaceExtractor.extractRenderState(ctx, skin, x, y, size);
    }

    private static PlayerSkin cached(Minecraft mc, UUID uuid) {
        var future = CACHE.get(uuid);
        if (future == null) {
            if (CACHE.size() >= 128) {
                var completed = CACHE.entrySet().stream().filter(entry -> entry.getValue().isDone()).findFirst();
                if (completed.isEmpty()) return DefaultPlayerSkin.get(uuid);
                CACHE.remove(completed.get().getKey());
            }
            future = CompletableFuture.supplyAsync(() -> mc.services().profileResolver().fetchById(uuid), EXECUTOR)
                    .thenComposeAsync(profile -> profile.map(mc.getSkinManager()::get)
                            .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())), mc)
                    .exceptionally(error -> Optional.empty());
            CACHE.put(uuid, future);
        }
        return future.getNow(Optional.empty()).orElseGet(() -> DefaultPlayerSkin.get(uuid));
    }
}
