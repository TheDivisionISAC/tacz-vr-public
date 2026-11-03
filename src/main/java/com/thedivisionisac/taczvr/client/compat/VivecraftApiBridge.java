package com.thedivisionisac.taczvr.client.compat;

import com.thedivisionisac.taczvr.client.vr.VrEyeToHeadTransform;
import com.thedivisionisac.taczvr.client.vr.VrRenderTrackingSnapshot;
import com.thedivisionisac.taczvr.client.vr.VrShotPose;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Version-scoped Vivecraft 1.3.x adapter. Tracking stays on the public API; the client holder is read only for its
 * render-frame index, current pass, and per-eye pose so stereo views can be converted back to one HMD-centered frame.
 */
final class VivecraftApiBridge implements VivecraftBridge {
    private final VRClientAPI api;

    VivecraftApiBridge() {
        this.api = VRClientAPI.instance();
    }

    @Override
    public String bridgeName() {
        return "vivecraft-api";
    }

    @Override
    public boolean isVrActive() {
        return this.api.isVRActive();
    }

    @Override
    public boolean isLeftHanded() {
        return this.api.isLeftHanded();
    }

    @Override
    public Optional<VrShotPose> getMainHandShotPose() {
        if (!this.api.isVRActive()) {
            return Optional.empty();
        }
        return this.getMainHandPose(this.api.getPreTickWorldPose());
    }

    @Override
    public Optional<VrShotPose> getMainHandRenderPose() {
        if (!this.api.isVRActive()) {
            return Optional.empty();
        }
        return this.getMainHandPose(this.api.getWorldRenderPose());
    }

    @Override
    public Optional<VrRenderTrackingSnapshot> getRenderTrackingSnapshot() {
        if (!this.api.isVRActive()) {
            return Optional.empty();
        }
        VRPose pose = this.api.getWorldRenderPose();
        if (pose == null) {
            return Optional.empty();
        }
        VRBodyPartData hand = pose.getMainHand();
        VRBodyPartData head = pose.getHead();
        if (hand == null || head == null) {
            return Optional.empty();
        }
        return VrRenderTrackingSnapshot.validated(
                hand.getPos(), hand.getDir(), hand.getRotation(),
                head.getPos(), head.getDir(), head.getRotation()
        );
    }

    @Override
    public Optional<VrEyeToHeadTransform> getCurrentEyeToHeadTransform() {
        if (!this.api.isVRActive()) {
            return Optional.empty();
        }
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        RenderPass pass = holder.currentPass;
        VRPose pose = this.api.getWorldRenderPose();
        if (pass == null || !RenderPass.isFirstPerson(pass) || pose == null || holder.vrPlayer == null) {
            return Optional.empty();
        }
        VRData worldPose = holder.vrPlayer.getVRDataWorld();
        VRBodyPartData head = pose.getHead();
        VRData.VRDevicePose eye = worldPose == null ? null : worldPose.getEye(pass);
        if (head == null || head.getRotation() == null || eye == null) {
            return Optional.empty();
        }
        Vec3 eyePosition = eye.getPosition();
        Vec3 headPosition = head.getPos();
        if (eyePosition == null || headPosition == null) {
            return Optional.empty();
        }
        Vector3f worldOffset = new Vector3f(
                (float) (eyePosition.x - headPosition.x),
                (float) (eyePosition.y - headPosition.y),
                (float) (eyePosition.z - headPosition.z)
        );
        Quaternionf worldToHead = new Quaternionf(head.getRotation()).normalize().conjugate();
        Vector3f headOffset = worldToHead.transform(worldOffset);
        if (!headOffset.isFinite()) {
            return Optional.empty();
        }
        Quaternionf eyeWorldRotation = eye.getMatrix()
                .getUnnormalizedRotation(new Quaternionf())
                .normalize();
        Quaternionf eyeRotationFromHead = new Quaternionf(worldToHead)
                .mul(eyeWorldRotation)
                .normalize();
        return VrEyeToHeadTransform.validated(
                new Vec3(headOffset.x, headOffset.y, headOffset.z),
                eyeRotationFromHead
        );
    }

    @Override
    public OptionalLong getRenderFrameIndex() {
        return OptionalLong.of(ClientDataHolderVR.getInstance().frameIndex);
    }

    @Override
    public String getRenderPassName() {
        return String.valueOf(ClientDataHolderVR.getInstance().currentPass);
    }

    private Optional<VrShotPose> getMainHandPose(VRPose pose) {
        if (pose == null) {
            return Optional.empty();
        }
        VRBodyPartData hand = pose.getMainHand();
        if (hand == null) {
            return Optional.empty();
        }
        return VrShotPose.validated(hand.getPos(), hand.getDir());
    }

    @Override
    public void triggerMainHandHaptic(float durationSeconds) {
        if (durationSeconds > 0.0F) {
            this.api.triggerHapticPulse(VRBodyPart.MAIN_HAND, durationSeconds);
        }
    }
}
