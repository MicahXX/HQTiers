package me.micahcode.hqtiers.client.mixin;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;


@Mixin(PlayerEntity.class)
public class LunarPlayerEntityMixin {

    private static final String LUNAR_MOD_ID = "ichor";


    @Inject(
            method = "getDisplayName",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void hqtiers$appendLunarNametagStats(
            CallbackInfoReturnable<Text> cir
    ) {

        try {

            if (!FabricLoader.getInstance().isModLoaded(LUNAR_MOD_ID))
                return;

            if (!HqTiersClientConfig.nametagEnabled)
                return;


            Text original = cir.getReturnValue();


            PlayerEntity player =
                    (PlayerEntity) (Object) this;


            if (HqTiersClientConfig.suppressRankedDuplicates) {

                String text = original == null
                        ? ""
                        : original.getString();

                if (text.contains("MT")
                        || text.contains("LT")
                        || text.contains("HT")) {
                    return;
                }
            }


            HqTiersClientState.cache()
                    .fetch(player.getUuid());


            HqTiersClientState.cache()
                    .getIfFresh(player.getUuid())
                    .ifPresent(stats -> {


                        Text base =
                                original == null
                                        ? player.getName()
                                        : original;


                        Text tier =
                                HqTiersFormatter.compact(stats);


                        if (tier.getString().isEmpty())
                            return;


                        Text result;


                        if (HqTiersClientConfig.nametagAlignment ==
                                HqTiersClientConfig.NametagAlignment.LEFT) {

                            result = tier.copy()
                                    .append(Text.literal(" "))
                                    .append(base);

                        } else {

                            result = base.copy()
                                    .append(Text.literal(" "))
                                    .append(tier);
                        }


                        cir.setReturnValue(result);

                    });


        } catch (Throwable ignored) {
            // Lunar modifies name rendering internally.
            // Keep vanilla/Lunar name if incompatible.
        }
    }
}