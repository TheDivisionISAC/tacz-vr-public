package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Verifies stereo-frame de-duplication, read-only shot resolution, cache expiry, source isolation, and reset. */
class VrAimStabilizationStateTest {
    private static final long FRAME_NANOS = 11_111_111L;
    private static final long CACHE_AGE_NANOS = 250_000_000L;
    private static final double STRENGTH = 0.92D;

    @Test
    void stereoEyesReuseExactlyOneStabilizedFrame() {
        VrAimStabilizationState state = new VrAimStabilizationState();
        VrAimStabilizationState control = new VrAimStabilizationState();
        VrShotPose first = pose(0.0D, 0.0D);
        VrShotPose second = pose(0.001D, 0.8D);
        VrShotPose third = pose(-0.001D, -0.8D);

        state.updateRenderPose("gun", 10L, first, STRENGTH, 0.02D, 120.0D, 1.2D, 1L);
        VrShotPose leftEye = state.updateRenderPose(
                "gun", 11L, second, STRENGTH, 0.02D, 120.0D, 1.2D, 1L + FRAME_NANOS
        );
        VrShotPose rightEye = state.updateRenderPose(
                "gun", 11L, second, STRENGTH, 0.02D, 120.0D, 1.2D, 1L + FRAME_NANOS + 1L
        );
        VrShotPose actualThird = state.updateRenderPose(
                "gun", 12L, third, STRENGTH, 0.02D, 120.0D, 1.2D, 1L + 2L * FRAME_NANOS
        );

        control.updateRenderPose("gun", 10L, first, STRENGTH, 0.02D, 120.0D, 1.2D, 1L);
        control.updateRenderPose("gun", 11L, second, STRENGTH, 0.02D, 120.0D, 1.2D, 1L + FRAME_NANOS);
        VrShotPose expectedThird = control.updateRenderPose(
                "gun", 12L, third, STRENGTH, 0.02D, 120.0D, 1.2D, 1L + 2L * FRAME_NANOS
        );

        assertSame(leftEye, rightEye);
        assertEquals(expectedThird, actualThird);
    }

    @Test
    void divergentStereoDuplicateCannotAdvanceFiltersTwice() {
        VrAimStabilizationState state = new VrAimStabilizationState();
        VrShotPose leftTracking = pose(0.001D, 0.8D);
        VrShotPose divergentRightTracking = pose(-0.02D, -12.0D);

        VrShotPose leftEye = state.updateRenderPose(
                "gun", 30L, leftTracking, STRENGTH, 0.02D, 120.0D, 1.2D, 1L
        );
        VrShotPose rightEye = state.updateRenderPose(
                "gun", 30L, divergentRightTracking, STRENGTH, 0.02D, 120.0D, 1.2D, 2L
        );

        assertSame(leftEye, rightEye);
    }

    @Test
    void olderTickShotPoseReadsRenderSnapshotWithoutBackfeedingFilters() {
        VrAimStabilizationState state = new VrAimStabilizationState();
        VrAimStabilizationState control = new VrAimStabilizationState();
        VrShotPose first = pose(0.0D, 0.0D);
        VrShotPose render = pose(0.001D, 1.0D);
        VrShotPose oldTickShot = pose(-0.03D, -25.0D);
        long renderTime = 1L + FRAME_NANOS;

        state.updateRenderPose("gun", 20L, first, STRENGTH, 0.02D, 120.0D, 1.2D, 1L);
        VrShotPose snapshot = state.updateRenderPose(
                "gun", 21L, render, STRENGTH, 0.02D, 120.0D, 1.2D, renderTime
        );
        VrShotPose shot = state.resolveShotPose("gun", oldTickShot, renderTime + 1L, CACHE_AGE_NANOS);
        VrShotPose actualNext = state.updateRenderPose(
                "gun", 22L, pose(-0.001D, -1.0D), STRENGTH, 0.02D, 120.0D, 1.2D,
                renderTime + FRAME_NANOS
        );

        control.updateRenderPose("gun", 20L, first, STRENGTH, 0.02D, 120.0D, 1.2D, 1L);
        control.updateRenderPose("gun", 21L, render, STRENGTH, 0.02D, 120.0D, 1.2D, renderTime);
        VrShotPose expectedNext = control.updateRenderPose(
                "gun", 22L, pose(-0.001D, -1.0D), STRENGTH, 0.02D, 120.0D, 1.2D,
                renderTime + FRAME_NANOS
        );

        assertSame(snapshot, shot);
        assertEquals(expectedNext, actualNext);
    }

    @Test
    void expiredOrDifferentSourceFallsBackToRawShotPose() {
        VrAimStabilizationState state = new VrAimStabilizationState();
        VrShotPose render = pose(0.0D, 0.0D);
        VrShotPose rawShot = pose(1.0D, 20.0D);
        state.updateRenderPose("first", 1L, render, STRENGTH, 0.02D, 120.0D, 1.2D, 1L);

        assertSame(rawShot, state.resolveShotPose("second", rawShot, 2L, CACHE_AGE_NANOS));
        assertSame(rawShot, state.resolveShotPose("first", rawShot, 2L + CACHE_AGE_NANOS, CACHE_AGE_NANOS));
    }

    @Test
    void resetDropsSnapshotAndReseedsFromRawTracking() {
        VrAimStabilizationState state = new VrAimStabilizationState();
        VrShotPose beforeReset = pose(0.0D, 0.0D);
        VrShotPose rawShot = pose(1.0D, 20.0D);
        state.updateRenderPose("gun", 1L, beforeReset, STRENGTH, 0.02D, 120.0D, 1.2D, 1L);

        state.reset();
        VrShotPose fallback = state.resolveShotPose("gun", rawShot, 2L, CACHE_AGE_NANOS);
        VrShotPose reseeded = state.updateRenderPose(
                "gun", 1L, rawShot, STRENGTH, 0.02D, 120.0D, 1.2D, 3L
        );

        assertSame(rawShot, fallback);
        assertNotSame(rawShot, reseeded);
        assertEquals(rawShot, reseeded);
    }

    private static VrShotPose pose(double x, double yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        return new VrShotPose(
                new Vec3(x, 1.5D, 0.0D),
                new Vec3(Math.sin(radians), 0.0D, Math.cos(radians))
        );
    }
}
