package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.render.VrGunHandPoseCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.functional.RightHandRender;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures TACZ's animated main-hand anchor for SHOOT and authored actions without steering the gun or projectile. */
@Mixin(value = RightHandRender.class, remap = false)
abstract class RightHandRenderMixin {
    @Shadow
    @Final
    private BedrockAnimatedModel bedrockGunModel;

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void taczvr$captureMainHandAnchor(PoseStack poseStack, VertexConsumer vertexBuffer,
                                              ItemDisplayContext transformType, int light, int overlay,
                                              CallbackInfo callback) {
        if (transformType.firstPerson() && this.bedrockGunModel instanceof BedrockGunModel gunModel) {
            VrGunHandPoseCache.captureRightAnchor(gunModel, poseStack);
        }
    }
}
