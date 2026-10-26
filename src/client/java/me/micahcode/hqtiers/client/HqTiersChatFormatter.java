package me.micahcode.hqtiers.client;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class HqTiersChatFormatter {
    private static final AtomicBoolean REFRESH_PENDING = new AtomicBoolean();
    private static int lastConfigVersion = -1;

    private HqTiersChatFormatter() {}

    public static Component decorate(Component message) {
        if (!HqTiersClientConfig.chatEnabled) return message;
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return message;
        var profiles = connection.getOnlinePlayers().stream().map(info -> info.getProfile()).toList();
        return ChatMessageDecorator.decorate(message, profiles, HqTiersChatFormatter::statsFor);
    }

    private static Optional<HqTiersStats> statsFor(UUID uuid) {
        var cache = HqTiersClientState.cache();
        var stats = cache.getIfFresh(uuid);
        if (stats.isEmpty()) {
            cache.fetch(uuid).thenAccept(loaded -> {
                if (loaded != null) REFRESH_PENDING.set(true);
            });
        }
        return stats;
    }

    public static boolean needsRefresh() {
        int configVersion = HqTiersClientConfig.configVersion();
        boolean changed = configVersion != lastConfigVersion;
        lastConfigVersion = configVersion;
        return REFRESH_PENDING.getAndSet(false) || changed;
    }
}
