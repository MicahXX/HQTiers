package me.micahcode.hqtiers.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    @ModifyReturnValue(method = "getDisplayName", at = @At("RETURN"), require = 0)
    private Text hqtiers$appendNametagStats(Text original) {
        try {
            if (!HqTiersClientConfig.nametagEnabled) return original;

            PlayerEntity player = (PlayerEntity) (Object) this;

            // If a TextDisplay is riding this player, that render path
            // (TextDisplayEntityRendererMixin) already handles the tier.
            // Skip here to avoid appending it twice.
            if (hasTextDisplayPassenger(player)) return original;

            if (HqTiersClientConfig.suppressRankedDuplicates) {
                String text = original == null ? "" : original.getString();
                if (text.contains("MT") || text.contains("LT") || text.contains("HT")) {
                    return original;
                }
            }

            HqTiersClientState.cache().fetch(player.getUuid());

            return HqTiersClientState.cache()
                    .getIfFresh(player.getUuid())
                    .map(stats -> {
                        Text base = original == null ? player.getName() : original;

                        if (base.getString().startsWith(" ")) {
                            base = Text.literal(base.getString().stripLeading()).setStyle(base.getStyle());
                        }

                        Text tier = HqTiersFormatter.compact(stats);

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

    private static boolean hasTextDisplayPassenger(PlayerEntity player) {
        for (var passenger : player.getPassengerList()) {
            if (passenger instanceof DisplayEntity.TextDisplayEntity) {
                return true;
            }
        }
        return false;
    }

    private static Text separator() {
        return Text.literal(" | ").formatted(net.minecraft.util.Formatting.GRAY);
    }
}