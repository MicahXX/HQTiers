package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.model.HqTiersStats;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.client.render.entity.state.TextDisplayEntityRenderState;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(DisplayEntityRenderer.TextDisplayEntityRenderer.class)
public class TextDisplayEntityRendererMixin {

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;Lnet/minecraft/client/render/entity/state/TextDisplayEntityRenderState;F)V",
            at = @At("TAIL"),
            require = 0
    )
    private void hqtiers$mergeIntoTextDisplay(
            DisplayEntity.TextDisplayEntity entity,
            TextDisplayEntityRenderState state,
            float tickProgress,
            CallbackInfo ci
    ) {
        try {
            if (!HqTiersClientConfig.nametagEnabled) return;
            if (state.textLines == null) return;

            if (!(entity.getVehicle() instanceof PlayerEntity player)) return;

            String scoreboardName = player.getNameForScoreboard();
            List<DisplayEntity.TextDisplayEntity.TextLine> lines = state.textLines.lines();

            for (int i = 0; i < lines.size(); i++) {
                DisplayEntity.TextDisplayEntity.TextLine line = lines.get(i);

                Text baseStyled = styledTextFromOrdered(line.contents());
                String plain = baseStyled.getString();

                if (plain.isBlank() || !plain.contains(scoreboardName)) continue;

                Optional<HqTiersStats> statsOpt = HqTiersClientState.cache().getIfFresh(player.getUuid());
                if (statsOpt.isEmpty()) {
                    HqTiersClientState.cache().fetch(player.getUuid());
                    return;
                }

                Text tier = HqTiersFormatter.compact(statsOpt.get());
                if (tier.getString().isEmpty() || plain.contains(tier.getString())) return;

                Text merged = HqTiersClientConfig.nametagAlignment == HqTiersClientConfig.NametagAlignment.LEFT
                        ? tier.copy().append(separator()).append(baseStyled)
                        : baseStyled.copy().append(separator()).append(tier);

                OrderedText newContents = merged.asOrderedText();
                int newWidth = MinecraftClient.getInstance().textRenderer.getWidth(merged);

                List<DisplayEntity.TextDisplayEntity.TextLine> newLines = new ArrayList<>(lines);
                newLines.set(i, new DisplayEntity.TextDisplayEntity.TextLine(newContents, newWidth));

                int newMaxWidth = newLines.stream()
                        .mapToInt(DisplayEntity.TextDisplayEntity.TextLine::width)
                        .max()
                        .orElse(state.textLines.width());

                state.textLines = new DisplayEntity.TextDisplayEntity.TextLines(newLines, newMaxWidth);
                return;
            }
        } catch (Throwable ignored) {
            // Server-controlled text display formatting varies too much to
            // guarantee compatibility — fail safe, leave text untouched.
        }
    }

    private static Text styledTextFromOrdered(OrderedText ordered) {
        MutableText result = Text.empty();
        StringBuilder current = new StringBuilder();
        Style[] currentStyle = { Style.EMPTY };
        boolean[] skippingLeading = { true };

        ordered.accept((index, style, codePoint) -> {
            if (skippingLeading[0]) {
                if (codePoint == ' ') return true; // skip leading spaces entirely
                skippingLeading[0] = false;
            }

            if (!style.equals(currentStyle[0]) && current.length() > 0) {
                result.append(Text.literal(current.toString()).setStyle(currentStyle[0]));
                current.setLength(0);
            }
            currentStyle[0] = style;
            current.appendCodePoint(codePoint);
            return true;
        });

        if (current.length() > 0) {
            result.append(Text.literal(current.toString()).setStyle(currentStyle[0]));
        }

        return result;
    }

    private static Text separator() {
        return Text.literal(" | ").formatted(net.minecraft.util.Formatting.GRAY);
    }
}