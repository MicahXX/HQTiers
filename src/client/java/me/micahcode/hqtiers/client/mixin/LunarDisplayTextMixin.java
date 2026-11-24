package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.nametag.StatPosition;
import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import me.micahcode.hqtiers.client.nametag.HqTiersFormatter;
import me.micahcode.hqtiers.client.nametag.NametagDuplicateDetector;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(Display.TextDisplay.class)
public class LunarDisplayTextMixin {
    // for Lunar we need to use a unique method to display the tier in the nametag
    // as Lunar changes mixins so ye
    @Unique
    private static final String LUNAR_MOD_ID = "ichor";

    @Unique private Display.TextDisplay.CachedInfo hqtiers$sourceLines;
    @Unique private Display.TextDisplay.CachedInfo hqtiers$currentLines;
    @Unique private Component hqtiers$currentText;

    @Inject(method = "cacheDisplay", at = @At("RETURN"), cancellable = true)
    private void hqtiers$refreshDisplayLines(Display.TextDisplay.LineSplitter splitter, CallbackInfoReturnable<Display.TextDisplay.CachedInfo> cir) {
        if (!FabricLoader.getInstance().isModLoaded(LUNAR_MOD_ID)) return;
        Display.TextDisplay self = (Display.TextDisplay) (Object) this;
        if (!(self.getVehicle() instanceof Player) || self.textRenderState() == null) return;
        Component text = self.getText();
        if (text.equals(self.textRenderState().text())) return;
        if (hqtiers$sourceLines != cir.getReturnValue() || !text.equals(hqtiers$currentText)) {
            hqtiers$sourceLines = cir.getReturnValue();
            hqtiers$currentText = text;
            hqtiers$currentLines = splitter.split(text, self.textRenderState().lineWidth());
        }
        cir.setReturnValue(hqtiers$currentLines);
    }

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
            if (HqTiersClientConfig.suppressRankedDuplicates
                    && NametagDuplicateDetector.hasTierLabel(plain, scoreboardName)) return;

            Optional<HqTiersStats> statsOpt = HqTiersClientState.cache().get(player.getUUID());
            if (statsOpt.isEmpty()) {
                return;
            }

            StatPosition position = HqTiersClientConfig.statPosition;
            Component tier = HqTiersFormatter.parts(statsOpt.get(), position != StatPosition.INLINE).combined();
            if (tier.getString().isEmpty()) return;

            Component merged = position == StatPosition.INLINE ? HqTiersFormatter.decorateName(statsOpt.get(), original)
                    : position.insertIntoDisplay(original, scoreboardName, tier);

            cir.setReturnValue(merged);
        } catch (Throwable ignored) {
        }
    }

}
