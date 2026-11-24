package me.micahcode.hqtiers.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.nametag.HqTiersFormatter;
import me.micahcode.hqtiers.client.nametag.NametagDuplicateDetector;
import me.micahcode.hqtiers.client.nametag.StatPosition;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarStatsRendererMixin {
    @Unique private static final RenderStateDataKey<Component> HQTIERS_STATS = RenderStateDataKey.create();

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void hqtiers$extractStats(Avatar avatar, AvatarRenderState state, float tick, CallbackInfo ci) {
        FabricRenderState data = (FabricRenderState) state;
        data.setData(HQTIERS_STATS, null);
        if (!(avatar instanceof Player player) || !HqTiersClientConfig.nametagEnabled
                || HqTiersClientConfig.statPosition == StatPosition.INLINE || state.nameTag == null) return;
        if (player.getPassengers().stream().anyMatch(Display.TextDisplay.class::isInstance)) return;
        if (HqTiersClientConfig.suppressRankedDuplicates
                && NametagDuplicateDetector.hasTierLabel(state.nameTag.getString(), player.getScoreboardName())) return;
        HqTiersClientState.cache().get(player.getUUID()).ifPresent(stats -> {
            Component line = HqTiersFormatter.parts(stats, true).left();
            if (!line.getString().isEmpty()) data.setData(HQTIERS_STATS, line);
        });
    }

    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"))
    private void hqtiers$submitStats(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        Component line = ((FabricRenderState) state).getData(HQTIERS_STATS);
        if (line == null || state.nameTag == null || state.nameTagAttachment == null) return;
        int rows = HqTiersClientConfig.statPosition == StatPosition.BELOW ? -1 : state.scoreText == null ? 1 : 2;
        var anchor = state.nameTagAttachment.add(0, rows * 9 * 1.15 * 0.025, 0);
        collector.submitNameTag(pose, anchor, state.showExtraEars ? -10 : 0, line,
                !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);
    }
}
