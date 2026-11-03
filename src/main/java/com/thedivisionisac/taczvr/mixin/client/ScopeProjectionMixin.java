package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.vr.VrScopeMagnificationRuntime;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** After Vivecraft's fixed telescope projection, replace angular scale only inside our owned pass; eye FOV is intact. */
@Mixin(value = GameRenderer.class, priority = 900)
abstract class ScopeProjectionMixin {
    @Dynamic("Optional C3 projection hook: a missing target leaves the runtime handshake incomplete")
    @Inject(method = "getProjectionMatrix", at = @At("RETURN"), cancellable = true, require = 0)
    private void taczvr$scopeProjection(double fov, CallbackInfoReturnable<Matrix4f> ci) {
        ci.setReturnValue(VrScopeMagnificationRuntime.projection(ci.getReturnValue()));
    }
}
