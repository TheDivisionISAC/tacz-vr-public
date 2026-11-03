package com.thedivisionisac.taczvr.client.compat.lrtactical;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.thedivisionisac.taczvr.config.VrHandDisplayMode;
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
 * Captures the functional hand nodes emitted by supported LRTactical models and reuses their animated palm positions
 * for Vivecraft hands. State is render-thread-only, stereo-pass-local, and contains no LRTactical types.
 */
public final class LrTacticalVrHandPoseCache {
    private static final float REGULAR_ARM_HALF_WIDTH = 0.375F;
    private static final float SLIM_ARM_HALF_WIDTH = 0.34375F;
    private static final float ARM_LENGTH = 0.75F;
    private static final long MAX_POST_RENDER_FRAME_LAG = 1L;
    private static final Map<RenderPass, PassPose> PASS_POSES = new EnumMap<>(RenderPass.class);
    private static final Map<RenderPass, AnimatedHandState> MAIN_HAND_STATES = new EnumMap<>(RenderPass.class);
    private static final Map<RenderPass, AnimatedHandState> SUPPORT_HAND_STATES = new EnumMap<>(RenderPass.class);
    private static ResourceLocation latchedManipulationItemId;
    private static long lastManipulationAnimationNs;

    private LrTacticalVrHandPoseCache() {
    }

    public static void beginPass(BedrockAnimatedModel model, ItemStack stack, boolean manipulationActive) {
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        RenderPass pass = holder.currentPass;
        ResourceLocation itemId = LrTacticalVrItemSupport.getLogicalItemId(stack);
        if (!isActiveItem(stack)
                || pass == null
                || itemId == null
                || !RenderPass.isFirstPerson(pass)) {
            return;
        }
        boolean renderModelHands = shouldRenderModelHands(stack, manipulationActive);
        boolean suppressColdModel = LrTacticalVrItemPoseTransform.shouldSuppressColdModel(stack);
        LrTacticalVrItemHandResolver.AuthoredHand primaryHand =
                LrTacticalVrItemHandResolver.resolve(model, itemId.getPath()).primary();
        PASS_POSES.put(pass, new PassPose(
                holder.frameIndex,
                model,
                itemId,
                renderModelHands,
                suppressColdModel,
                primaryHand
        ));
    }

    public static boolean shouldSuppressModel(BedrockAnimatedModel model) {
        PassPose passPose = getCurrentPass(model);
        return passPose != null && passPose.suppressColdModel;
    }

    /**
     * @return {@code true} when this is an active LRTactical pass and its authored model arm should be suppressed.
     */
    public static boolean captureRightAnchor(BedrockAnimatedModel model, PoseStack poseStack) {
        PassPose passPose = getCurrentPass(model);
        if (passPose == null) {
            return false;
        }
        passPose.rightPalmPosition = getPalmPosition(poseStack.last().pose(), true);
        return !passPose.renderModelHands;
    }

    /**
     * @return {@code true} when this is an active LRTactical pass and its authored model arm should be suppressed.
     */
    public static boolean captureLeftAnchor(BedrockAnimatedModel model, PoseStack poseStack) {
        PassPose passPose = getCurrentPass(model);
        if (passPose == null) {
            return false;
        }
        passPose.leftPalmPosition = getPalmPosition(poseStack.last().pose(), false);
        return !passPose.renderModelHands;
    }

    public static boolean shouldHideTrackedHands(ItemStack mainHandStack) {
        if (!isActiveItem(mainHandStack)) {
            return false;
        }
        return switch (TaczVrClientConfig.VR_HAND_DISPLAY_MODE.get()) {
            case HIDDEN -> true;
            case TACZ_DURING_MANIPULATION -> isManipulationAnimationActive(mainHandStack);
            case VIVECRAFT_SNAP -> false;
        };
    }

    public static boolean shouldPostRenderMainHand(ItemStack stack) {
        return isActiveItem(stack)
                && com.thedivisionisac.taczvr.client.render.VrGunHandVisibility.allowsVivecraftHands()
                && !shouldRenderModelHands(stack)
                && TaczVrClientConfig.POST_RENDER_VIVECRAFT_GUN_HAND.get();
    }

