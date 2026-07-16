package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.client.vr.VrGunAimStabilization;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preserves Vivecraft's physical controller origin and item scale while removing generic held-item translation that is
 * incompatible with TACZ's righthand_pos anchor.
 */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.render.VivecraftItemRendering", remap = false)
abstract class VivecraftItemRenderingMixin {
    @Unique
    private static final ThreadLocal<Matrix4f> TACZVR_CONTROLLER_POSE = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Matrix3f> TACZVR_CONTROLLER_NORMAL = new ThreadLocal<>();

    @Inject(
            method = "applyFirstPersonItemTransforms",
            at = @At("HEAD"),
            require = 1
    )
    private static void taczvr$captureControllerBasis(PoseStack poseStack, @Coerce Object transformType,
                                                       boolean mainHand, AbstractClientPlayer player,
                                                       float equippedProgress, float partialTick, ItemStack stack,
                                                       InteractionHand hand, CallbackInfo callback) {
        if (taczvr$isMainHandGun(mainHand, hand, stack)) {
            Matrix4f controllerPose = new Matrix4f(poseStack.last().pose());
            Matrix3f controllerNormal = new Matrix3f(poseStack.last().normal());
            VrGunAimStabilization.stabilizeControllerPose(stack, controllerPose, controllerNormal);
            TACZVR_CONTROLLER_POSE.set(controllerPose);
            TACZVR_CONTROLLER_NORMAL.set(controllerNormal);
        } else {
            TACZVR_CONTROLLER_POSE.remove();
            TACZVR_CONTROLLER_NORMAL.remove();
        }
    }

    @Inject(
            method = "applyFirstPersonItemTransforms",
            at = @At("RETURN"),
            require = 1
    )
    private static void taczvr$useHeldItemPointWithControllerRotation(PoseStack poseStack,
                                                                      @Coerce Object transformType,
                                                                      boolean mainHand,
                                                                      AbstractClientPlayer player,
                                                                      float equippedProgress,
                                                                      float partialTick,
                                                                      ItemStack stack,
                                                                      InteractionHand hand,
                                                                      CallbackInfo callback) {
        Matrix4f controllerPose = TACZVR_CONTROLLER_POSE.get();
        Matrix3f controllerNormal = TACZVR_CONTROLLER_NORMAL.get();
        TACZVR_CONTROLLER_POSE.remove();
        TACZVR_CONTROLLER_NORMAL.remove();
        if (controllerPose == null || controllerNormal == null || !taczvr$isMainHandGun(mainHand, hand, stack)) {
            return;
        }

        Matrix4f heldItemPose = poseStack.last().pose();
        Vector3f heldItemScale = heldItemPose.getScale(new Vector3f());
        float scale = heldItemScale.x() * TaczVrClientConfig.VR_GUN_SCALE_MULTIPLIER.get().floatValue();
        if (!Float.isFinite(scale)) {
            scale = 1.0F;
        }
        // Keep Vivecraft's physical controller/wrist origin. The generic item translation sits in front of the
        // hand and is unsuitable for a gun whose animated righthand_pos is the actual grip anchor.
        controllerPose.scale(scale);
        poseStack.last().pose().set(controllerPose);
        poseStack.last().normal().set(controllerNormal);
    }

    @Unique
    private static boolean taczvr$isMainHandGun(boolean mainHand, InteractionHand hand, ItemStack stack) {
        return mainHand
                && hand == InteractionHand.MAIN_HAND
                && TaczVrClientConfig.ENABLE_VR_GUN_RENDERING.get()
                && TaczVrClient.vivecraft().isVrActive()
                && IGun.getIGunOrNull(stack) != null;
    }
}
