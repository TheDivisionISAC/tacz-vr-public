package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides Vivecraft's world-space pointer while a TACZ gun supplies the player's aiming reference. */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.render.helpers.VREffectsHelper", remap = false)
abstract class VivecraftCrosshairMixin {
    @Inject(method = "renderCrosshairAtDepth", at = @At("HEAD"), cancellable = true, require = 1)
    private static void taczvr$hideCrosshairForGun(boolean depthAlways, PoseStack poseStack, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (TaczVrClientConfig.HIDE_VIVECRAFT_CROSSHAIR_WITH_GUN.get()
                && TaczVrClient.vivecraft().isVrActive()
                && minecraft.player != null
                && IGun.getIGunOrNull(minecraft.player.getMainHandItem()) != null) {
            callback.cancel();
        }
    }
}
