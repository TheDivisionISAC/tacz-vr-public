package com.thedivisionisac.taczvr.client.compat.lrtactical;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationChannel;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.util.math.MathUtil;
import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.thedivisionisac.taczvr.mixin.client.AnimationControllerAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Re-bases a supported LRTactical item from its inferred authored grip onto the matching Vivecraft controller.
 * Throwables, consumables and melee items intentionally share this transform.
 */
public final class LrTacticalVrItemPoseTransform {
    private static final float REGULAR_ARM_HALF_WIDTH = 0.375F;
    private static final float SLIM_ARM_HALF_WIDTH = 0.34375F;
    private static final float ARM_LENGTH = 0.75F;
    private static final float FALLBACK_CONTROLLER_LOCAL_GRIP_Y = 0.14F;
    private static final long COLD_GRIP_TRACK_WAIT_NS = 1_000_000_000L;
    private static final long COLD_GRIP_TIMER_UNSTARTED_NS = Long.MIN_VALUE;
    private static final Map<ResourceLocation, StablePalm> STABLE_PALMS = new HashMap<>();
    private static final Map<ResourceLocation, ColdGripLatch> COLD_GRIP_LATCHES = new HashMap<>();
    private static final Set<ResourceLocation> LOGGED_RESOLUTIONS = new HashSet<>();
    private static final Set<ResourceLocation> LOGGED_STATIC_SEEDS = new HashSet<>();
    private static ResourceLocation observedHeldItemId;
    private static int observedSelectedSlot = Integer.MIN_VALUE;

    private LrTacticalVrItemPoseTransform() {
    }

    public static void observeHeldItem(@Nullable LocalPlayer player) {
        if (player == null) {
            clearObservedHeldItem();
            return;
        }

        ItemStack stack = player.getMainHandItem();
        ResourceLocation itemId = LrTacticalVrItemSupport.isRenderSupported(stack)
                ? LrTacticalVrItemSupport.getLogicalItemId(stack)
                : null;
        int selectedSlot = player.getInventory().selected;
        if (!heldItemSelectionChanged(
                observedHeldItemId, observedSelectedSlot, itemId, selectedSlot
        )) {
            return;
        }

        if (observedHeldItemId != null) {
            COLD_GRIP_LATCHES.remove(observedHeldItemId);
        }
        observedHeldItemId = itemId;
        observedSelectedSlot = selectedSlot;
        if (itemId != null) {
            COLD_GRIP_LATCHES.put(
                    itemId,
                    new ColdGripLatch(ColdGripPhase.WAITING_FOR_MANIPULATION, COLD_GRIP_TIMER_UNSTARTED_NS)
            );
        }
    }

    static boolean heldItemSelectionChanged(@Nullable ResourceLocation previousItemId, int previousSlot,
                                            @Nullable ResourceLocation currentItemId, int currentSlot) {
        return previousSlot != currentSlot || !Objects.equals(previousItemId, currentItemId);
    }

    public static boolean alignRightHand(PoseStack poseStack, BedrockAnimatedModel model, Matrix4f controllerPose,
                                         Matrix3f controllerNormal, ItemStack stack,
                                         @Nullable LuaAnimationStateMachine<?> stateMachine,
                                         boolean manipulationActive) {
        if (controllerPose == null || controllerNormal == null) {
            return false;
        }

        ResourceLocation itemId = LrTacticalVrItemSupport.getLogicalItemId(stack);
        LrTacticalVrItemHandResolver.Resolution resolution = LrTacticalVrItemHandResolver.resolve(
                model,
                itemId == null ? null : itemId.getPath()
        );
        LrTacticalVrItemHandResolver.AuthoredHand primaryHand = resolution.primary()
                == LrTacticalVrItemHandResolver.AuthoredHand.UNKNOWN
                ? LrTacticalVrItemHandResolver.AuthoredHand.RIGHT
                : resolution.primary();
        logResolution(itemId, resolution, primaryHand);

        LrTacticalVrControllerPoseCache.ControllerPose authoredController =
                LrTacticalVrControllerPoseCache.getAuthoredHand(primaryHand);
        Matrix4f selectedControllerPose = authoredController == null ? controllerPose : authoredController.pose();
        Matrix3f selectedControllerNormal = authoredController == null ? controllerNormal : authoredController.normal();
        String anchorName = primaryHand == LrTacticalVrItemHandResolver.AuthoredHand.LEFT
                ? LrTacticalVrItemHandResolver.LEFT_HAND_ANCHOR
                : LrTacticalVrItemHandResolver.RIGHT_HAND_ANCHOR;
        BedrockPart handAnchor = model == null ? null : model.getNode(anchorName);
        if (handAnchor != null
                && alignAnimatedPalm(
                        poseStack, model, handAnchor, selectedControllerPose, selectedControllerNormal, stack,
                        primaryHand, stateMachine, manipulationActive
                )) {
            return true;
        }
        // No animated palm will ever advance this latch on the fallback path. Keep root geometry visible.
        if (itemId != null) COLD_GRIP_LATCHES.remove(itemId);
        alignFallbackRoot(poseStack, selectedControllerPose, selectedControllerNormal);
        return true;
    }

