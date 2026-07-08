package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.HqTiersFormatter;
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
	@Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
	private void hqtiers$appendNametagStats(PlayerLikeEntity player, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
		if (!HqTiersClientConfig.nametagEnabled) return;

		Text name = renderName(player, state);
		if (HqTiersClientConfig.suppressRankedDuplicates && name != null) {
			return;
		}

		HqTiersClientState.cache().fetch(player.getUuid());
		HqTiersClientState.cache().getIfFresh(player.getUuid()).ifPresent(stats -> {
			Text suffix = HqTiersFormatter.compact(stats);
			String suffixStr = suffix.getString();
			if (suffixStr.isEmpty()) return;

			Text currentName = renderName(player, state);

			if (currentName != null && currentName.getString().contains(suffixStr)) return;

			if (HqTiersClientConfig.nametagAlignment == HqTiersClientConfig.NametagAlignment.LEFT) {
				state.displayName = suffix.copy()
						.append(Text.literal(" "))
						.append(currentName == null ? Text.empty() : currentName);
			} else {
				state.displayName = (currentName == null ? Text.empty() : currentName.copy())
						.append(Text.literal(" "))
						.append(suffix);
			}
		});
	}

	private static Text renderName(PlayerLikeEntity player, PlayerEntityRenderState state) {
		if (state.displayName != null) {
			return state.displayName;
		}
		if (state.playerName != null) {
			return state.playerName;
		}

		Text displayName = player.getDisplayName();
		return displayName == null ? player.getName() : displayName;
	}
}
