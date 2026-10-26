package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.HqTiersChatFormatter;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GuiMessage.class)
public class GuiMessageMixin {
    @ModifyArg(
            method = "splitLines",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"),
            index = 0
    )
    private FormattedText hqtiers$decorateChat(FormattedText text) {
        return text instanceof Component message ? HqTiersChatFormatter.decorate(message) : text;
    }
}
