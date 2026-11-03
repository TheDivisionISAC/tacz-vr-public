package com.thedivisionisac.taczvr.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.functional.AttachmentRender;
import com.thedivisionisac.taczvr.client.vr.VrScopeAlignmentCapture;
import com.thedivisionisac.taczvr.client.vr.VrScopeLateDivisionRenderer;
import com.thedivisionisac.taczvr.client.vr.VrScopeOcularRenderContext;
import com.thedivisionisac.taczvr.client.vr.VrScopeTaczCircleRenderContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wraps TACZ 1.1.8-hotfix's attachment-model call after the exact scope model has been resolved. During a first-person scope
 * render it temporarily multiplies the author's radius modifier by the current eye's physical alignment progress and
 * lends physical ownership of the ocular opening to the TACZ aiming-progress getter. TACZ and an ARCANA handler
 * injected inside {@code BedrockAttachmentModel.render} therefore observe the same field and render-scoped ADS
 * factor. The original value is always restored; non-scope, non-VR, disabled, and invalid-data paths retain or close
 * safely. The {@code require=1} target is the pinned TACZ lambda signature; the operation is called exactly once on
 * every path so a cooperating MixinExtras wrapper can remain in the chain.
 */
@Mixin(value = AttachmentRender.class, remap = false)
abstract class AttachmentRenderMixin {
    @WrapOperation(
            method = "lambda$renderAttachment$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/client/model/BedrockAttachmentModel;render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V"
            ),
            require = 1
    )
    private static void taczvr$gatePhysicalScopeRadius(
            BedrockAttachmentModel model,
            ItemStack attachmentItem,
            ItemStack gunItem,
            PoseStack poseStack,
            ItemDisplayContext transformType,
            RenderType renderType,
            int light,
            int overlay,
            Operation<Void> original
    ) {
        BedrockAttachmentModelAccessor accessor = (BedrockAttachmentModelAccessor) model;
        if (!transformType.firstPerson() || !accessor.taczvr$isScope()) {
            original.call(model, attachmentItem, gunItem, poseStack, transformType, renderType, light, overlay);
            return;
        }

        float physicalProgress = VrScopeAlignmentCapture.scopeRadiusScale(model, gunItem);
        if (!VrScopeAlignmentCapture.ownsScopeOcularOpening()) {
            original.call(model, attachmentItem, gunItem, poseStack, transformType, renderType, light, overlay);
            return;
        }

        float originalModifier = accessor.taczvr$getScopeViewRadiusModifier();
        if (!Float.isFinite(originalModifier) || originalModifier <= 0.0F || !Float.isFinite(physicalProgress)) {
            // Invalid author data must not poison TACZ's model field or the late replay with NaN/negative radii.
            original.call(model, attachmentItem, gunItem, poseStack, transformType, renderType, light, overlay);
            return;
        }
        float appliedModifier = originalModifier * physicalProgress;
        // Zero is a valid closed aperture; only non-finite or negative results require the unmodified fallback.
        if (!Float.isFinite(appliedModifier) || appliedModifier < 0.0F) {
            original.call(model, attachmentItem, gunItem, poseStack, transformType, renderType, light, overlay);
            return;
        }
        VrScopeAlignmentCapture.VrScopeCircleCenter circleCenter =
                VrScopeAlignmentCapture.scopeCircleCenter(model, gunItem).orElse(null);
        accessor.taczvr$setScopeViewRadiusModifier(appliedModifier);
        try {
            VrScopeAlignmentCapture.recordAppliedScopeRadius(
                    model,
                    gunItem,
                    physicalProgress,
                    originalModifier,
                    appliedModifier
            );
            VrScopeLateDivisionRenderer.capture(
                    model,
                    poseStack,
                    transformType,
                    renderType,
                    light,
                    overlay,
                    appliedModifier,
                    accessor.taczvr$isSight(),
                    circleCenter
            );
            try (VrScopeOcularRenderContext.Scope ignored =
                         VrScopeOcularRenderContext.enterPhysicalOpening();
                 VrScopeTaczCircleRenderContext.Scope ignoredCircle =
                         VrScopeTaczCircleRenderContext.enter(circleCenter)) {
                original.call(model, attachmentItem, gunItem, poseStack, transformType, renderType, light, overlay);
            }
        } finally {
            accessor.taczvr$setScopeViewRadiusModifier(originalModifier);
        }
    }
}
