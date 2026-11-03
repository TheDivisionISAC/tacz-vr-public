package com.thedivisionisac.taczvr.client.render;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client_vr.ClientDataHolderVR;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Shares TACZ's animated hand anchors with Vivecraft inside one render frame. All state is render-thread-only and
 * separated by eye pass so neither stereo matrices nor animation poses leak into the other eye.
 * Functional-anchor history belongs to the actual model instance and is discarded on resource/session reset.
 */
public final class VrGunHandPoseCache {
    private static final float REGULAR_ARM_HALF_WIDTH = 0.375F;
    private static final float SLIM_ARM_HALF_WIDTH = 0.34375F;
    private static final float ARM_LENGTH = 0.75F;
    private static final Map<RenderPass, PassPose> PASS_POSES = new EnumMap<>(RenderPass.class);
    private static final Map<RenderPass, AnimatedHandState> MAIN_HAND_STATES = new EnumMap<>(RenderPass.class);
    private static final Map<RenderPass, AnimatedHandState> SUPPORT_ANIMATION_STATES = new EnumMap<>(RenderPass.class);
    private static final ModelHandAnchorHistory<BedrockGunModel> ANCHOR_HISTORY = new ModelHandAnchorHistory<>();

    private static boolean supportHandSnapped;
    private static ResourceLocation snappedGunId;

    private VrGunHandPoseCache() {
    }

    public static void resetTransientState() {
        PASS_POSES.clear();
        MAIN_HAND_STATES.clear();
        SUPPORT_ANIMATION_STATES.clear();
        ANCHOR_HISTORY.reset();
        releaseSupportHand();
    }

    public static void releaseSupportHandState() {
        SUPPORT_ANIMATION_STATES.clear();
        releaseSupportHand();
    }

    public static void beginGunPass(BedrockGunModel model, ItemStack stack, @Nullable Matrix4f controllerPose) {
        if (!isAnyAnimatedHandFeatureEnabled()) {
            return;
        }
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        RenderPass pass = holder.currentPass;
        ResourceLocation gunId = getGunId(stack);
        if (controllerPose == null || pass == null || gunId == null || !RenderPass.isFirstPerson(pass)) {
            return;
        }
        PASS_POSES.put(pass, new PassPose(holder.frameIndex, model, gunId));
    }

    public static void captureLeftAnchor(BedrockGunModel model, PoseStack poseStack) {
        if (!TaczVrClientConfig.ENABLE_VR_SUPPORT_HAND_SNAP.get()
                && !TaczVrClientConfig.ENABLE_VR_SHIELD_HAND_SNAP.get()
                && !TaczVrClientConfig.ENABLE_VR_ANIMATION_HAND_SNAP.get()) {
            return;
        }
        PassPose passPose = getCurrentPass(model);
        if (passPose != null) {
            passPose.leftPalmPosition = getPalmPosition(poseStack.last().pose(), false);
            ANCHOR_HISTORY.recordLeft(model);
        }
    }

    public static void captureRightAnchor(BedrockGunModel model, PoseStack poseStack) {
        if (!TaczVrClientConfig.ENABLE_VR_SHOOT_HAND_ANIMATION.get()
                && !TaczVrClientConfig.ENABLE_VR_ANIMATION_HAND_SNAP.get()
                && !TaczVrClientConfig.ENABLE_VR_NEAR_MAIN_HAND_SUPPORT_SNAP.get()
                && !VrGunHandVisibility.keepsMainHandOnGrip()) {
            return;
        }
        PassPose passPose = getCurrentPass(model);
        if (passPose == null) {
            return;
        }
        passPose.rightPalmPosition = getPalmPosition(poseStack.last().pose(), true);
        ANCHOR_HISTORY.recordRight(model);
    }

    public static boolean hasKnownAnimationAnchors(ItemStack stack) {
        return ANCHOR_HISTORY.hasBoth(currentGunModel(stack));
    }

    public static boolean isSupportHandSnapped(ItemStack stack) {
        ResourceLocation gunId = getGunId(stack);
        return supportHandSnapped && gunId != null && gunId.equals(snappedGunId);
    }

