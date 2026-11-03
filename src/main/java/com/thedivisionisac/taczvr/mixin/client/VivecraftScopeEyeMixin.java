package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.compat.VivecraftScopeBridge;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Returns a call-scoped synthetic camera pose for our SCOPER only; every stored tracking pose stays unchanged. */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.VRData", remap = false)
abstract class VivecraftScopeEyeMixin {
    @Dynamic("Vivecraft 1.3.13 optional eye adapter; missing hook cannot satisfy the magnification handshake")
    @Inject(method = "getEye", at = @At("RETURN"), cancellable = true, require = 0)
    private void taczvr$scopeCamera(@Coerce Object pass, CallbackInfoReturnable<Object> ci) {
        ci.setReturnValue(VivecraftScopeBridge.eyePose(this, pass, ci.getReturnValue()));
    }
}
