package com.thedivisionisac.taczvr.client.compat.lrtactical;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thedivisionisac.taczvr.client.TaczVrClient;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client_vr.ClientDataHolderVR;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Keeps both physical controller bases for each Vivecraft eye pass. Vivecraft's merged hand-render method can
 * associate the main inventory stack with the opposite {@link InteractionHand} value, so item identity is not a
 * reliable substitute for the physical controller channel.
 */
public final class LrTacticalVrControllerPoseCache {
    private static final Map<RenderPass, PassPose> MAIN_HAND_POSES = new EnumMap<>(RenderPass.class);
    private static final Map<RenderPass, PassPose> SUPPORT_HAND_POSES = new EnumMap<>(RenderPass.class);

    private LrTacticalVrControllerPoseCache() {
    }

    public static void capturePhysicalHands(InteractionHand hand, PoseStack poseStack, ItemStack mainHandStack) {
        if (!LrTacticalVrItemSupport.isRenderSupported(mainHandStack)
                || !TaczVrClient.vivecraft().isVrActive()) {
            return;
        }
        InteractionHand physicalMainHand = TaczVrClient.vivecraft().isLeftHanded()
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;

        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        RenderPass pass = holder.currentPass;
        if (pass == null || !RenderPass.isFirstPerson(pass)) {
            return;
        }
        Map<RenderPass, PassPose> poses = hand == physicalMainHand ? MAIN_HAND_POSES : SUPPORT_HAND_POSES;
        poses.put(pass, new PassPose(
                holder.frameIndex,
                new Matrix4f(poseStack.last().pose()),
                new Matrix3f(poseStack.last().normal())
        ));
    }

    @Nullable
    public static ControllerPose getPhysicalMainHand() {
        return getCurrentPose(MAIN_HAND_POSES);
    }

    @Nullable
    public static ControllerPose getPhysicalSupportHand() {
        return getCurrentPose(SUPPORT_HAND_POSES);
    }

    @Nullable
    public static ControllerPose getAuthoredHand(LrTacticalVrItemHandResolver.AuthoredHand authoredHand) {
        if (authoredHand == LrTacticalVrItemHandResolver.AuthoredHand.UNKNOWN) {
            return getPhysicalMainHand();
        }
        boolean authoredHandIsMain = authoredHand
                == LrTacticalVrHandRoleResolver.physicalMainAuthoredHand(
                        TaczVrClient.vivecraft().isLeftHanded()
                );
        return authoredHandIsMain ? getPhysicalMainHand() : getPhysicalSupportHand();
    }

    @Nullable
    private static ControllerPose getCurrentPose(Map<RenderPass, PassPose> poses) {
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        PassPose pose = poses.get(holder.currentPass);
        if (pose == null || pose.frameIndex < holder.frameIndex - 1L || pose.frameIndex > holder.frameIndex) {
            return null;
        }
        return new ControllerPose(new Matrix4f(pose.pose), new Matrix3f(pose.normal));
    }

    public static void reset() {
        MAIN_HAND_POSES.clear();
        SUPPORT_HAND_POSES.clear();
    }

    public record ControllerPose(Matrix4f pose, Matrix3f normal) {
    }

    private record PassPose(long frameIndex, Matrix4f pose, Matrix3f normal) {
    }
}
