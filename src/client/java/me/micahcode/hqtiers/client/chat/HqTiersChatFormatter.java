package me.micahcode.hqtiers.client.chat;

import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import java.util.Optional;
import java.util.UUID;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class HqTiersChatFormatter {
    private static int lastConfigVersion = -1;
    private static long lastStatsRevision = -1;

    private HqTiersChatFormatter() {}

    public static Component decorate(Component message) {
        if (!HqTiersClientConfig.chatEnabled) return message;
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return message;
        var profiles = connection.getOnlinePlayers().stream().map(info -> info.getProfile()).toList();
        return ChatMessageDecorator.decorate(message, profiles, HqTiersChatFormatter::statsFor);
    }

    private static Optional<HqTiersStats> statsFor(UUID uuid) {
        return HqTiersClientState.cache().get(uuid);
    }

    public static boolean needsRefresh() {
        int configVersion = HqTiersClientConfig.configVersion();
        long statsRevision = HqTiersClientState.cache().revision();
        boolean changed = configVersion != lastConfigVersion || statsRevision != lastStatsRevision;
        lastConfigVersion = configVersion;
        lastStatsRevision = statsRevision;
        return changed;
    }
}
