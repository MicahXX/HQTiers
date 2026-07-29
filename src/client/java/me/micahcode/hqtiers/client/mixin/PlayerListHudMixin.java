package me.micahcode.hqtiers.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.HqTiersMinecraftCompat;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerTabOverlay.class)
public class PlayerListHudMixin {

    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"), require = 0)
    private Component hqtiers$appendTabStats(Component current, PlayerInfo entry) {
        if (!HqTiersClientConfig.tabListEnabled) return current;

        var uuid = HqTiersMinecraftCompat.profileId(entry.getProfile());

        // Trigger background fetch, but only apply from cache synchronously —
        // the return value is dead by the time an async callback would fire.
        if (HqTiersClientState.cache().getIfFresh(uuid).isEmpty()) {
            HqTiersClientState.cache().fetch(uuid);
        }

        return HqTiersClientState.cache().getIfFresh(uuid)
                .map(stats -> {
                    Component suffix = HqTiersFormatter.compact(stats);
                    if (suffix.getString().isEmpty()) return current;
                    if (current.getString().contains(suffix.getString())) return current;

                    Component cleanName = stripLeadingSeparator(current);
                    return HqTiersClientConfig.nametagAlignment == HqTiersClientConfig.NametagAlignment.LEFT
                            ? suffix.copy().append(Component.literal(" ")).append(cleanName)
                            : cleanName.copy().append(Component.literal(" ")).append(suffix);
                })
                .orElse(current);
    }

    private static Component stripLeadingSeparator(Component text) {
        String raw = text.getString();
        String stripped = raw.replaceFirst("^\\s*\\|\\s*", "");
        return stripped.equals(raw) ? text : Component.literal(stripped);
    }
}