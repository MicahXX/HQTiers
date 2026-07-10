package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.HqTiersMinecraftCompat;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void hqtiers$appendTabStats(
            PlayerListEntry entry,
            CallbackInfoReturnable<Text> cir
    ) {
        if (!HqTiersClientConfig.tabListEnabled) {
            return;
        }

        var uuid = HqTiersMinecraftCompat.profileId(entry.getProfile());

        HqTiersClientState.cache()
                .fetch(uuid)
                .thenAccept(stats -> {

                    if (stats == null) {
                        return;
                    }

                    Text suffix = HqTiersFormatter.compact(stats);

                    if (suffix.getString().isEmpty()) {
                        return;
                    }

                    Text current = cir.getReturnValue();

                    if (current.getString().contains(suffix.getString())) {
                        return;
                    }

                    Text cleanName = stripLeadingSeparator(current);

                    if (HqTiersClientConfig.nametagAlignment ==
                            HqTiersClientConfig.NametagAlignment.LEFT) {

                        cir.setReturnValue(
                                suffix.copy()
                                        .append(Text.literal(" "))
                                        .append(cleanName)
                        );

                    } else {

                        cir.setReturnValue(
                                cleanName.copy()
                                        .append(Text.literal(" "))
                                        .append(suffix)
                        );
                    }
                });
    }


    private static Text stripLeadingSeparator(Text text) {
        String raw = text.getString();
        String stripped = raw.replaceFirst("^\\s*\\|\\s*", "");

        return stripped.equals(raw)
                ? text
                : Text.literal(stripped);
    }
}