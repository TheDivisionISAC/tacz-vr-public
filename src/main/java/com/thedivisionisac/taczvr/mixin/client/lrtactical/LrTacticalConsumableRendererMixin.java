package com.thedivisionisac.taczvr.mixin.client.lrtactical;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.client.compat.lrtactical.LrTacticalVrAnimationState;
import com.thedivisionisac.taczvr.client.compat.lrtactical.LrTacticalVrControllerPoseCache;
import com.thedivisionisac.taczvr.client.compat.lrtactical.LrTacticalVrHandPoseCache;
import com.thedivisionisac.taczvr.client.compat.lrtactical.LrTacticalVrItemPoseTransform;
import com.thedivisionisac.taczvr.client.compat.lrtactical.LrTacticalVrItemSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Aligns LRTactical food and medicine with the validated throwable hand transform. */
@Pseudo
@Mixin(targets = "me.xjqsh.lrtactical.client.renderer.item.ConsumableItemRenderer", remap = false)
abstract class LrTacticalConsumableRendererMixin {
    @Unique
    private Matrix4f taczvr$controllerPose;
    @Unique
    private Matrix3f taczvr$controllerNormal;

    @Shadow
    public abstract void renderFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext context,
                                           PoseStack poseStack, MultiBufferSource buffer, int light,
                                           float partialTick);

    @Shadow
    public abstract BedrockAnimatedModel getModel(ItemStack stack);

    @Shadow
    public abstract LuaAnimationStateMachine<?> getStateMachine(ItemStack stack);

    @Inject(method = "renderFirstPerson", at = @At("HEAD"), require = 1)
    private void taczvr$captureControllerPose(LocalPlayer player, ItemStack stack, ItemDisplayContext context,
                                               PoseStack poseStack, MultiBufferSource buffer, int light,
                                               float partialTick, CallbackInfo callback) {
        if (TaczVrClient.vivecraft().isVrActive()
                && LrTacticalVrItemSupport.isRenderSupported(stack)
                && LrTacticalVrItemSupport.isConsumable(stack)) {
            LrTacticalVrControllerPoseCache.ControllerPose physicalMain =
                    LrTacticalVrControllerPoseCache.getPhysicalMainHand();
            if (physicalMain != null) {
                this.taczvr$controllerPose = physicalMain.pose();
                this.taczvr$controllerNormal = physicalMain.normal();
            } else {
                this.taczvr$controllerPose = new Matrix4f(poseStack.last().pose());
                this.taczvr$controllerNormal = new Matrix3f(poseStack.last().normal());
            }
        } else {
            this.taczvr$controllerPose = null;
            this.taczvr$controllerNormal = null;
        }
    }

    @Inject(method = {"renderByItem", "m_108829_"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void taczvr$renderAtController(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                                           MultiBufferSource buffer, int light, int overlay,
                                           CallbackInfo callback) {
        if (!context.firstPerson()
                || !TaczVrClient.vivecraft().isVrActive()
                || !LrTacticalVrItemSupport.isRenderSupported(stack)
                || !LrTacticalVrItemSupport.isConsumable(stack)) {
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

    @Inject(
            method = "renderFirstPerson",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/client/model/BedrockAnimatedModel;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V"
            ),
            require = 1
    )
    private void taczvr$alignAnimatedPalm(LocalPlayer player, ItemStack stack, ItemDisplayContext context,
                                          PoseStack poseStack, MultiBufferSource buffer, int light,
                                          float partialTick, CallbackInfo callback) {
        BedrockAnimatedModel model = this.getModel(stack);
        if (model != null) {
            LuaAnimationStateMachine<?> stateMachine = this.getStateMachine(stack);
            boolean manipulationActive = LrTacticalVrAnimationState.hasManipulationAnimation(stateMachine);
            LrTacticalVrItemPoseTransform.alignRightHand(
                    poseStack, model, this.taczvr$controllerPose, this.taczvr$controllerNormal,
                    stack, stateMachine, manipulationActive
            );
            LrTacticalVrHandPoseCache.beginPass(model, stack, manipulationActive);
        }
    }

    @Group(name = "taczvr$lrtConsumableViewPitch", min = 1, max = 1)
    @Redirect(
            method = "renderFirstPerson",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getViewXRot(F)F"),
            require = 0
    )
    private float taczvr$removeVrViewPitch(LocalPlayer player, float partialTick) {
        return taczvr$resolveViewPitch(player, partialTick);
    }

    @Group(name = "taczvr$lrtConsumableViewPitch", min = 1, max = 1)
    @Redirect(
            method = "renderFirstPerson",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;m_5686_(F)F",
                    remap = false
            ),
            require = 0
    )
    private float taczvr$removeVrViewPitchSrg(LocalPlayer player, float partialTick) {
        return taczvr$resolveViewPitch(player, partialTick);
    }

    @Unique
    private float taczvr$resolveViewPitch(LocalPlayer player, float partialTick) {
        return TaczVrClient.vivecraft().isVrActive()
                ? Mth.lerp(partialTick, player.xBobO, player.xBob)
                : player.getViewXRot(partialTick);
    }

    @Group(name = "taczvr$lrtConsumableViewYaw", min = 1, max = 1)
    @Redirect(
            method = "renderFirstPerson",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getViewYRot(F)F"),
            require = 0
    )
    private float taczvr$removeVrViewYaw(LocalPlayer player, float partialTick) {
        return taczvr$resolveViewYaw(player, partialTick);
    }

    @Group(name = "taczvr$lrtConsumableViewYaw", min = 1, max = 1)
    @Redirect(
            method = "renderFirstPerson",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;m_5675_(F)F",
                    remap = false
            ),
            require = 0
    )
    private float taczvr$removeVrViewYawSrg(LocalPlayer player, float partialTick) {
        return taczvr$resolveViewYaw(player, partialTick);
    }

    @Unique
    private float taczvr$resolveViewYaw(LocalPlayer player, float partialTick) {
        return TaczVrClient.vivecraft().isVrActive()
                ? Mth.lerp(partialTick, player.yBobO, player.yBob)
                : player.getViewYRot(partialTick);
    }
}
