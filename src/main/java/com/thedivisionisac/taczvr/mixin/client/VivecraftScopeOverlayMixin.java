package com.thedivisionisac.taczvr.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thedivisionisac.taczvr.client.vr.VrScopeMagnificationRuntime;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Telescope world passes already omit ordinary hands/HUD; also exclude 1.3.13's in-block face-overlay hand path. */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.render.helpers.VREffectsHelper", remap = false)
abstract class VivecraftScopeOverlayMixin {
    @Dynamic("An absent in-block guard cannot satisfy the prototype hook handshake, preventing recursive item rendering")
    @Inject(method = "renderFaceOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void taczvr$omitScopeFaceOverlay(float partialTick, PoseStack pose, CallbackInfo ci) {
        VrScopeMagnificationRuntime.observe(VrScopeMagnificationRuntime.Hook.OVERLAY);
        if (VrScopeMagnificationRuntime.isScopePass()) ci.cancel();
    }
}
