package me.micahcode.hqtiers.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import me.micahcode.hqtiers.client.nametag.HqTiersFormatter;
import me.micahcode.hqtiers.client.HqTiersMinecraftCompat;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.Optional;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerTabOverlay.class)
public class PlayerListHudMixin {

    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"), require = 0)
    private Component hqtiers$appendTabStats(Component current, PlayerInfo entry) {
        if (!HqTiersClientConfig.tabListEnabled) return current;

        var uuid = HqTiersMinecraftCompat.profileId(entry.getProfile());

        return HqTiersClientState.cache().get(uuid)
                .map(stats -> {
                    var parts = HqTiersFormatter.parts(stats, HqTiersClientConfig.tabListAlwaysLeft);
                    if (parts.isEmpty() || parts.surrounds(current)) return current;
                    return parts.around(stripLeadingSeparator(current));
                })
                .orElse(current);
    }

    @Unique
    private static Component stripLeadingSeparator(Component name) {
        String raw = name.getString();
        String stripped = raw.stripLeading();
        if (!stripped.startsWith("|")) return name;
        int[] skip = {raw.length() - stripped.substring(1).stripLeading().length()};
        var result = Component.empty();
        name.visit((style, text) -> {
            int cut = Math.min(skip[0], text.length());
            skip[0] -= cut;
            if (cut < text.length()) result.append(Component.literal(text.substring(cut)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}
