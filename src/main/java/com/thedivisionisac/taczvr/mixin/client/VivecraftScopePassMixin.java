package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.compat.VivecraftScopeBridge;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Optional 1.3.13 pass-list extension; setup-only queries never consume a request or allocate another target. */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.provider.VRRenderer", remap = false)
abstract class VivecraftScopePassMixin {
    @Dynamic("Optional prototype: absent callback leaves the hook handshake incomplete and schedules no scope pass")
    @Inject(method = "getRenderPasses(Z)Ljava/util/List;", at = @At("RETURN"), cancellable = true, require = 0)
    private void taczvr$scheduleScope(boolean includeNonRendered, CallbackInfoReturnable<List<?>> ci) {
        ci.setReturnValue(VivecraftScopeBridge.schedule(ci.getReturnValue(), includeNonRendered));
    }
}