    public static boolean applySupportHandSnap(PoseStack poseStack, ItemStack gunStack) {
        if (!TaczVrClient.vivecraft().isVrActive()) {
            releaseSupportHand();
            SUPPORT_ANIMATION_STATES.clear();
            return false;
        }
        PassPose passPose = getCurrentPass(gunStack);
        if (passPose == null || passPose.leftPalmPosition == null) {
            return false;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        boolean forceAnimation = player != null
                && VrGunHandVisibility.isForcedSupportHandSnapActive(player, gunStack);
        Vector3f currentPosition = poseStack.last().pose().getTranslation(new Vector3f());
        Vector3f targetPosition = passPose.leftPalmPosition;
        if (forceAnimation) {
            releaseSupportHand();
            return applyAnimatedPalmOffset(
                    poseStack,
                    passPose,
                    passPose.leftPalmPosition,
                    SUPPORT_ANIMATION_STATES,
                    TaczVrClientConfig.VR_ANIMATION_HAND_SNAP_SMOOTHING.get().floatValue(),
                    TaczVrClientConfig.VR_ANIMATION_HAND_MAX_TRANSLATION.get().floatValue()
            );
        }

        if (snappedGunId != null && !snappedGunId.equals(passPose.gunId)) {
            releaseSupportHand();
        }
        float leftAnchorDistance = currentPosition.distance(targetPosition);
        float mainHandDistance = passPose.rightPalmPosition == null
                ? Float.POSITIVE_INFINITY
                : currentPosition.distance(passPose.rightPalmPosition);
        float threshold = (supportHandSnapped
                ? TaczVrClientConfig.VR_SUPPORT_HAND_SNAP_EXIT_DISTANCE
                : TaczVrClientConfig.VR_SUPPORT_HAND_SNAP_ENTER_DISTANCE).get().floatValue();
        boolean supportSnapEnabled = TaczVrClientConfig.ENABLE_VR_SUPPORT_HAND_SNAP.get()
                || TaczVrClientConfig.ENABLE_VR_SHIELD_HAND_SNAP.get();
        boolean attachToGrip = VrSupportHandSnapPolicy.shouldAttach(
                supportSnapEnabled,
                TaczVrClientConfig.ENABLE_VR_NEAR_MAIN_HAND_SUPPORT_SNAP.get(),
                isNonPistol(gunStack),
                leftAnchorDistance,
                mainHandDistance,
                threshold
        );
        if (!attachToGrip) {
            releaseSupportHand();
            return applyAnimatedPalmOffset(
                    poseStack,
                    passPose,
                    null,
                    SUPPORT_ANIMATION_STATES,
                    TaczVrClientConfig.VR_SUPPORT_HAND_RELEASE_SMOOTHING.get().floatValue(),
                    TaczVrClientConfig.VR_SUPPORT_HAND_SNAP_EXIT_DISTANCE.get().floatValue()
            );
        }

        supportHandSnapped = true;
        snappedGunId = passPose.gunId;
        return applyAnimatedPalmOffset(
                poseStack,
                passPose,
                targetPosition,
                SUPPORT_ANIMATION_STATES,
                TaczVrClientConfig.VR_SUPPORT_HAND_SNAP_SMOOTHING.get().floatValue(),
                TaczVrClientConfig.VR_SUPPORT_HAND_SNAP_EXIT_DISTANCE.get().floatValue()
        );
    }

    public static boolean applyShootHandAnimation(PoseStack poseStack, ItemStack gunStack) {
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        RenderPass pass = holder.currentPass;
        boolean keepGrip = VrGunHandVisibility.keepsMainHandOnGrip();
        if ((!TaczVrClientConfig.ENABLE_VR_SHOOT_HAND_ANIMATION.get()
                && !TaczVrClientConfig.ENABLE_VR_ANIMATION_HAND_SNAP.get()
                && !keepGrip) || pass == null) {
            if (pass != null) {
                MAIN_HAND_STATES.remove(pass);
            }
            return false;
        }
        PassPose passPose = getCurrentPass(gunStack);
        if (passPose == null || passPose.rightPalmPosition == null) {
            return false;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        boolean forceAnimation = player != null
                && VrGunHandVisibility.isForcedAnimationSnapActive(player, gunStack);
        boolean forceShoot = TaczVrClientConfig.ENABLE_VR_SHOOT_HAND_ANIMATION.get()
                && VrGunHandVisibility.isShootAnimationActive(gunStack);
        float smoothing = forceAnimation
                ? TaczVrClientConfig.VR_ANIMATION_MAIN_HAND_SNAP_SMOOTHING.get().floatValue()
                : forceShoot
                    ? TaczVrClientConfig.VR_SHOOT_HAND_SNAP_SMOOTHING.get().floatValue()
                    : keepGrip
                        ? 1.0F
                        : TaczVrClientConfig.VR_ANIMATION_HAND_RELEASE_SMOOTHING.get().floatValue();
        float maxTranslation = forceAnimation
                ? TaczVrClientConfig.VR_ANIMATION_HAND_MAX_TRANSLATION.get().floatValue()
                : forceShoot
                    ? TaczVrClientConfig.VR_SHOOT_HAND_MAX_TRANSLATION.get().floatValue()
                    : keepGrip
                        ? TaczVrClientConfig.VR_ANIMATION_HAND_MAX_TRANSLATION.get().floatValue()
                        : TaczVrClientConfig.VR_SHOOT_HAND_MAX_TRANSLATION.get().floatValue();
        return applyAnimatedPalmOffset(
                poseStack,
                passPose,
                forceAnimation || forceShoot || keepGrip ? passPose.rightPalmPosition : null,
                MAIN_HAND_STATES,
                smoothing,
                maxTranslation
        );
    }

    private static boolean applyAnimatedPalmOffset(PoseStack poseStack, PassPose passPose,
                                                    @Nullable Vector3f targetPosition,
                                                    Map<RenderPass, AnimatedHandState> states,
                                                    float smoothing, float maxTranslation) {
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        RenderPass pass = holder.currentPass;
        if (pass == null) {
            return false;
        }
        AnimatedHandState state = states.get(pass);
        if (state == null && targetPosition == null) {
            return false;
        }
        if (state == null) {
            state = new AnimatedHandState();
            states.put(pass, state);
        }
        if (!passPose.gunId.equals(state.gunId)) {
            state.offset.zero();
            state.gunId = passPose.gunId;
        }

        Matrix4f handPose = poseStack.last().pose();
        Vector3f currentPosition = handPose.getTranslation(new Vector3f());
        Vector3f targetOffset = targetPosition == null
                ? new Vector3f()
                : new Vector3f(targetPosition).sub(currentPosition);
        if (targetOffset.lengthSquared() > maxTranslation * maxTranslation) {
            targetOffset.normalize(maxTranslation);
        }
        if (state.frameIndex != holder.frameIndex) {
            state.offset.lerp(targetOffset, smoothing);
            state.frameIndex = holder.frameIndex;
        }
        if (state.offset.lengthSquared() < 0.000001F) {
            state.offset.zero();
            if (targetPosition == null) {
                states.remove(pass);
            }
            return false;
        }

        // Functional hand nodes provide palm position; controller tracking continues to own wrist rotation.
        handPose.m30(currentPosition.x + state.offset.x)
                .m31(currentPosition.y + state.offset.y)
                .m32(currentPosition.z + state.offset.z);
        return true;
    }

    private static void releaseSupportHand() {
        supportHandSnapped = false;
        snappedGunId = null;
    }

    private static boolean isAnyAnimatedHandFeatureEnabled() {
        return TaczVrClientConfig.ENABLE_VR_SUPPORT_HAND_SNAP.get()
                || TaczVrClientConfig.ENABLE_VR_NEAR_MAIN_HAND_SUPPORT_SNAP.get()
                || TaczVrClientConfig.ENABLE_VR_SHIELD_HAND_SNAP.get()
                || TaczVrClientConfig.ENABLE_VR_SHOOT_HAND_ANIMATION.get()
                || TaczVrClientConfig.ENABLE_VR_ANIMATION_HAND_SNAP.get()
                || VrGunHandVisibility.keepsMainHandOnGrip();
    }

    private static Vector3f getPalmPosition(Matrix4f armOriginPose, boolean rightHand) {
        boolean slimArm = net.minecraft.client.Minecraft.getInstance().player != null
                && "slim".equals(net.minecraft.client.Minecraft.getInstance().player.getModelName());
        float halfWidth = slimArm ? SLIM_ARM_HALF_WIDTH : REGULAR_ARM_HALF_WIDTH;
        return armOriginPose.transformPosition(new Vector3f(
                rightHand ? halfWidth : -halfWidth,
                -ARM_LENGTH,
                0.0F
        ));
    }

    @Nullable
    private static PassPose getCurrentPass(BedrockGunModel model) {
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        PassPose pose = PASS_POSES.get(holder.currentPass);
        return pose != null && pose.frameIndex == holder.frameIndex && pose.model == model ? pose : null;
    }

    @Nullable
    private static PassPose getCurrentPass(ItemStack stack) {
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        PassPose pose = PASS_POSES.get(holder.currentPass);
        ResourceLocation gunId = getGunId(stack);
        return pose != null && pose.frameIndex == holder.frameIndex && pose.gunId.equals(gunId)
                && pose.model == currentGunModel(stack) ? pose : null;
    }

    @Nullable
    private static BedrockGunModel currentGunModel(ItemStack stack) {
        return TimelessAPI.getGunDisplay(stack).map(display -> display.getGunModel()).orElse(null);
    }

    @Nullable
    private static ResourceLocation getGunId(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? null : gun.getGunId(stack);
    }

    private static boolean isNonPistol(ItemStack stack) {
        ResourceLocation gunId = getGunId(stack);
        return gunId != null && TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> !"pistol".equalsIgnoreCase(index.getType()))
                .orElse(false);
    }

    private static final class PassPose {
        private final long frameIndex;
        private final BedrockGunModel model;
        private final ResourceLocation gunId;
        private Vector3f leftPalmPosition;
        private Vector3f rightPalmPosition;

        private PassPose(long frameIndex, BedrockGunModel model, ResourceLocation gunId) {
            this.frameIndex = frameIndex;
            this.model = model;
            this.gunId = gunId;
        }
    }

    private static final class AnimatedHandState {
        private final Vector3f offset = new Vector3f();
        private long frameIndex = Long.MIN_VALUE;
        private ResourceLocation gunId;
    }
}
