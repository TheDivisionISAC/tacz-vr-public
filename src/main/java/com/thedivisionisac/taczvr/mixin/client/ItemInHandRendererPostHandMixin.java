package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.client.render.VrGunHandVisibility;
import com.thedivisionisac.taczvr.client.render.VrGunHandPoseCache;
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
import org.vivecraft.client_vr.ClientDataHolderVR;

/**
 * Owns Vivecraft hand presentation around TACZ guns: fallback model-hand suppression, support-hand grip snapping,
 * and post-gun Vivecraft hands that can follow TACZ's functional anchors during authored animations.
 */
@Mixin(value = ItemInHandRenderer.class, priority = 900)
abstract class ItemInHandRendererPostHandMixin {
    @Unique
    private boolean taczvr$restoreShowPlayerHands;
    @Unique
    private boolean taczvr$previousShowPlayerHands;

    @Shadow
    protected abstract void renderPlayerArm(PoseStack poseStack, MultiBufferSource buffer, int combinedLight,
                                            float equippedProgress, float swingProgress, HumanoidArm side);

    @Dynamic("Vivecraft merges this method into ItemInHandRenderer")
    @Inject(method = "vivecraft$vrRenderArmWithItem", at = @At("HEAD"), remap = false, require = 0)
    private void taczvr$suppressTrackedHandsForAnimation(AbstractClientPlayer player, float partialTick,
                                                          InteractionHand hand, float swingProgress, ItemStack stack,
                                                          PoseStack poseStack, MultiBufferSource buffer,
                                                          int combinedLight, CallbackInfo callback) {
        if (!(player instanceof net.minecraft.client.player.LocalPlayer localPlayer)) {
            return;
        }
        ItemStack gunStack = localPlayer.getMainHandItem();
        boolean hideTrackedHands = VrGunHandVisibility.shouldHideVivecraftHands(localPlayer, gunStack);
        boolean replaceMainHandWithPostRender = hand == InteractionHand.MAIN_HAND
                && VrGunHandVisibility.allowsVivecraftHands()
                && TaczVrClientConfig.POST_RENDER_VIVECRAFT_GUN_HAND.get()
                && TaczVrClient.vivecraft().isVrActive()
                && IGun.getIGunOrNull(stack) != null
                && !hideTrackedHands;
        if (!hideTrackedHands && !replaceMainHandWithPostRender) {
            return;
        }
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();
        this.taczvr$previousShowPlayerHands = dataHolder.vrSettings.showPlayerHands;
        this.taczvr$restoreShowPlayerHands = true;
        dataHolder.vrSettings.showPlayerHands = false;
    }

    @Dynamic("Vivecraft merges this method into ItemInHandRenderer")
    @Inject(method = "vivecraft$vrRenderArmWithItem", at = @At("HEAD"), remap = false, require = 0)
    private void taczvr$snapSupportHandToGun(AbstractClientPlayer player, float partialTick, InteractionHand hand,
                                             float swingProgress, ItemStack stack, PoseStack poseStack,
                                             MultiBufferSource buffer, int combinedLight, CallbackInfo callback) {
        if (hand != InteractionHand.OFF_HAND
                || !(player instanceof net.minecraft.client.player.LocalPlayer localPlayer)) {
            return;
        }
        if (!VrGunHandVisibility.allowsVivecraftHands()
                || !VrGunHandVisibility.isSupportHandSnapEligible(stack)
                || VrGunHandVisibility.shouldHideVivecraftHands(localPlayer, localPlayer.getMainHandItem())) {
            VrGunHandPoseCache.releaseSupportHandState();
            return;
        }
        VrGunHandPoseCache.applySupportHandSnap(poseStack, localPlayer.getMainHandItem());
    }

    @Dynamic("Vivecraft merges this method into ItemInHandRenderer")
    @Inject(method = "vivecraft$vrRenderArmWithItem", at = @At("RETURN"), remap = false, require = 0)
    private void taczvr$restoreTrackedHandsSetting(AbstractClientPlayer player, float partialTick,
                                                    InteractionHand hand, float swingProgress, ItemStack stack,
                                                    PoseStack poseStack, MultiBufferSource buffer,
                                                    int combinedLight, CallbackInfo callback) {
        if (this.taczvr$restoreShowPlayerHands) {
            ClientDataHolderVR.getInstance().vrSettings.showPlayerHands = this.taczvr$previousShowPlayerHands;
            this.taczvr$restoreShowPlayerHands = false;
        }
    }

    @Dynamic("Vivecraft merges this method into ItemInHandRenderer")
    @Inject(method = "vivecraft$vrRenderArmWithItem", at = @At("RETURN"), remap = false, require = 0)
    private void taczvr$postRenderGunHand(AbstractClientPlayer player, float partialTick, InteractionHand hand,
                                          float swingProgress, ItemStack stack, PoseStack poseStack,
                                          MultiBufferSource buffer, int combinedLight, CallbackInfo callback) {
        if (hand != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!VrGunHandVisibility.allowsVivecraftHands()
                || !TaczVrClientConfig.POST_RENDER_VIVECRAFT_GUN_HAND.get()
                || !TaczVrClient.vivecraft().isVrActive()
                || (player instanceof net.minecraft.client.player.LocalPlayer localPlayer
                    && VrGunHandVisibility.shouldHideVivecraftHands(localPlayer, stack))
                || IGun.getIGunOrNull(stack) == null) {
            VrGunAimStabilization.discardMainHandRenderCorrection();
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
            VrGunAimStabilization.applyMainHandRenderCorrection(poseStack);
            VrGunHandPoseCache.applyShootHandAnimation(poseStack, stack);
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
}
