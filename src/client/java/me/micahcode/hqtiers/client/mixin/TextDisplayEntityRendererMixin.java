package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public class TextDisplayEntityRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V",
            at = @At("TAIL"),
            require = 0
    )
    private void hqtiers$mergeIntoTextDisplay(
            Display.TextDisplay entity,
            TextDisplayEntityRenderState state,
            float tickProgress,
            CallbackInfo ci
    ) {
        try {
            if (!HqTiersClientConfig.nametagEnabled) return;
            if (state.cachedInfo == null) return;

            if (!(entity.getVehicle() instanceof Player player)) return;

            String scoreboardName = player.getScoreboardName();
            List<Display.TextDisplay.CachedLine> lines = state.cachedInfo.lines();

            for (int i = 0; i < lines.size(); i++) {
                Display.TextDisplay.CachedLine line = lines.get(i);

                Component baseStyled = styledTextFromOrdered(line.contents());
                String plain = baseStyled.getString();

                if (plain.isBlank() || !plain.contains(scoreboardName)) continue;

                Optional<HqTiersStats> statsOpt = HqTiersClientState.cache().getIfFresh(player.getUUID());
                if (statsOpt.isEmpty()) {
                    HqTiersClientState.cache().fetch(player.getUUID());
                    return;
                }

                Component tier = HqTiersFormatter.compact(statsOpt.get());
                if (tier.getString().isEmpty() || plain.contains(tier.getString())) return;

                Component merged = HqTiersClientConfig.nametagAlignment == HqTiersClientConfig.NametagAlignment.LEFT
                        ? tier.copy().append(separator()).append(baseStyled)
                        : baseStyled.copy().append(separator()).append(tier);

                FormattedCharSequence newContents = merged.getVisualOrderText();
                int newWidth = Minecraft.getInstance().font.width(merged);

                List<Display.TextDisplay.CachedLine> newLines = new ArrayList<>(lines);
                newLines.set(i, new Display.TextDisplay.CachedLine(newContents, newWidth));

                int newMaxWidth = newLines.stream()
                        .mapToInt(Display.TextDisplay.CachedLine::width)
                        .max()
                        .orElse(state.cachedInfo.width());

                state.cachedInfo = new Display.TextDisplay.CachedInfo(newLines, newMaxWidth);
                return;
            }
        } catch (Throwable ignored) {
            // if there is some kind of issue just show nothing
        }
    }

    private static Component styledTextFromOrdered(FormattedCharSequence ordered) {
        MutableComponent result = Component.empty();
        StringBuilder current = new StringBuilder();
        Style[] currentStyle = { Style.EMPTY };
        boolean[] skippingLeading = { true };

        ordered.accept((index, style, codePoint) -> {
            if (skippingLeading[0]) {
                if (codePoint == ' ') return true; // skip leading spaces entirely
                skippingLeading[0] = false;
            }

            if (!style.equals(currentStyle[0]) && current.length() > 0) {
                result.append(Component.literal(current.toString()).setStyle(currentStyle[0]));
                current.setLength(0);
            }
            currentStyle[0] = style;
            current.appendCodePoint(codePoint);
            return true;
        });

        if (current.length() > 0) {
            result.append(Component.literal(current.toString()).setStyle(currentStyle[0]));
        }

        return result;
    }

    private static Component separator() {
        return Component.literal(" | ").withStyle(net.minecraft.ChatFormatting.GRAY);
    }
}