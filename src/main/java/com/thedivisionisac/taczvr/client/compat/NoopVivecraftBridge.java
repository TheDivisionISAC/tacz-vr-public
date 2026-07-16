package com.thedivisionisac.taczvr.client.compat;

import com.thedivisionisac.taczvr.client.vr.VrShotPose;

import java.util.Optional;

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
    public void triggerMainHandHaptic(float durationSeconds) {
    }
}
