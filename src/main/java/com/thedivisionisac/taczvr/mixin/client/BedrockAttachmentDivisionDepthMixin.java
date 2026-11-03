package com.thedivisionisac.taczvr.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.thedivisionisac.taczvr.client.vr.VrScopeDivisionRenderType;
import com.thedivisionisac.taczvr.client.vr.VrScopeOcularRenderContext;
import com.thedivisionisac.taczvr.client.vr.VrScopeMagnificationRuntime;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Gives only a physically opened VR scope's division geometry overlay depth while preserving TACZ's ocular stencil.
 * The scope body, ocular mask, and all desktop rendering retain TACZ's original depth behavior. OpenGL state is
 * restored around every division draw so ordinary and Accelerated Rendering paths share the same bounded change. The
 * three static {@code renderTempPart} calls in TACZ 1.1.8-hotfix's division routine are deliberately pinned with
 * {@code require=3}; a target-layout change must disable this optional slice rather than alter an unrelated draw.
 * Before the selected division, delegates optional magnification to a current-stencil consumer; the same narrow
 * operation participates in late replay, retaining the authored reticle as the final layer.
 */
@Mixin(value = BedrockAttachmentModel.class, remap = false)
abstract class BedrockAttachmentDivisionDepthMixin {
    @Shadow
    private List<List<BedrockPart>> divisionNodePaths;

    @WrapOperation(
            method = "renderOcularAndDivision",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/client/model/BedrockAttachmentModel;renderTempPart(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IILjava/util/List;)V"
            ),
            require = 3
    )
    private void taczvr$renderPhysicalDivisionAboveWorldDepth(
            BedrockAttachmentModel model,
            PoseStack poseStack,
            ItemDisplayContext transformType,
            RenderType renderType,
            int light,
            int overlay,
            List<BedrockPart> path,
            Operation<Void> original
    ) {
        VrScopeMagnificationRuntime.observe(VrScopeMagnificationRuntime.Hook.COMPOSITE);
        int divisionIndex = this.taczvr$divisionIndex(path);
        if (!VrScopeOcularRenderContext.isPhysicalOpeningActive() || divisionIndex < 0) {
            original.call(model, poseStack, transformType, renderType, light, overlay, path);
            return;
        }

        // TACZ has already selected the ocular stencil reference here. The consumer validates the inverted
        // reference and current view before drawing; the authored division remains the final layer.
        VrScopeMagnificationRuntime.composite(model, divisionIndex);
        original.call(
                model,
                poseStack,
                transformType,
                VrScopeDivisionRenderType.overlay(renderType),
                light,
                overlay,
                path
        );
    }

    private int taczvr$divisionIndex(List<BedrockPart> path) {
        if (path == null || this.divisionNodePaths == null) {
            return -1;
        }
        for (int index = 0; index < this.divisionNodePaths.size(); index++) {
            if (this.divisionNodePaths.get(index) == path) {
                return index;
            }
        }
        return -1;
    }
}
