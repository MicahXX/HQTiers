package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.NametagDuplicateDetector;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class LunarPlayerEntityMixin {

    @Unique
    private static final String LUNAR_MOD_ID = "ichor";

    @Inject(
            method = "getDisplayName",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void hqtiers$appendLunarNametagStats(CallbackInfoReturnable<Component> cir) {
        try {
            if (!FabricLoader.getInstance().isModLoaded(LUNAR_MOD_ID)) return;
            if (!HqTiersClientConfig.nametagEnabled) return;

            Player player = (Player) (Object) this;

            if (hasTextDisplayPassenger(player)) return;

            Component original = cir.getReturnValue();

            if (HqTiersClientConfig.suppressRankedDuplicates) {
                String text = original == null ? "" : original.getString();
                if (NametagDuplicateDetector.hasTierLabel(text, player.getScoreboardName())) {
                    return;
                }
            }

            HqTiersClientState.cache().fetch(player.getUUID());
            HqTiersClientState.cache().getIfFresh(player.getUUID()).ifPresent(stats -> {
                Component base = original == null ? player.getName() : original;

                if (base.getString().startsWith(" ")) {
                    base = Component.literal(base.getString().stripLeading()).setStyle(base.getStyle());
                }

                Component tier = HqTiersFormatter.compact(stats);
                if (tier.getString().isEmpty()) return;

                Component merged = HqTiersFormatter.decorateName(stats, base);

                cir.setReturnValue(merged);
            });
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private static boolean hasTextDisplayPassenger(Player player) {
        for (var passenger : player.getPassengers()) {
            if (passenger instanceof Display.TextDisplay) {
                return true;
            }
        }
        return false;
    }

}