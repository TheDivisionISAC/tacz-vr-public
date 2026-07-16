package com.thedivisionisac.taczvr.client.compat;

import com.thedivisionisac.taczvr.client.vr.VrShotPose;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;

import java.util.Optional;

/**
 * Direct adapter to Vivecraft 1.3.x public client APIs. It is instantiated only after ModList confirms Vivecraft.
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
