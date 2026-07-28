package me.micahcode.hqtiers.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.HqTiersMinecraftCompat;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    @ModifyReturnValue(method = "getPlayerName", at = @At("RETURN"), require = 0)
    private Text hqtiers$appendTabStats(Text current, PlayerListEntry entry) {
        if (!HqTiersClientConfig.tabListEnabled) return current;

        var uuid = HqTiersMinecraftCompat.profileId(entry.getProfile());

        // Trigger background fetch, but only apply from cache synchronously —
        // the return value is dead by the time an async callback would fire.
        if (HqTiersClientState.cache().getIfFresh(uuid).isEmpty()) {
            HqTiersClientState.cache().fetch(uuid);
        }

        return HqTiersClientState.cache().getIfFresh(uuid)
                .map(stats -> {
                    Text suffix = HqTiersFormatter.compact(stats);
                    if (suffix.getString().isEmpty()) return current;
                    if (current.getString().contains(suffix.getString())) return current;

                    Text cleanName = stripLeadingSeparator(current);
                    return HqTiersClientConfig.nametagAlignment == HqTiersClientConfig.NametagAlignment.LEFT
                            ? suffix.copy().append(Text.literal(" ")).append(cleanName)
                            : cleanName.copy().append(Text.literal(" ")).append(suffix);
                })
                .orElse(current);
    }

    private static Text stripLeadingSeparator(Text text) {
        String raw = text.getString();
        String stripped = raw.replaceFirst("^\\s*\\|\\s*", "");
        return stripped.equals(raw) ? text : Text.literal(stripped);
    }
}