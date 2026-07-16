package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.client.render.VrGunAnimationPolicy;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.event.TickAnimationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Replaces TACZ's sprint state-machine input with walk while a gun is held in active VR. */
@Mixin(value = TickAnimationEvent.class, remap = false)
abstract class TickAnimationEventMixin {
    @ModifyConstant(
            method = "lambda$tickAnimation$0",
            constant = @Constant(stringValue = "run"),
            require = 1
    )
    private static String taczvr$replaceSprintAnimation(String input) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean suppress = TaczVrClientConfig.DISABLE_TACZ_SPRINT_ANIMATION_IN_VR.get()
                && TaczVrClient.vivecraft().isVrActive()
                && player != null
                && IGun.mainHandHoldGun(player);
        return VrGunAnimationPolicy.replaceSprintInput(input, suppress);
    }
}
