package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
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

            Component original = cir.getReturnValue();
            Player player = (Player) (Object) this;

            if (HqTiersClientConfig.suppressRankedDuplicates) {
                String text = original == null ? "" : original.getString();
                if (text.contains("MT") || text.contains("LT") || text.contains("HT")) {
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
                if (base.getString().contains(tier.getString())) return;

                Component merged = HqTiersClientConfig.nametagAlignment == HqTiersClientConfig.NametagAlignment.LEFT
                        ? tier.copy().append(separator()).append(base)
                        : base.copy().append(separator()).append(tier);

                cir.setReturnValue(merged);
            });
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private static Component separator() {
        return Component.literal(" | ").withStyle(net.minecraft.ChatFormatting.GRAY);
    }
}