    public static boolean applyMainHandAnimation(PoseStack poseStack, ItemStack stack) {
        PassPose passPose = getCurrentPass(stack);
        LrTacticalVrItemHandResolver.AuthoredHand renderedHand =
                LrTacticalVrHandRoleResolver.physicalMainAuthoredHand(
                         TaczVrClient.vivecraft().isLeftHanded()
                 );
        if (!shouldFollowHandAnchor(stack, passPose, renderedHand)) {
            MAIN_HAND_STATES.clear();
            return false;
        }
        return applyAnimatedPalmOffset(
                poseStack,
                passPose,
                renderedHand == LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                MAIN_HAND_STATES,
                TaczVrClientConfig.VR_ANIMATION_MAIN_HAND_SNAP_SMOOTHING.get().floatValue()
        );
    }

    public static boolean applySupportHandAnimation(PoseStack poseStack, ItemStack stack) {
        PassPose passPose = getCurrentPass(stack);
        LrTacticalVrItemHandResolver.AuthoredHand renderedHand =
                LrTacticalVrHandRoleResolver.physicalSupportAuthoredHand(
                        TaczVrClient.vivecraft().isLeftHanded()
                );
        if (!shouldFollowHandAnchor(stack, passPose, renderedHand)) {
            releaseSupportHandState();
            return false;
        }
        return applyAnimatedPalmOffset(
                poseStack,
                passPose,
                renderedHand == LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                SUPPORT_HAND_STATES,
                TaczVrClientConfig.VR_ANIMATION_HAND_SNAP_SMOOTHING.get().floatValue()
        );
    }

