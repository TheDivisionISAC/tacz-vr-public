package com.thedivisionisac.taczvr.mixin.client;

import com.tacz.guns.client.gameplay.LocalPlayerAim;
import com.thedivisionisac.taczvr.client.vr.VrScopeOcularRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps TACZ's real ADS state everywhere except the physically owned optical-mask render. Hooking the concrete TACZ
 * getter also covers compatible renderers that copy the ocular formula but still read the public gun-operator API.
 */
@Mixin(value = LocalPlayerAim.class, remap = false)
abstract class LocalPlayerAimMixin {
    @Inject(method = "getClientAimingProgress", at = @At("RETURN"), cancellable = true, require = 1)
    private void taczvr$decouplePhysicalOcularFromAds(
            float partialTicks,
            CallbackInfoReturnable<Float> callback
    ) {
        callback.setReturnValue(
                VrScopeOcularRenderContext.resolveTaczAimingProgress(callback.getReturnValue())
        );
    }
}
