package com.thedivisionisac.taczvr.client.render;

import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.thedivisionisac.taczvr.mixin.client.AnimationControllerAccessor;
import com.thedivisionisac.taczvr.client.vr.VrMuzzlePoseCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationChannel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.util.math.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Re-bases a TACZ first-person Bedrock model onto Vivecraft's controller while preserving TACZ animation state.
 * Static calibration entries are render-thread-only and weakly keyed by the model instance.
 */
public final class VrGunPoseTransform {
    private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0D);
    private static final float REGULAR_ARM_HALF_WIDTH = 0.375F;
    private static final float SLIM_ARM_HALF_WIDTH = 0.34375F;
    private static final float ARM_LENGTH = 0.75F;
    private static final Map<BedrockGunModel, StaticGunCalibration> STATIC_CALIBRATIONS = new WeakHashMap<>();

    private VrGunPoseTransform() {
    }

    public static boolean alignRightHand(PoseStack poseStack, BedrockGunModel model, ItemStack stack,
                                         @Nullable Matrix4f controllerPose,
                                         @Nullable Matrix3f controllerNormal) {
        if (controllerPose == null || controllerNormal == null) {
            return false;
        }

        BedrockPart anchor = GunHandAnchorResolver.findRightHand(model);
        if (anchor == null) {
            return false;
        }

        StaticGunCalibration calibration = STATIC_CALIBRATIONS.computeIfAbsent(
                model, ignored -> createStaticCalibration(model, anchor, findStaticIdle(stack))
        );
        boolean slimArm = Minecraft.getInstance().player != null
                && "slim".equals(Minecraft.getInstance().player.getModelName());
        float armHalfWidth = slimArm ? SLIM_ARM_HALF_WIDTH : REGULAR_ARM_HALF_WIDTH;
        Vector3f gripPosition = calibration.rightArmOriginTransform.transformPosition(
                new Vector3f(armHalfWidth, -ARM_LENGTH, 0.0F)
        );
        Matrix4f controllerGrip = new Matrix4f()
                .translate(
                        TaczVrClientConfig.VR_GUN_GRIP_OFFSET_X.get().floatValue(),
                        TaczVrClientConfig.VR_GUN_GRIP_OFFSET_Y.get().floatValue(),
                        TaczVrClientConfig.VR_GUN_GRIP_OFFSET_Z.get().floatValue()
                );
        // Animated hand bones are not allowed to steer the gun; they would feed recoil/reload animation back into aim.
        Matrix4f weaponAimBasis = new Matrix4f()
                .rotateX(TaczVrClientConfig.VR_GUN_AIM_ROTATION_X.get().floatValue() * DEGREES_TO_RADIANS)
                .rotateY(TaczVrClientConfig.VR_GUN_AIM_ROTATION_Y.get().floatValue() * DEGREES_TO_RADIANS)
                .rotateZ(TaczVrClientConfig.VR_GUN_AIM_ROTATION_Z.get().floatValue() * DEGREES_TO_RADIANS)
                .rotate(calibration.inverseMuzzleRotation);
        Matrix4f inverseGripPosition = new Matrix4f().translate(
                -gripPosition.x,
                -gripPosition.y,
                -gripPosition.z
        );
        Matrix4f modelToController = new Matrix4f(controllerGrip).mul(weaponAimBasis).mul(inverseGripPosition);
        if (calibration.muzzlePosition != null) {
            Vector3f scale = controllerPose.getScale(new Vector3f());
            Vector3f muzzleOffset = modelToController.transformPosition(new Vector3f(calibration.muzzlePosition));
            muzzleOffset.mul(scale.x);
            VrMuzzlePoseCache.update(stack, muzzleOffset);
        } else {
            VrMuzzlePoseCache.clear(stack);
        }
        PoseStack.Pose pose = poseStack.last();
        pose.pose().set(controllerPose).mul(modelToController);
        pose.normal().set(controllerNormal)
                .mul(new Matrix3f(weaponAimBasis));
        return true;
    }

    private static StaticGunCalibration createStaticCalibration(BedrockGunModel model, BedrockPart gripAnchor,
                                                                 @Nullable ObjectAnimation staticIdle) {
        Matrix4f rightArmOriginTransform = createStaticAnchorTransform(gripAnchor, staticIdle);
        Quaternionf inverseMuzzleRotation = new Quaternionf();
        Vector3f muzzlePosition = null;
        List<BedrockPart> muzzlePath = model.getMuzzleFlashPosPath();
        if (muzzlePath != null && !muzzlePath.isEmpty()) {
            Matrix4f muzzleTransform = createStaticAnchorTransform(muzzlePath.get(muzzlePath.size() - 1), staticIdle);
            muzzlePosition = muzzleTransform.getTranslation(new Vector3f());
            muzzleTransform.getUnnormalizedRotation(inverseMuzzleRotation)
                    .normalize()
                    .invert();
        }
        return new StaticGunCalibration(rightArmOriginTransform, inverseMuzzleRotation, muzzlePosition);
    }

    @Nullable
    private static ObjectAnimation findStaticIdle(ItemStack stack) {
        return TimelessAPI.getGunDisplay(stack)
                .flatMap(display -> Optional.ofNullable(display.getAnimationStateMachine()))
                .map(stateMachine -> stateMachine.getAnimationController())
                .map(controller -> ((AnimationControllerAccessor) controller).taczvr$getPrototypes())
                .map(prototypes -> prototypes.get("static_idle"))
                .orElse(null);
    }

    private static Matrix4f createStaticAnchorTransform(BedrockPart anchor,
                                                         @Nullable ObjectAnimation staticIdle) {
        Deque<BedrockPart> path = new ArrayDeque<>();
        for (BedrockPart part = anchor; part != null; part = part.getParent()) {
            path.addFirst(part);
        }

        Matrix4f transform = new Matrix4f();
        for (BedrockPart part : path) {
            StaticNodePose staticPose = getStaticNodePose(staticIdle, part.name);
            transform.translate(
                    staticPose.offsetX + part.x / 16.0F,
                    staticPose.offsetY + part.y / 16.0F,
                    staticPose.offsetZ + part.z / 16.0F
            );
            if (part.zRot != 0.0F) {
                transform.rotateZ(part.zRot);
            }
            if (part.yRot != 0.0F) {
                transform.rotateY(part.yRot);
            }
            if (part.xRot != 0.0F) {
                transform.rotateX(part.xRot);
            }
            transform.rotate(staticPose.rotation);
        }
        return transform;
    }

    private static StaticNodePose getStaticNodePose(@Nullable ObjectAnimation animation,
                                                     @Nullable String nodeName) {
        StaticNodePose pose = new StaticNodePose();
        if (animation == null || nodeName == null) {
            return pose;
        }
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
            }
        }
        return pose;
    }

    private static final class StaticNodePose {
        private float offsetX;
        private float offsetY;
        private float offsetZ;
        private final Quaternionf rotation = new Quaternionf();
    }

    private record StaticGunCalibration(Matrix4f rightArmOriginTransform, Quaternionf inverseMuzzleRotation,
                                        @Nullable Vector3f muzzlePosition) {
    }
}
