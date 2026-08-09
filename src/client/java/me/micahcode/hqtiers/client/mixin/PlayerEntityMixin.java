package me.micahcode.hqtiers.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class PlayerEntityMixin {

    @ModifyReturnValue(method = "getDisplayName", at = @At("RETURN"), require = 0)
    private Component hqtiers$appendNametagStats(Component original) {
        try {
            if (!HqTiersClientConfig.nametagEnabled) return original;

            Player player = (Player) (Object) this;

            if (hasTextDisplayPassenger(player)) return original;

            if (HqTiersClientConfig.suppressRankedDuplicates) {
                String text = original == null ? "" : original.getString();
                if (text.contains("MT") || text.contains("LT") || text.contains("HT")) {
                    return original;
                }
            }

            HqTiersClientState.cache().fetch(player.getUUID());

            return HqTiersClientState.cache()
                    .getIfFresh(player.getUUID())
                    .map(stats -> {
                        Component base = original == null ? player.getName() : original;

                        if (base.getString().startsWith(" ")) {
                            base = Component.literal(base.getString().stripLeading()).setStyle(base.getStyle());
                        }

                        Component tier = HqTiersFormatter.compact(stats);

                        if (tier.getString().isEmpty()) return base;
                        if (base.getString().contains(tier.getString())) return base;

                        return HqTiersClientConfig.nametagAlignment == HqTiersClientConfig.NametagAlignment.LEFT
                                ? tier.copy().append(separator()).append(base)
                                : base.copy().append(separator()).append(tier);
                    })
                    .orElse(original);

        } catch (Throwable ignored) {
            return original;
        }
    }

    private static boolean hasTextDisplayPassenger(Player player) {
        for (var passenger : player.getPassengers()) {
            if (passenger instanceof Display.TextDisplay) {
                return true;
            }
        }
        return false;
    }

    private static Component separator() {
        return Component.literal(" | ").withStyle(net.minecraft.ChatFormatting.GRAY);
    }
}