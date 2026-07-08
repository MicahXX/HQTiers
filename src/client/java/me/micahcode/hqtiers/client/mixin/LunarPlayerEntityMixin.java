package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

@Mixin(PlayerEntity.class)
public class LunarPlayerEntityMixin {
	// todo: this is also a little bugged
	private static final String LUNAR_MOD_ID = "ichor";

	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
	private void hqtiers$appendLunarNametagStats(CallbackInfoReturnable<Text> cir) {
		if (!FabricLoader.getInstance().isModLoaded(LUNAR_MOD_ID)) return;
		if (!HqTiersClientConfig.nametagEnabled) return;

		Text original = cir.getReturnValue();
		if (HqTiersClientConfig.suppressRankedDuplicates) return; // && RankedMatchDetector.nameAlreadyHasTierInfo(original)

		PlayerEntity player = (PlayerEntity) (Object) this;
		HqTiersClientState.cache().fetch(player.getUuid());
		HqTiersClientState.cache().getIfFresh(player.getUuid()).ifPresent(stats -> {
			Text suffix = HqTiersFormatter.compact(stats);
			String originalString = original == null ? "" : original.getString();
			String suffixString = suffix.getString();
			if (suffixString.isEmpty()) return;
			if (originalString.contains(suffixString)) return;

			Text baseName = original == null ? player.getName() : original;
			Text name = HqTiersClientConfig.nametagAlignment == HqTiersClientConfig.NametagAlignment.LEFT
					? suffix.copy().append(joiner(suffix, baseName)).append(baseName)
					: baseName.copy().append(joiner(baseName, suffix)).append(suffix);
			cir.setReturnValue(name);
		});
	}

	private static Text joiner(Text left, Text right) {
		String leftValue = left.getString();
		String rightValue = right.getString();
		return hasSeparatorEdge(leftValue, rightValue) || leftValue.endsWith(" ") || rightValue.startsWith(" ")
				? Text.empty() : Text.literal(" ");
	}

	private static boolean hasSeparatorEdge(String left, String right) {
		return left.endsWith("|") || right.startsWith("|");
	}
}
