package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.compat.OculusScopeBridge;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Oculus 1.8.0 optional lifecycle proof. Begin and final color-conversion callbacks must both be observed before
 * shader magnification is allowed. Missing soft targets leave the adapter disabled; this never cancels Iris work.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
abstract class OculusScopePipelineMixin {
    @Dynamic("Oculus 1.8.0 optional pipeline lifecycle; adapter handshake requires both callbacks")
    @Inject(method = "beginLevelRendering", at = @At("HEAD"), require = 0)
    private void taczvr$beginShaderScope(CallbackInfo ci) { OculusScopeBridge.beginPipeline(this); }

    @Dynamic("Oculus 1.8.0 final color-space conversion; no successful publication without this callback")
    @Inject(method = "finalizeGameRendering", at = @At("RETURN"), require = 0)
    private void taczvr$finishShaderScope(CallbackInfo ci) { OculusScopeBridge.finishPipeline(this); }
}