    private static boolean alignAnimatedPalm(PoseStack poseStack, BedrockAnimatedModel model,
                                             BedrockPart handAnchor,
                                             Matrix4f controllerPose, Matrix3f controllerNormal,
                                             ItemStack stack,
                                             LrTacticalVrItemHandResolver.AuthoredHand primaryHand,
                                             @Nullable LuaAnimationStateMachine<?> stateMachine,
                                             boolean manipulationActive) {
        Matrix4f animatedPalm = createAnimatedPalmTransform(
                handAnchor,
                primaryHand == LrTacticalVrItemHandResolver.AuthoredHand.RIGHT
        );
        Vector3f palmPosition = animatedPalm.getTranslation(new Vector3f());
        if (!isFinite(palmPosition)) {
            return false;
        }

        ResourceLocation itemId = LrTacticalVrItemSupport.getLogicalItemId(stack);
        StablePalm cachedPalm = itemId == null ? null : STABLE_PALMS.get(itemId);
        Vector3f stablePalm = cachedPalm != null
                && cachedPalm.model == model
                && cachedPalm.hand == primaryHand
                ? cachedPalm.position
                : null;
        Vector3f staticPalm = stablePalm == null
                ? createStaticPalmPosition(handAnchor, primaryHand, stateMachine)
                : null;
        if (itemId != null && staticPalm != null) {
            COLD_GRIP_LATCHES.putIfAbsent(
                    itemId,
                    new ColdGripLatch(ColdGripPhase.WAITING_FOR_MANIPULATION, COLD_GRIP_TIMER_UNSTARTED_NS)
            );
        }
        boolean holdColdGrip = updateColdGripLatch(itemId, manipulationActive, System.nanoTime());
        boolean preserveStablePalm = manipulationActive || holdColdGrip;
        Vector3f alignmentPalm = chooseAlignmentPalm(
                palmPosition, stablePalm, staticPalm, preserveStablePalm
        );
        if (itemId != null && shouldStoreStablePalm(preserveStablePalm, staticPalm != null)) {
            STABLE_PALMS.put(itemId, new StablePalm(model, primaryHand, new Vector3f(alignmentPalm)));
            logStaticSeed(itemId, staticPalm);
        }

        // The functional hand renderer receives its *_pos node at the shoulder-side arm origin. TACZ then draws the
        // vanilla 12-pixel player arm from that pose. Idle frames continually refresh the stable controller grip.
        // During an authored action that stable palm remains fixed, so the current animated palm and both authored
        // hands can move together instead of being cancelled by a per-frame inverse solve.
        float scale = TaczVrClientConfig.VR_LRTACTICAL_THROWABLE_SCALE.get().floatValue();
        Matrix4f controllerToModel = createPositionOnlyControllerToModel(alignmentPalm, scale);

        PoseStack.Pose pose = poseStack.last();
        pose.pose().set(controllerPose).mul(controllerToModel);
        Matrix3f rotationOnly = new Matrix3f().rotateZ((float) Math.PI);
        pose.normal().set(controllerNormal).mul(rotationOnly);
        return true;
    }

