package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

// just to make it work on lunar and pvphq
@Mixin(Display.TextDisplay.class)
public class LunarDisplayTextMixin {

    private static final String LUNAR_MOD_ID = "ichor";

    @Inject(
            method = "getText",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void hqtiers$appendTierToText(CallbackInfoReturnable<Component> cir) {
        try {
            if (!FabricLoader.getInstance().isModLoaded(LUNAR_MOD_ID)) return;
            if (!HqTiersClientConfig.nametagEnabled) return;

            Display.TextDisplay self = (Display.TextDisplay) (Object) this;
            if (!(self.getVehicle() instanceof Player player)) return;

            Component original = cir.getReturnValue();
            String plain = original == null ? "" : original.getString();
            String scoreboardName = player.getScoreboardName();

            if (plain.isBlank() || !plain.contains(scoreboardName)) return;

            Optional<HqTiersStats> statsOpt = HqTiersClientState.cache().getIfFresh(player.getUUID());
            if (statsOpt.isEmpty()) {
                HqTiersClientState.cache().fetch(player.getUUID());
                return;
            }

            Component tier = HqTiersFormatter.compact(statsOpt.get());
            if (tier.getString().isEmpty() || plain.contains(tier.getString())) return;

            Component merged = HqTiersClientConfig.nametagAlignment == HqTiersClientConfig.NametagAlignment.LEFT
                    ? tier.copy().append(separator()).append(original)
                    : original.copy().append(separator()).append(tier);

            cir.setReturnValue(merged);
        } catch (Throwable ignored) {
            // if there is some kind of issue just show nothing
        }
    }

    private static Component separator() {
        return Component.literal(" | ").withStyle(net.minecraft.ChatFormatting.GRAY);
    }
}