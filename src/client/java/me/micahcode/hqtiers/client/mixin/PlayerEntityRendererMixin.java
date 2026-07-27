package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.model.HqTiersStats;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.text.Text;


@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {


    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void hqtiers$appendNametagStats(
            PlayerLikeEntity player,
            PlayerEntityRenderState state,
            float tickProgress,
            CallbackInfo ci
    ) {

        if (!HqTiersClientConfig.nametagEnabled) {
            return;
        }


        Text currentName = renderName(player, state);


        // Don't spam fetch every render tick
        if (HqTiersClientState.cache()
                .getIfFresh(player.getUuid())
                .isEmpty()) {

            HqTiersClientState.cache()
                    .fetch(player.getUuid());
        }


        HqTiersClientState.cache()
                .getIfFresh(player.getUuid())
                .ifPresent(stats -> {

                    Text tier = HqTiersFormatter.compact(stats);

                    if (tier.getString().isEmpty()) {
                        return;
                    }


                    // Prevent duplicate tiers
                    if (currentName != null &&
                            currentName.getString()
                                    .contains(tier.getString())) {
                        return;
                    }


                    state.displayName =
                            HqTiersFormatter.nametag(
                                    stats,
                                    currentName == null
                                            ? Text.empty()
                                            : currentName
                            );
                });
    }


    private static Text renderName(
            PlayerLikeEntity player,
            PlayerEntityRenderState state
    ) {

        // PvPHQ/Lunar already modified name
        if (state.displayName != null) {
            return state.displayName;
        }


        if (state.playerName != null) {
            return state.playerName;
        }


        Text displayName = player.getDisplayName();

        return displayName == null
                ? player.getName()
                : displayName;
    }
}