    static Vector3f chooseAlignmentPalm(Vector3f currentPalm, @Nullable Vector3f stablePalm,
                                        @Nullable Vector3f staticPalm,
                                        boolean preserveStablePalm) {
        // LRTactical schedules draw on the first state-machine update, but the animation track can remain absent
        // from getUpdatingTrackArray() for several render frames. A cold item must therefore use static_idle before
        // consulting the reported track state, otherwise the first draw pose becomes the cached controller grip.
        if (stablePalm == null && staticPalm != null) {
            return new Vector3f(staticPalm);
        }
        if (!preserveStablePalm) {
            return new Vector3f(currentPalm);
        }
        if (stablePalm != null) {
            return new Vector3f(stablePalm);
        }
        return staticPalm != null ? new Vector3f(staticPalm) : new Vector3f(currentPalm);
    }

    static boolean shouldRefreshStablePalm(boolean preserveStablePalm) {
        return !preserveStablePalm;
    }

    static boolean shouldStoreStablePalm(boolean preserveStablePalm, boolean hasStaticPalmSeed) {
        return shouldRefreshStablePalm(preserveStablePalm) || hasStaticPalmSeed;
    }

    static ColdGripPhase nextColdGripPhase(ColdGripPhase phase, boolean manipulationActive,
                                           boolean waitTimedOut) {
        return switch (phase) {
            case NONE -> ColdGripPhase.NONE;
            case WAITING_FOR_MANIPULATION -> manipulationActive
                    ? ColdGripPhase.MANIPULATION_ACTIVE
                    : waitTimedOut ? ColdGripPhase.NONE : ColdGripPhase.WAITING_FOR_MANIPULATION;
            case MANIPULATION_ACTIVE -> manipulationActive
                    ? ColdGripPhase.MANIPULATION_ACTIVE
                    : ColdGripPhase.NONE;
        };
    }

    public static boolean shouldSuppressColdModel(ItemStack stack) {
        ResourceLocation itemId = LrTacticalVrItemSupport.getLogicalItemId(stack);
        ColdGripLatch latch = itemId == null ? null : COLD_GRIP_LATCHES.get(itemId);
        return latch != null && shouldSuppressColdModel(latch.phase);
    }

    static boolean shouldSuppressColdModel(ColdGripPhase phase) {
        return phase == ColdGripPhase.WAITING_FOR_MANIPULATION;
    }

    public static void resetTransientState() {
        STABLE_PALMS.clear();
        COLD_GRIP_LATCHES.clear();
        LOGGED_RESOLUTIONS.clear();
        LOGGED_STATIC_SEEDS.clear();
        clearObservedHeldItem();
        LrTacticalVrItemHandResolver.reset();
    }

    private static void clearObservedHeldItem() {
        observedHeldItemId = null;
        observedSelectedSlot = Integer.MIN_VALUE;
    }

    static Matrix4f createPositionOnlyControllerToModel(Vector3f palmPosition, float scale) {
        return new Matrix4f()
                .rotate(Axis.ZP.rotationDegrees(180.0F))
                .scale(scale)
                .translate(-palmPosition.x, -palmPosition.y, -palmPosition.z);
    }

    private static Matrix4f createAnimatedPalmTransform(BedrockPart handAnchor, boolean rightHand) {
        Deque<BedrockPart> path = new ArrayDeque<>();
        for (BedrockPart part = handAnchor; part != null; part = part.getParent()) {
            path.addFirst(part);
        }

        PoseStack palmPose = new PoseStack();
        for (BedrockPart part : path) {
            part.translateAndRotateAndScale(palmPose);
        }
        boolean slimArm = net.minecraft.client.Minecraft.getInstance().player != null
                && "slim".equals(net.minecraft.client.Minecraft.getInstance().player.getModelName());
        float halfWidth = slimArm ? SLIM_ARM_HALF_WIDTH : REGULAR_ARM_HALF_WIDTH;
        palmPose.translate(rightHand ? halfWidth : -halfWidth, -ARM_LENGTH, 0.0F);
        return new Matrix4f(palmPose.last().pose());
    }

