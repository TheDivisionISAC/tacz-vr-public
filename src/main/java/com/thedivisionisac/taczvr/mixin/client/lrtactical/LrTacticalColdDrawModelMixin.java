package com.thedivisionisac.taczvr.mixin.client.lrtactical;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.thedivisionisac.taczvr.client.compat.lrtactical.LrTacticalVrHandPoseCache;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps an LRTactical model hidden while its 1.20.1 state machine is waiting to expose the initial draw track.
 * The owning renderer still updates and cleans the animation state every frame.
 */
@Mixin(value = BedrockModel.class, remap = false)
abstract class LrTacticalColdDrawModelMixin {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void taczvr$hideColdDrawSetupFrame(PoseStack poseStack, ItemDisplayContext context,
                                                RenderType renderType, int light, int overlay,
                                                CallbackInfo callback) {
        if ((Object) this instanceof BedrockAnimatedModel model
                && LrTacticalVrHandPoseCache.shouldSuppressModel(model)) {
            callback.cancel();
        }
    }
}
