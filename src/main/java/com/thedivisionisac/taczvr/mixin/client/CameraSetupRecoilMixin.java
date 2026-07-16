package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels TACZ camera recoil in VR while leaving gun animation and gameplay recoil intact. */
@Mixin(value = CameraSetupEvent.class, remap = false)
abstract class CameraSetupRecoilMixin {
    @Inject(method = "initialCameraRecoil", at = @At("HEAD"), cancellable = true, require = 1)
    private static void taczvr$skipVrCameraRecoilSetup(GunFireEvent event, CallbackInfo callback) {
        taczvr$cancelInVr(callback);
    }

    @Inject(method = "applyCameraRecoil", at = @At("HEAD"), cancellable = true, require = 1)
    private static void taczvr$skipVrCameraRecoil(ViewportEvent.ComputeCameraAngles event, CallbackInfo callback) {
        taczvr$cancelInVr(callback);
    }

    private static void taczvr$cancelInVr(CallbackInfo callback) {
        if (TaczVrClientConfig.DISABLE_TACZ_CAMERA_RECOIL.get()
                && TaczVrClient.vivecraft().isVrActive()) {
            callback.cancel();
        }
    }
}
