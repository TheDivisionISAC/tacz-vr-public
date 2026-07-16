package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents TACZ scope FOV changes from scaling the whole VR world. */
@Mixin(value = CameraSetupEvent.class, remap = false)
abstract class CameraSetupFovMixin {
    @Inject(method = "applyScopeMagnification", at = @At("HEAD"), cancellable = true, require = 1)
    private static void taczvr$preserveVrWorldFov(ViewportEvent.ComputeFov event, CallbackInfo callback) {
        if (TaczVrClientConfig.DISABLE_TACZ_WORLD_SCOPE_FOV.get()
                && TaczVrClient.vivecraft().isVrActive()) {
            callback.cancel();
        }
    }
}
