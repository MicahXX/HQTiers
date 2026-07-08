package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.HqTiersMinecraftCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
	@Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
	private void hqtiers$appendTabStats(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
		if (!HqTiersClientConfig.tabListEnabled) return;

		String rawName = cir.getReturnValue().getString();
		if (rawName != null && rawName.matches("^\\d{2,5}[\\s|].*") && HqTiersClientConfig.suppressRankedDuplicates) return;
		if (HqTiersClientConfig.suppressRankedDuplicates) return; // RankedMatchDetector.nameAlreadyHasTierInfo(cir.getReturnValue()

		HqTiersClientState.cache().fetch(HqTiersMinecraftCompat.profileId(entry.getProfile()));
		HqTiersClientState.cache().getIfFresh(HqTiersMinecraftCompat.profileId(entry.getProfile())).ifPresent(stats -> {
			Text suffix = HqTiersFormatter.compact(stats);
			String suffixStr = suffix.getString();
			if (suffixStr.isEmpty()) return;
			if (cir.getReturnValue().getString().contains(suffixStr)) return;

			Text cleanName = stripLeadingSeparator(cir.getReturnValue());

			if (HqTiersClientConfig.nametagAlignment == HqTiersClientConfig.NametagAlignment.LEFT) {
				cir.setReturnValue(suffix.copy()
						.append(Text.literal(" "))
						.append(cleanName));
			} else {
				cir.setReturnValue(cleanName.copy()
						.append(Text.literal(" "))
						.append(suffix));
			}
		});
	}

	private static Text stripLeadingSeparator(Text text) {
		String raw = text.getString();
		String stripped = raw.replaceFirst("^\\s*\\|\\s*", "");
		return stripped.equals(raw) ? text : Text.literal(stripped);
	}
}