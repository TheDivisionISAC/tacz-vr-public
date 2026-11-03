package com.thedivisionisac.taczvr.client.compat;

import com.thedivisionisac.taczvr.client.vr.VrEyeToHeadTransform;
import com.thedivisionisac.taczvr.client.vr.VrRenderTrackingSnapshot;
import com.thedivisionisac.taczvr.client.vr.VrShotPose;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Allocation-free fallback used when Vivecraft is absent or its bridge has failed.
 */
enum NoopVivecraftBridge implements VivecraftBridge {
    INSTANCE;

    @Override
    public String bridgeName() {
        return "none";
    }

    @Override
    public boolean isVrActive() {
        return false;
    }

    @Override
    public boolean isLeftHanded() {
        return false;
    }

    @Override
    public Optional<VrShotPose> getMainHandShotPose() {
        return Optional.empty();
    }

    @Override
    public Optional<VrShotPose> getMainHandRenderPose() {
        return Optional.empty();
    }

    @Override
    public Optional<VrRenderTrackingSnapshot> getRenderTrackingSnapshot() {
        return Optional.empty();
    }

    @Override
    public Optional<VrEyeToHeadTransform> getCurrentEyeToHeadTransform() {
        return Optional.empty();
    }

    @Override
    public OptionalLong getRenderFrameIndex() {
        return OptionalLong.empty();
    }

    @Override
    public String getRenderPassName() {
        return "none";
    }

    @Override
    public void triggerMainHandHaptic(float durationSeconds) {
    }
}
