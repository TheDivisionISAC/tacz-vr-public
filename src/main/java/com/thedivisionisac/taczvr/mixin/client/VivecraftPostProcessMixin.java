package com.thedivisionisac.taczvr.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thedivisionisac.taczvr.client.vr.VrScopeLateDivisionRenderer;
import com.thedivisionisac.taczvr.client.vr.VrScopeRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replays the stencil-clipped scope division after Vivecraft 1.20.1-1.3.13's per-eye water and other post effects.
 * The target is optional and therefore remains a string {@code @Pseudo} mixin gated by the plugin; {@code require=1}
 * is intentional so a signature change fails the compatibility slice at load time instead of silently leaving a
 * half-applied late replay. The injected method only classifies the pass and delegates all state ownership to the
 * render-thread bridge.
 */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.render.helpers.ShaderHelper", remap = false)
abstract class VivecraftPostProcessMixin {
    // The complete world image must inherit global eye effects; the TAIL callback now redraws only the reticle.
    @Inject(method = "doVrPostProcess", at = @At("HEAD"), require = 1)
    private static void taczvr$composeScopeBeforeEyeEffects(@Coerce Object renderPass, RenderTarget source,
                                                           float partialTick, CallbackInfo ci) {
        VrScopeLateDivisionRenderer.renderBeforePostProcess(VrScopeRenderPass.fromObject(renderPass), source);
    }

    @Inject(method = "doVrPostProcess", at = @At("TAIL"), require = 1)
    private static void taczvr$renderScopeDivisionAfterPostProcess(
            @Coerce Object renderPass,
            RenderTarget source,
            float partialTick,
            CallbackInfo ci
    ) {
        VrScopeLateDivisionRenderer.renderAfterPostProcess(VrScopeRenderPass.fromObject(renderPass));
    }
}
