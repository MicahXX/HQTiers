package me.micahcode.hqtiers.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.HqTiersMinecraftCompat;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerTabOverlay.class)
public class PlayerListHudMixin {

    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"), require = 0)
    private Component hqtiers$appendTabStats(Component current, PlayerInfo entry) {
        if (!HqTiersClientConfig.tabListEnabled) return current;

        var uuid = HqTiersMinecraftCompat.profileId(entry.getProfile());

        if (HqTiersClientState.cache().getIfFresh(uuid).isEmpty()) {
            HqTiersClientState.cache().fetch(uuid);
        }

        return HqTiersClientState.cache().getIfFresh(uuid)
                .map(stats -> {
                    Component formatted = HqTiersFormatter.compact(stats);

                    if (formatted.getString().isEmpty()) {
                        return current;
                    }

                    Component cleanName = stripLeadingSeparator(current);

                    return HqTiersFormatter.decorateName(stats, cleanName);
                })
                .orElse(current);
    }

    @Unique
    private static Component stripLeadingSeparator(Component text) {
        String raw = text.getString();
        String stripped = raw.replaceFirst("^\\s*\\|\\s*", "");
        return stripped.equals(raw) ? text : Component.literal(stripped);
    }

}