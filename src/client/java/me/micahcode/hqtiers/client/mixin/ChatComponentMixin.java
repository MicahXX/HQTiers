package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.HqTiersChatFormatter;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Shadow
    protected abstract void refreshTrimmedMessages();

    @Inject(method = "tick", at = @At("TAIL"))
    private void hqtiers$refreshChat(CallbackInfo ci) {
        // refresh it on new config version
        if (HqTiersChatFormatter.needsRefresh()) refreshTrimmedMessages();
    }
}
