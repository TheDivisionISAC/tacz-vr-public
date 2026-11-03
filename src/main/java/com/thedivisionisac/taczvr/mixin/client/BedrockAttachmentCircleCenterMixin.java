package com.thedivisionisac.taczvr.mixin.client;

import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.thedivisionisac.taczvr.client.vr.VrScopeTaczCircleRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Replaces only TACZ 1.1.8's two desktop ocular-circle center locals. The target bytecode slots are version-locked
 * to {@code renderOcularAndDivision}: {@code centerX} is local 12 and {@code centerY} is local 13 after
 * {@code getBedrockPartCenter}. Outside the bounded physical-opening context both modifiers return the original
 * values, preserving desktop/non-VR behavior.
 */
@Mixin(value = BedrockAttachmentModel.class, remap = false)
abstract class BedrockAttachmentCircleCenterMixin {
    @ModifyVariable(
            method = "renderOcularAndDivision",
            at = @At("STORE"),
            index = 12,
            require = 1
    )
    private float taczvr$projectOcularCircleCenterX(float originalCenterX) {
        return VrScopeTaczCircleRenderContext.resolveCenterX(originalCenterX);
    }

    @ModifyVariable(
            method = "renderOcularAndDivision",
            at = @At("STORE"),
            index = 13,
            require = 1
    )
    private float taczvr$projectOcularCircleCenterY(float originalCenterY) {
        return VrScopeTaczCircleRenderContext.resolveCenterY(originalCenterY);
    }
}
