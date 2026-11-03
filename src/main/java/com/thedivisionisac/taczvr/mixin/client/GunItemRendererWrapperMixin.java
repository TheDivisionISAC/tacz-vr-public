package com.thedivisionisac.taczvr.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.client.render.VrGunHandVisibility;
import com.thedivisionisac.taczvr.client.render.VrGunHandPoseCache;
import com.thedivisionisac.taczvr.client.render.VrGunPoseTransform;
import com.thedivisionisac.taczvr.client.vr.VrScopeAlignmentCapture;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Central TACZ first-person integration: captures the controller basis, re-bases the gun, hides TACZ model hands, and
 * removes HMD view rotation from TACZ's camera-relative gun transform. The model-render hook is a chainable
 * MixinExtras operation so cooperative render-type wrappers, such as ARCANA's, can preserve these VR transforms.
 */
@Mixin(value = GunItemRendererWrapper.class, remap = false)
abstract class GunItemRendererWrapperMixin {
    @Unique
    private Matrix4f taczvr$controllerPose;
    @Unique
    private Matrix3f taczvr$controllerNormal;

    @Shadow
    public abstract void renderFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext context,
                                           PoseStack poseStack, MultiBufferSource buffer, int light,
                                           float partialTick);

    @Inject(method = "renderFirstPerson", at = @At("HEAD"), require = 1)
    private void taczvr$captureControllerPose(LocalPlayer player, ItemStack stack, ItemDisplayContext context,
                                              PoseStack poseStack, MultiBufferSource buffer, int light,
                                              float partialTick, CallbackInfo callback) {
        if (TaczVrClientConfig.ENABLE_VR_GUN_RENDERING.get() && TaczVrClient.vivecraft().isVrActive()) {
            this.taczvr$controllerPose = new Matrix4f(poseStack.last().pose());
            this.taczvr$controllerNormal = new Matrix3f(poseStack.last().normal());
        } else {
            this.taczvr$controllerPose = null;
            this.taczvr$controllerNormal = null;
        }
    }

    // This TACZ method overrides a Minecraft method, so production keeps only its SRG name.
    @Inject(method = {"renderByItem", "m_108829_"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void taczvr$renderGunAtVrController(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                                                MultiBufferSource buffer, int light, int overlay,
                                                CallbackInfo callback) {
        if (!context.firstPerson()
                || !TaczVrClientConfig.ENABLE_VR_GUN_RENDERING.get()
                || !TaczVrClient.vivecraft().isVrActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !ItemStack.matches(stack, player.getMainHandItem())) {
            return;
        }
        this.renderFirstPerson(player, stack, context, poseStack, buffer, light, minecraft.getFrameTime());
        callback.cancel();
    }

    @WrapOperation(
            method = "lambda$renderFirstPerson$5",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/client/model/BedrockGunModel;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V"
            ),
            require = 1
    )
    private void taczvr$renderWithoutModelHands(BedrockGunModel gunModel, PoseStack poseStack, ItemStack stack,
                                                 ItemDisplayContext context, RenderType renderType, int light,
                                                 int overlay, Operation<Void> original) {
        if (TaczVrClientConfig.ENABLE_VR_GUN_RENDERING.get() && TaczVrClient.vivecraft().isVrActive()) {
            boolean aligned = VrGunPoseTransform.alignRightHand(
                    poseStack, gunModel, stack, this.taczvr$controllerPose, this.taczvr$controllerNormal
            );
            if (aligned) {
                VrGunHandPoseCache.beginGunPass(gunModel, stack, this.taczvr$controllerPose);
                VrScopeAlignmentCapture.capture(gunModel, stack, poseStack);
            }
        }
        LocalPlayer player = Minecraft.getInstance().player;
        boolean renderTaczHands = player == null || VrGunHandVisibility.shouldRenderTaczModelHands(player, stack);
        if (!TaczVrClient.vivecraft().isVrActive() || renderTaczHands) {
            original.call(gunModel, poseStack, stack, context, renderType, light, overlay);
            return;
        }

        boolean renderHand = gunModel.getRenderHand();
        gunModel.setRenderHand(false);
        try {
            original.call(gunModel, poseStack, stack, context, renderType, light, overlay);
        } finally {
            gunModel.setRenderHand(renderHand);
        }
    }

    // Keep each named/SRG pair in one group: exactly one selector exists in either dev or production bytecode.
    @Group(name = "taczvr$viewPitch", min = 1, max = 1)
    @Redirect(
            method = "lambda$renderFirstPerson$5",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getViewXRot(F)F"
            ),
            require = 0
    )
    private float taczvr$removeVrViewPitchFromGun(LocalPlayer player, float partialTick) {
        return this.taczvr$resolveViewPitch(player, partialTick);
    }

    @Group(name = "taczvr$viewPitch", min = 1, max = 1)
    @Redirect(
            method = "lambda$renderFirstPerson$5",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;m_5686_(F)F",
                    remap = false
            ),
            require = 0
    )
    private float taczvr$removeVrViewPitchFromGunSrg(LocalPlayer player, float partialTick) {
        return this.taczvr$resolveViewPitch(player, partialTick);
    }

    @Unique
    private float taczvr$resolveViewPitch(LocalPlayer player, float partialTick) {
        if (TaczVrClientConfig.ENABLE_VR_GUN_RENDERING.get() && TaczVrClient.vivecraft().isVrActive()) {
            return Mth.lerp(partialTick, player.xBobO, player.xBob);
        }
        return player.getViewXRot(partialTick);
    }

    @Group(name = "taczvr$viewYaw", min = 1, max = 1)
    @Redirect(
            method = "lambda$renderFirstPerson$5",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getViewYRot(F)F"
            ),
            require = 0
    )
    private float taczvr$removeVrViewYawFromGun(LocalPlayer player, float partialTick) {
        return this.taczvr$resolveViewYaw(player, partialTick);
    }

    @Group(name = "taczvr$viewYaw", min = 1, max = 1)
    @Redirect(
            method = "lambda$renderFirstPerson$5",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;m_5675_(F)F",
                    remap = false
            ),
            require = 0
    )
    private float taczvr$removeVrViewYawFromGunSrg(LocalPlayer player, float partialTick) {
        return this.taczvr$resolveViewYaw(player, partialTick);
    }

    @Unique
    private float taczvr$resolveViewYaw(LocalPlayer player, float partialTick) {
        if (TaczVrClientConfig.ENABLE_VR_GUN_RENDERING.get() && TaczVrClient.vivecraft().isVrActive()) {
            return Mth.lerp(partialTick, player.yBobO, player.yBob);
        }
        return player.getViewYRot(partialTick);
    }
}
