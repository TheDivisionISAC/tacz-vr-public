package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps TACZ level-camera animation from rotating the tracked HMD view. */
@Mixin(value = CameraSetupEvent.class, remap = false)
abstract class CameraSetupAnimationMixin {
    @Inject(method = "applyLevelCameraAnimation", at = @At("HEAD"), cancellable = true, require = 1)
    private static void taczvr$keepCameraAnimationOnGun(ViewportEvent.ComputeCameraAngles event,
                                                        CallbackInfo callback) {
        if (TaczVrClientConfig.DISABLE_TACZ_LEVEL_CAMERA_ANIMATION.get()
                && TaczVrClient.vivecraft().isVrActive()) {
            callback.cancel();
        }
    }
}