    @Nullable
    private static Vector3f createStaticPalmPosition(
            BedrockPart handAnchor,
            LrTacticalVrItemHandResolver.AuthoredHand primaryHand,
            @Nullable LuaAnimationStateMachine<?> stateMachine) {
        ObjectAnimation staticIdle = findStaticIdle(stateMachine);
        if (staticIdle == null) {
            return null;
        }

        Deque<BedrockPart> path = new ArrayDeque<>();
        for (BedrockPart part = handAnchor; part != null; part = part.getParent()) {
            path.addFirst(part);
        }

        PoseStack palmPose = new PoseStack();
        for (BedrockPart part : path) {
            StaticNodePose staticPose = getStaticNodePose(staticIdle, part.name);
            palmPose.translate(
                    staticPose.offsetX + part.x / 16.0F,
                    staticPose.offsetY + part.y / 16.0F,
                    staticPose.offsetZ + part.z / 16.0F
            );
            if (part.zRot != 0.0F) {
                palmPose.mulPose(Axis.ZP.rotation(part.zRot));
            }
            if (part.yRot != 0.0F) {
                palmPose.mulPose(Axis.YP.rotation(part.yRot));
            }
            if (part.xRot != 0.0F) {
                palmPose.mulPose(Axis.XP.rotation(part.xRot));
            }
            palmPose.mulPose(staticPose.rotation);
            palmPose.scale(staticPose.scaleX, staticPose.scaleY, staticPose.scaleZ);
        }

        boolean slimArm = net.minecraft.client.Minecraft.getInstance().player != null
                && "slim".equals(net.minecraft.client.Minecraft.getInstance().player.getModelName());
        float halfWidth = slimArm ? SLIM_ARM_HALF_WIDTH : REGULAR_ARM_HALF_WIDTH;
        palmPose.translate(
                primaryHand == LrTacticalVrItemHandResolver.AuthoredHand.RIGHT ? halfWidth : -halfWidth,
                -ARM_LENGTH,
                0.0F
        );
        Vector3f staticPalm = palmPose.last().pose().getTranslation(new Vector3f());
        return isFinite(staticPalm) ? staticPalm : null;
    }

    @Nullable
    private static ObjectAnimation findStaticIdle(@Nullable LuaAnimationStateMachine<?> stateMachine) {
        if (stateMachine == null) {
            return null;
        }
        Map<String, ObjectAnimation> prototypes =
                ((AnimationControllerAccessor) stateMachine.getAnimationController()).taczvr$getPrototypes();
        return prototypes == null ? null : prototypes.get("static_idle");
    }

    private static StaticNodePose getStaticNodePose(ObjectAnimation animation, String nodeName) {
        StaticNodePose pose = new StaticNodePose();
        List<ObjectAnimationChannel> channels = animation.getChannels().get(nodeName);
        if (channels == null) {
            return pose;
        }
        for (ObjectAnimationChannel channel : channels) {
            if (channel.content.keyframeTimeS == null || channel.content.keyframeTimeS.length == 0) {
                continue;
            }
            float[] values = channel.getResult(0.0F);
            if (channel.type == ObjectAnimationChannel.ChannelType.TRANSLATION && values.length >= 3) {
                pose.offsetX = values[0];
                pose.offsetY = -values[1];
                pose.offsetZ = values[2];
            } else if (channel.type == ObjectAnimationChannel.ChannelType.ROTATION) {
                float[] angles = values.length == 4 ? MathUtil.toEulerAngles(values) : values;
                if (angles.length >= 3) {
                    MathUtil.toQuaternion(angles[0], angles[1], angles[2], pose.rotation);
                }
            } else if (channel.type == ObjectAnimationChannel.ChannelType.SCALE && values.length >= 3) {
                pose.scaleX = values[0];
                pose.scaleY = values[1];
                pose.scaleZ = values[2];
            }
        }
        return pose;
    }

