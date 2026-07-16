package com.thedivisionisac.taczvr.client.compat;

import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.client.vr.VrShotPose;

import java.util.Optional;

/**
 * Circuit breaker that permanently falls back to the no-op bridge after the first API/linkage failure.
 */
final class SafeVivecraftBridge implements VivecraftBridge {
    private VivecraftBridge delegate;
    private boolean failureLogged;

    SafeVivecraftBridge(VivecraftBridge delegate) {
        this.delegate = delegate;
    }

    @Override
    public String bridgeName() {
        return this.delegate.bridgeName();
    }

    @Override
    public boolean isVrActive() {
        try {
            return this.delegate.isVrActive();
        } catch (LinkageError | RuntimeException error) {
            this.disable("VR state query", error);
            return false;
        }
    }

    @Override
    public boolean isLeftHanded() {
        try {
            return this.delegate.isLeftHanded();
        } catch (LinkageError | RuntimeException error) {
            this.disable("left-handed mode query", error);
            return false;
        }
    }

    @Override
    public Optional<VrShotPose> getMainHandShotPose() {
        try {
            return this.delegate.getMainHandShotPose();
        } catch (LinkageError | RuntimeException error) {
            this.disable("main-hand pose query", error);
            return Optional.empty();
        }
    }

    @Override
    public Optional<VrShotPose> getMainHandRenderPose() {
        try {
            return this.delegate.getMainHandRenderPose();
        } catch (LinkageError | RuntimeException error) {
            this.disable("main-hand render pose query", error);
            return Optional.empty();
        }
    }

    @Override
    public void triggerMainHandHaptic(float durationSeconds) {
        try {
            this.delegate.triggerMainHandHaptic(durationSeconds);
        } catch (LinkageError | RuntimeException error) {
            this.disable("haptic request", error);
        }
    }

    private void disable(String operation, Throwable error) {
        this.delegate = VivecraftBridge.unavailable();
        if (!this.failureLogged) {
            this.failureLogged = true;
            TaczVr.LOGGER.warn("Disabling the Vivecraft bridge after a failed {}.", operation, error);
        }
    }
}
