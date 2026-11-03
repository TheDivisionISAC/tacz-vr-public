package com.thedivisionisac.taczvr.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.client.compat.VivecraftHandVisibility;
import com.thedivisionisac.taczvr.client.compat.lrtactical.LrTacticalVrControllerPoseCache;
import com.thedivisionisac.taczvr.client.compat.lrtactical.LrTacticalVrHandPoseCache;
import com.thedivisionisac.taczvr.client.compat.lrtactical.LrTacticalVrItemSupport;
import com.thedivisionisac.taczvr.client.render.VrGunHandVisibility;
import com.thedivisionisac.taczvr.client.render.VrGunHandPoseCache;
import com.thedivisionisac.taczvr.client.render.VrHandRenderPlan;
import com.thedivisionisac.taczvr.client.vr.VrGunAimStabilization;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Owns Vivecraft hand presentation around TACZ guns: fallback model-hand suppression, support-hand grip snapping,
 * and post-gun Vivecraft hands that can follow TACZ's functional anchors during authored animations.
 * Vivecraft 1.3.15 has one native arm call, including THIRD/MR passes. Per-call shared state replaces that call
 * without changing VR settings or leaking ownership across nested/failed renders. The optional merged target
 * stays soft: if the wrapper does not match, post drawing is disabled and the native hand remains.
 * INVOKE injection into a merged method requires priority above its owner (Vivecraft 1.3.15: 999).
 * Mixin completes method merging before preparing injections, so priority 1000 can target that method.
 */
@Mixin(value = ItemInHandRenderer.class, priority = 1000)
abstract class ItemInHandRendererPostHandMixin {
    @Shadow
    protected abstract void renderPlayerArm(PoseStack poseStack, MultiBufferSource buffer, int combinedLight,
                                            float equippedProgress, float swingProgress, HumanoidArm side);

    @Dynamic("Vivecraft merges this method into ItemInHandRenderer")
    @Inject(method = "vivecraft$vrRenderArmWithItem", at = @At("HEAD"), remap = false, require = 0)
    private void taczvr$suppressTrackedHandsForAnimation(AbstractClientPlayer player, float partialTick,
                                                          InteractionHand hand, float swingProgress, ItemStack stack,
                                                          PoseStack poseStack, MultiBufferSource buffer,
                                                          int combinedLight, CallbackInfo callback,
                                                          @Share("handPlan") LocalRef<VrHandRenderPlan> plan) {
        plan.set(VrHandRenderPlan.KEEP);
        boolean upstreamVisible = VivecraftHandVisibility.allows(player, hand, stack);
        if (!(player instanceof net.minecraft.client.player.LocalPlayer localPlayer)) {
            return;
        }
        ItemStack gunStack = localPlayer.getMainHandItem();
        LrTacticalVrControllerPoseCache.capturePhysicalHands(hand, poseStack, gunStack);
        boolean hideTrackedHands = VrGunHandVisibility.shouldHideVivecraftHands(localPlayer, gunStack);
        boolean hideLrTacticalHands = LrTacticalVrHandPoseCache.shouldHideTrackedHands(gunStack);
        boolean replaceMainHandWithPostRender = hand == InteractionHand.MAIN_HAND
                && VrGunHandVisibility.allowsVivecraftHands()
                && TaczVrClientConfig.POST_RENDER_VIVECRAFT_GUN_HAND.get()
                && TaczVrClient.vivecraft().isVrActive()
                && IGun.getIGunOrNull(stack) != null
                && !hideTrackedHands;
        boolean replaceLrTacticalMainHand = taczvr$isPhysicalMainHand(hand)
                && LrTacticalVrHandPoseCache.shouldPostRenderMainHand(gunStack)
                && !hideLrTacticalHands;
        boolean replaceLrTacticalSupportHand = taczvr$isPhysicalSupportHand(hand)
                && LrTacticalVrHandPoseCache.shouldPostRenderMainHand(gunStack)
                && !hideLrTacticalHands;
        plan.set(VrHandRenderPlan.select(upstreamVisible, hideTrackedHands || hideLrTacticalHands,
                replaceMainHandWithPostRender || replaceLrTacticalMainHand || replaceLrTacticalSupportHand));
    }