    private static boolean updateColdGripLatch(@Nullable ResourceLocation itemId,
                                               boolean manipulationActive,
                                               long nowNs) {
        if (itemId == null) {
            return false;
        }
        ColdGripLatch latch = COLD_GRIP_LATCHES.get(itemId);
        if (latch == null) {
            return false;
        }
        latch.seededAtNs = ensureColdGripTimerStarted(latch.seededAtNs, nowNs);
        boolean waitTimedOut = nowNs - latch.seededAtNs >= COLD_GRIP_TRACK_WAIT_NS;
        latch.phase = nextColdGripPhase(latch.phase, manipulationActive, waitTimedOut);
        if (latch.phase == ColdGripPhase.NONE) {
            COLD_GRIP_LATCHES.remove(itemId);
            return false;
        }
        return true;
    }

    static long ensureColdGripTimerStarted(long seededAtNs, long nowNs) {
        return seededAtNs == COLD_GRIP_TIMER_UNSTARTED_NS ? nowNs : seededAtNs;
    }

    private static void logResolution(ResourceLocation itemId,
                                      LrTacticalVrItemHandResolver.Resolution resolution,
                                      LrTacticalVrItemHandResolver.AuthoredHand selectedHand) {
        if (itemId == null || !LOGGED_RESOLUTIONS.add(itemId)) {
            return;
        }
        TaczVr.LOGGER.info(
                "LRTactical VR grip for {}: selected={}, inferred={}, source={}, confidence={}, split={}, "
                        + "leftWeight={}, rightWeight={}",
                itemId,
                selectedHand,
                resolution.primary(),
                resolution.source(),
                resolution.confidence(),
                resolution.split(),
                resolution.leftWeight(),
                resolution.rightWeight()
        );
    }

    private static void logStaticSeed(ResourceLocation itemId, @Nullable Vector3f staticPalm) {
        if (staticPalm == null || !LOGGED_STATIC_SEEDS.add(itemId)) {
            return;
        }
        TaczVr.LOGGER.info("LRTactical VR cold grip for {} seeded from static_idle", itemId);
    }

    private static void alignFallbackRoot(PoseStack poseStack, Matrix4f controllerPose,
                                          Matrix3f controllerNormal) {
        // LRTactical applies the same Z flip immediately before Bedrock rendering. Keep that model convention and
        // use the previous fixed root only when an LRTactical model omits the standard TACZ right-hand anchor.
        Matrix4f controllerToModel = new Matrix4f()
                .translate(0.0F, FALLBACK_CONTROLLER_LOCAL_GRIP_Y, 0.0F)
                .rotate(Axis.ZP.rotationDegrees(180.0F))
                .scale(TaczVrClientConfig.VR_LRTACTICAL_THROWABLE_SCALE.get().floatValue());

        PoseStack.Pose pose = poseStack.last();
        pose.pose().set(controllerPose).mul(controllerToModel);

        Matrix3f rotationOnly = new Matrix3f().rotateZ((float) Math.PI);
        pose.normal().set(controllerNormal).mul(rotationOnly);
    }

    private static boolean isFinite(Vector3f value) {
        return Float.isFinite(value.x) && Float.isFinite(value.y) && Float.isFinite(value.z);
    }

    private static final class StaticNodePose {
        private float offsetX;
        private float offsetY;
        private float offsetZ;
        private final Quaternionf rotation = new Quaternionf();
        private float scaleX = 1.0F;
        private float scaleY = 1.0F;
        private float scaleZ = 1.0F;
    }

    enum ColdGripPhase {
        NONE,
        WAITING_FOR_MANIPULATION,
        MANIPULATION_ACTIVE
    }

    private static final class ColdGripLatch {
        private ColdGripPhase phase;
        private long seededAtNs;

        private ColdGripLatch(ColdGripPhase phase, long seededAtNs) {
            this.phase = phase;
            this.seededAtNs = seededAtNs;
        }
    }

    private record StablePalm(BedrockAnimatedModel model,
                              LrTacticalVrItemHandResolver.AuthoredHand hand,
                              Vector3f position) {
    }
}