    private static boolean shouldFollowHandAnchor(
            ItemStack stack,
            @Nullable PassPose passPose,
            LrTacticalVrItemHandResolver.AuthoredHand renderedHand) {
        if (passPose == null || !isActiveItem(stack)) {
            return false;
        }
        boolean supportedUseAnimationActive =
                (LrTacticalVrItemSupport.isThrowable(stack)
                || LrTacticalVrItemSupport.isConsumable(stack))
                && isManipulationAnimationActive(stack);
        return LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                passPose.primaryHand,
                renderedHand,
                supportedUseAnimationActive
        );
    }

    public static void releaseSupportHandState() {
        SUPPORT_HAND_STATES.clear();
    }

    public static void resetTransientState() {
        PASS_POSES.clear();
        MAIN_HAND_STATES.clear();
        SUPPORT_HAND_STATES.clear();
        clearManipulationLatch();
        LrTacticalVrItemPoseTransform.resetTransientState();
    }

    private static boolean applyAnimatedPalmOffset(PoseStack poseStack, @Nullable PassPose passPose,
                                                    boolean rightHand,
                                                    Map<RenderPass, AnimatedHandState> states,
                                                    float smoothing) {
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        RenderPass pass = holder.currentPass;
        if (!TaczVrClientConfig.ENABLE_VR_ANIMATION_HAND_SNAP.get() || pass == null) {
            if (pass != null) {
                states.remove(pass);
            }
            return false;
        }

        Vector3f targetPosition = passPose == null
                ? null
                : rightHand ? passPose.rightPalmPosition : passPose.leftPalmPosition;
        AnimatedHandState state = states.get(pass);
        if (state == null && targetPosition == null) {
            return false;
        }
        if (state == null) {
            state = new AnimatedHandState();
            states.put(pass, state);
        }
        if (passPose != null && !passPose.itemId.equals(state.itemId)) {
            state.offset.zero();
            state.itemId = passPose.itemId;
        }

        Matrix4f handPose = poseStack.last().pose();
        Vector3f currentPosition = handPose.getTranslation(new Vector3f());
        Vector3f targetOffset = targetPosition == null
                ? new Vector3f()
                : new Vector3f(targetPosition).sub(currentPosition);
        float maxTranslation = TaczVrClientConfig.VR_ANIMATION_HAND_MAX_TRANSLATION.get().floatValue();
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

        handPose.m30(currentPosition.x + state.offset.x)
                .m31(currentPosition.y + state.offset.y)
                .m32(currentPosition.z + state.offset.z);
        return true;
    }

    private static boolean isActiveItem(ItemStack stack) {
        return TaczVrClient.vivecraft().isVrActive()
                && LrTacticalVrItemSupport.isRenderSupported(stack);
    }

    private static boolean shouldRenderModelHands(ItemStack stack, boolean manipulationActive) {
        updateManipulationLatch(stack, manipulationActive);
        return shouldRenderModelHands(stack);
    }

    private static boolean shouldRenderModelHands(ItemStack stack) {
        return TaczVrClientConfig.VR_HAND_DISPLAY_MODE.get() == VrHandDisplayMode.TACZ_DURING_MANIPULATION
                && isManipulationAnimationActive(stack);
    }

    private static boolean updateManipulationLatch(ItemStack stack, boolean activeNow) {
        ResourceLocation itemId = LrTacticalVrItemSupport.getLogicalItemId(stack);
        if (itemId == null) {
            clearManipulationLatch();
            return false;
        }
        long now = System.nanoTime();
        if (!itemId.equals(latchedManipulationItemId)) {
            latchedManipulationItemId = itemId;
            lastManipulationAnimationNs = 0L;
        }
        if (activeNow) {
            lastManipulationAnimationNs = now;
            return true;
        }
        return isWithinManipulationReleaseGrace(now);
    }

    private static boolean isManipulationAnimationActive(ItemStack stack) {
        ResourceLocation itemId = LrTacticalVrItemSupport.getLogicalItemId(stack);
        if (itemId == null || !itemId.equals(latchedManipulationItemId)) {
            return false;
        }
        return isWithinManipulationReleaseGrace(System.nanoTime());
    }

    private static boolean isWithinManipulationReleaseGrace(long now) {
        long graceNs = TaczVrClientConfig.VR_ANIMATION_HAND_RELEASE_GRACE_MS.get() * 1_000_000L;
        return lastManipulationAnimationNs != 0L && now - lastManipulationAnimationNs <= graceNs;
    }

    private static void clearManipulationLatch() {
        latchedManipulationItemId = null;
        lastManipulationAnimationNs = 0L;
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
    private static PassPose getCurrentPass(BedrockAnimatedModel model) {
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        PassPose pose = PASS_POSES.get(holder.currentPass);
        return pose != null && pose.frameIndex == holder.frameIndex && pose.model == model ? pose : null;
    }

    @Nullable
    private static PassPose getCurrentPass(ItemStack stack) {
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        PassPose pose = PASS_POSES.get(holder.currentPass);
        ResourceLocation itemId = LrTacticalVrItemSupport.getLogicalItemId(stack);
        return pose != null
                && isUsablePostRenderFrame(pose.frameIndex, holder.frameIndex)
                && pose.itemId.equals(itemId) ? pose : null;
    }

    static boolean isUsablePostRenderFrame(long cachedFrame, long currentFrame) {
        return currentFrame >= cachedFrame
                && currentFrame - cachedFrame <= MAX_POST_RENDER_FRAME_LAG;
    }

    private static final class PassPose {
        private final long frameIndex;
        private final BedrockAnimatedModel model;
        private final ResourceLocation itemId;
        private final boolean renderModelHands;
        private final boolean suppressColdModel;
        private final LrTacticalVrItemHandResolver.AuthoredHand primaryHand;
        private Vector3f leftPalmPosition;
        private Vector3f rightPalmPosition;

        private PassPose(long frameIndex, BedrockAnimatedModel model, ResourceLocation itemId,
                         boolean renderModelHands,
                         boolean suppressColdModel,
                         LrTacticalVrItemHandResolver.AuthoredHand primaryHand) {
            this.frameIndex = frameIndex;
            this.model = model;
            this.itemId = itemId;
            this.renderModelHands = renderModelHands;
            this.suppressColdModel = suppressColdModel;
            this.primaryHand = primaryHand;
        }
    }

    private static final class AnimatedHandState {
        private final Vector3f offset = new Vector3f();
        private long frameIndex = Long.MIN_VALUE;
        private ResourceLocation itemId;
    }
}