    @Dynamic("Vivecraft 1.3.15 merges this method and its single native arm draw into ItemInHandRenderer")
    @WrapOperation(
            method = "vivecraft$vrRenderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;"
                            + "renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                            + "IFFLnet/minecraft/world/entity/HumanoidArm;)V",
                    remap = true
            ),
            remap = false, require = 0, allow = 1
    )
    private void taczvr$takeOverTrackedHand(ItemInHandRenderer renderer, PoseStack pose, MultiBufferSource buffer,
                                           int light, float equipped, float swing, HumanoidArm arm,
                                           Operation<Void> original,
                                           @Share("handPlan") LocalRef<VrHandRenderPlan> plan,
                                           @Share("originalHandSuppressed") LocalBooleanRef suppressed) {
        VrHandRenderPlan current = plan.get();
        if (current != null && current.suppressesOriginal()) {
            suppressed.set(true);
            return;
        }
        original.call(renderer, pose, buffer, light, equipped, swing, arm);
    }

    @Dynamic("Vivecraft merges this method into ItemInHandRenderer")
    @Inject(method = "vivecraft$vrRenderArmWithItem", at = @At("HEAD"), remap = false, require = 0)
    private void taczvr$snapSupportHandToGun(AbstractClientPlayer player, float partialTick, InteractionHand hand,
                                             float swingProgress, ItemStack stack, PoseStack poseStack,
                                             MultiBufferSource buffer, int combinedLight, CallbackInfo callback) {
        if (!(player instanceof net.minecraft.client.player.LocalPlayer localPlayer)) {
            return;
        }
        ItemStack mainHandStack = localPlayer.getMainHandItem();
        if (LrTacticalVrItemSupport.isRenderSupported(mainHandStack)) {
            if (taczvr$isPhysicalSupportHand(hand)
                    && (!VrGunHandVisibility.allowsVivecraftHands()
                        || LrTacticalVrHandPoseCache.shouldHideTrackedHands(mainHandStack))) {
                LrTacticalVrHandPoseCache.releaseSupportHandState();
            }
            return;
        }
        if (hand != InteractionHand.OFF_HAND) {
            return;
        }
        boolean handsAllowed = VrGunHandVisibility.allowsVivecraftHands();
        boolean regularSupportEligible = VrGunHandVisibility.isSupportHandSnapEligible(stack);
        boolean forcedSupportAnimation = VrGunHandVisibility.isForcedSupportHandSnapActive(
                localPlayer,
                localPlayer.getMainHandItem()
        );
        boolean supportEligible = regularSupportEligible || forcedSupportAnimation;
        boolean handsHidden = VrGunHandVisibility.shouldHideVivecraftHands(
                localPlayer,
                localPlayer.getMainHandItem()
        );
        if (!handsAllowed || !supportEligible || handsHidden) {
            VrGunHandPoseCache.releaseSupportHandState();
            return;
        }
        VrGunHandPoseCache.applySupportHandSnap(poseStack, localPlayer.getMainHandItem());
    }

    @Dynamic("Vivecraft merges this method into ItemInHandRenderer")
    @Inject(method = "vivecraft$vrRenderArmWithItem", at = @At("RETURN"), remap = false, require = 0)
    private void taczvr$postRenderGunHand(AbstractClientPlayer player, float partialTick, InteractionHand hand,
                                          float swingProgress, ItemStack stack, PoseStack poseStack,
                                          MultiBufferSource buffer, int combinedLight, CallbackInfo callback,
                                          @Share("handPlan") LocalRef<VrHandRenderPlan> plan,
                                          @Share("originalHandSuppressed") LocalBooleanRef suppressed) {
        ItemStack lrTacticalStack = player instanceof net.minecraft.client.player.LocalPlayer localPlayer
                ? localPlayer.getMainHandItem()
                : ItemStack.EMPTY;
        boolean isLrTacticalItem = taczvr$isPhysicalMainHand(hand)
                && LrTacticalVrHandPoseCache.shouldPostRenderMainHand(lrTacticalStack);
        boolean isTaczGun = hand == InteractionHand.MAIN_HAND && IGun.getIGunOrNull(stack) != null;
        ItemStack renderedItem = isLrTacticalItem ? lrTacticalStack : stack;
        if (plan.get() == null || !plan.get().allowsPostRender(suppressed.get())
                || !VrGunHandVisibility.allowsVivecraftHands()
                || !TaczVrClientConfig.POST_RENDER_VIVECRAFT_GUN_HAND.get()
                || !TaczVrClient.vivecraft().isVrActive()
                || (player instanceof net.minecraft.client.player.LocalPlayer localPlayer
                    && VrGunHandVisibility.shouldHideVivecraftHands(localPlayer, renderedItem))
                || (!isTaczGun && !isLrTacticalItem)) {
            if (hand == InteractionHand.MAIN_HAND) {
                VrGunAimStabilization.discardMainHandRenderCorrection();
            }
            return;
        }

        HumanoidArm arm = player.getMainArm();
        if (TaczVrClient.vivecraft().isLeftHanded()) {
            arm = arm.getOpposite();
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        poseStack.pushPose();
        try {
            if (isTaczGun) {
                VrGunAimStabilization.applyMainHandRenderCorrection(poseStack);
                VrGunHandPoseCache.applyShootHandAnimation(poseStack, stack);
            } else {
                VrGunAimStabilization.discardMainHandRenderCorrection();
                LrTacticalVrHandPoseCache.applyMainHandAnimation(poseStack, lrTacticalStack);
            }
            this.renderPlayerArm(poseStack, buffer, combinedLight, 0.0F, swingProgress, arm);
            if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
                bufferSource.endBatch();
            }
        } finally {
            poseStack.popPose();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    @Dynamic("Vivecraft merges this method into ItemInHandRenderer")
    @Inject(method = "vivecraft$vrRenderArmWithItem", at = @At("RETURN"), remap = false, require = 0)
    private void taczvr$postRenderLrTacticalSupportHand(AbstractClientPlayer player, float partialTick,
                                                        InteractionHand hand, float swingProgress, ItemStack stack,
                                                        PoseStack poseStack, MultiBufferSource buffer,
                                                        int combinedLight, CallbackInfo callback,
                                                        @Share("handPlan") LocalRef<VrHandRenderPlan> plan,
                                                        @Share("originalHandSuppressed") LocalBooleanRef suppressed) {
        if (plan.get() == null || !plan.get().allowsPostRender(suppressed.get())
                || !(player instanceof net.minecraft.client.player.LocalPlayer localPlayer)
                || !taczvr$isPhysicalSupportHand(hand)
                || !LrTacticalVrHandPoseCache.shouldPostRenderMainHand(localPlayer.getMainHandItem())) {
            return;
        }

        HumanoidArm physicalMainArm = player.getMainArm();
        if (TaczVrClient.vivecraft().isLeftHanded()) {
            physicalMainArm = physicalMainArm.getOpposite();
        }
        HumanoidArm supportArm = physicalMainArm.getOpposite();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        poseStack.pushPose();
        try {
            if (VrGunHandVisibility.allowsVivecraftHands()
                    && !LrTacticalVrHandPoseCache.shouldHideTrackedHands(localPlayer.getMainHandItem())) {
                LrTacticalVrHandPoseCache.applySupportHandAnimation(poseStack, localPlayer.getMainHandItem());
            } else {
                LrTacticalVrHandPoseCache.releaseSupportHandState();
            }
            this.renderPlayerArm(poseStack, buffer, combinedLight, 0.0F, swingProgress, supportArm);
            if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
                bufferSource.endBatch();
            }
        } finally {
            poseStack.popPose();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    @Unique
    private static boolean taczvr$isPhysicalMainHand(InteractionHand hand) {
        InteractionHand physicalMainHand = TaczVrClient.vivecraft().isLeftHanded()
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        return hand == physicalMainHand;
    }

    @Unique
    private static boolean taczvr$isPhysicalSupportHand(InteractionHand hand) {
        return !taczvr$isPhysicalMainHand(hand);
    }
}
