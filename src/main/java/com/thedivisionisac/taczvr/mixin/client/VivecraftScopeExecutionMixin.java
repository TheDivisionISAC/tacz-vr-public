package com.thedivisionisac.taczvr.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thedivisionisac.taczvr.client.compat.VivecraftScopeBridge;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Borrows the native 1.3.13 pass execution boundary to publish a complete texture and preserve first-eye semantics. */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.render.helpers.VRPassHelper", remap = false)
abstract class VivecraftScopeExecutionMixin {
    @Dynamic("Optional prototype lifecycle hook; no observed normal-eye callback means no scope pass may be added")
    @WrapOperation(method = "renderAndSubmit", at = @At(value = "INVOKE",
            target = "Lorg/vivecraft/client_vr/render/helpers/VRPassHelper;renderSingleView(Lorg/vivecraft/api/client/data/RenderPass;FJZ)V"), require = 0)
    private static void taczvr$scopeTransaction(@Coerce Object pass, float partialTick, long nanoTime,
                                               boolean renderLevel, Operation<Void> original) {
        VivecraftScopeBridge.renderView(pass, renderLevel, () -> original.call(pass, partialTick, nanoTime, renderLevel));
    }
}
