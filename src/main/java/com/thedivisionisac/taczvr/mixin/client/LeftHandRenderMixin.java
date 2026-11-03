package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.render.VrGunHandPoseCache;
import com.thedivisionisac.taczvr.client.compat.lrtactical.LrTacticalVrHandPoseCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.functional.LeftHandRender;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures TACZ's fully animated support-hand anchor before the optional model arm is rendered. */
@Mixin(value = LeftHandRender.class, remap = false)
abstract class LeftHandRenderMixin {
    @Shadow
    @Final
    private BedrockAnimatedModel bedrockGunModel;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 1)
    private void taczvr$captureSupportHandAnchor(PoseStack poseStack, VertexConsumer vertexBuffer,
                                                 ItemDisplayContext transformType, int light, int overlay,
                                                 CallbackInfo callback) {
        if (!transformType.firstPerson()) {
            return;
        }
        if (this.bedrockGunModel instanceof BedrockGunModel gunModel) {
            VrGunHandPoseCache.captureLeftAnchor(gunModel, poseStack);
        } else if (LrTacticalVrHandPoseCache.captureLeftAnchor(this.bedrockGunModel, poseStack)) {
            callback.cancel();
        }
    }
}
