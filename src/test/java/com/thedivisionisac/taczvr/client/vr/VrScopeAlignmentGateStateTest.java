package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies first-frame closure, stereo de-duplication, hysteresis, frame-rate-independent smoothing, and every
 * fail-closed reset boundary of the render-thread gate state.
 */
class VrScopeAlignmentGateStateTest {
    private static final long FRAME_100_HZ_NANOS = 10_000_000L;
    private static final double TOLERANCE = 1.0E-9D;

    @Test
    void firstFrameStaysClosedAndAlignedFramesAttackSmoothly() {
        VrScopeAlignmentGateState state = new VrScopeAlignmentGateState();
        VrScopeAlignmentGateProfile profile = VrScopeAlignmentGateProfileTest.profile();
        long time = 1L;

        assertEquals(0.0D, state.update(aligned(1L), profile, time), TOLERANCE);
        time += FRAME_100_HZ_NANOS;
        double firstAttack = state.update(aligned(2L), profile, time);
        time += FRAME_100_HZ_NANOS;
        double secondAttack = state.update(aligned(3L), profile, time);

        assertTrue(state.isLatched());
        assertTrue(firstAttack > 0.0D);
        assertTrue(secondAttack > firstAttack);
        assertTrue(secondAttack < 1.0D);
    }

    @Test
    void hysteresisKeepsIntermediateScoreLatchedUntilExitBoundary() {
        VrScopeAlignmentGateState state = new VrScopeAlignmentGateState();
        VrScopeAlignmentGateProfile profile = VrScopeAlignmentGateProfileTest.profile();
        long time = 1L;
        state.update(aligned(1L), profile, time);
        time += FRAME_100_HZ_NANOS;
        state.update(aligned(2L), profile, time);

        time += FRAME_100_HZ_NANOS;
        double intermediate = state.update(withOpticalLateral(3L, 0.06D), profile, time);
        assertTrue(state.isLatched());
        assertTrue(intermediate > 0.0D);

        time += FRAME_100_HZ_NANOS;
        double releasing = state.update(withOpticalLateral(4L, 0.10D), profile, time);
        assertFalse(state.isLatched());
        assertTrue(releasing > 0.0D, "valid exit should use the configured release smoothing");
        assertTrue(releasing < intermediate);
    }

    @Test
    void duplicateStereoFrameReturnsExactlyOneSharedProgress() {
        VrScopeAlignmentGateState state = new VrScopeAlignmentGateState();
        VrScopeAlignmentGateProfile profile = VrScopeAlignmentGateProfileTest.profile();
        long time = 1L;
        state.update(aligned(1L), profile, time);
        time += FRAME_100_HZ_NANOS;
        double left = state.update(aligned(2L), profile, time);

        double right = state.update(withOpticalLateral(2L, 0.12D), profile, time + 1_000_000L);

        assertEquals(left, right, TOLERANCE);
        assertTrue(state.isLatched());
    }

    @Test
    void sourceProfileRegressionAndLongGapResetClosed() {
        VrScopeAlignmentGateState state = new VrScopeAlignmentGateState();
        VrScopeAlignmentGateProfile profile = VrScopeAlignmentGateProfileTest.profile();
        VrScopeAlignmentGateProfile revisedProfile = new VrScopeAlignmentGateProfile(
                profile.eyeRelief(),
                profile.opticalLateralOffset(),
                profile.viewCenterAngleDegrees(),
                profile.opticalAxisAngleDegrees(),
                profile.coaxialAngleDegrees(),
                profile.enterScore(),
                profile.exitScore(),
                0.10D,
                profile.releaseSeconds(),
                profile.resetGapSeconds()
        );
        long time = 1L;
        state.update(aligned(10L), profile, time);
        time += FRAME_100_HZ_NANOS;
        state.update(aligned(11L), profile, time);
        assertTrue(state.progress() > 0.0D);

        assertEquals(0.0D, state.update(withScope(aligned(12L), "tacz:other_scope"), profile,
                time + FRAME_100_HZ_NANOS), TOLERANCE);
        state.update(withScope(aligned(13L), "tacz:other_scope"), profile,
                time + 2L * FRAME_100_HZ_NANOS);
        assertTrue(state.progress() > 0.0D);
        assertEquals(0.0D, state.update(withScope(aligned(14L), "tacz:other_scope"), revisedProfile,
                time + 3L * FRAME_100_HZ_NANOS), TOLERANCE);

        state.update(aligned(20L), profile, time + 2L * FRAME_100_HZ_NANOS);
        state.update(aligned(21L), profile, time + 3L * FRAME_100_HZ_NANOS);
        assertEquals(0.0D, state.update(aligned(19L), profile,
                time + 4L * FRAME_100_HZ_NANOS), TOLERANCE);
        state.update(aligned(30L), profile, time + 5L * FRAME_100_HZ_NANOS);
        assertEquals(0.0D, state.update(aligned(31L), profile,
                time + 5L * FRAME_100_HZ_NANOS + 300_000_000L), TOLERANCE);
    }

    @Test
    void monotonicClockRegressionResetsClosed() {
        VrScopeAlignmentGateState state = new VrScopeAlignmentGateState();
        VrScopeAlignmentGateProfile profile = VrScopeAlignmentGateProfileTest.profile();
        long time = 100_000_000L;
        state.update(aligned(1L), profile, time);
        state.update(aligned(2L), profile, time + FRAME_100_HZ_NANOS);
        assertTrue(state.progress() > 0.0D);

        assertEquals(0.0D, state.update(aligned(3L), profile, time), TOLERANCE);
        assertFalse(state.isLatched());
    }

    @Test
    void missingOrInvalidFactsCloseImmediately() {
        VrScopeAlignmentGateState state = new VrScopeAlignmentGateState();
        VrScopeAlignmentGateProfile profile = VrScopeAlignmentGateProfileTest.profile();
        long time = 1L;
        state.update(aligned(1L), profile, time);
        state.update(aligned(2L), profile, time + FRAME_100_HZ_NANOS);
        assertTrue(state.progress() > 0.0D);

        assertEquals(0.0D, state.update(null, profile, time + 2L * FRAME_100_HZ_NANOS), TOLERANCE);
        state.update(aligned(3L), profile, time + 3L * FRAME_100_HZ_NANOS);
        state.update(aligned(4L), profile, time + 4L * FRAME_100_HZ_NANOS);
        assertEquals(0.0D, state.update(
                VrScopeAlignmentGateProfileTest.sample(5L, 0.20D, Double.NaN, 2.0D, 2.0D, 2.0D),
                profile,
                time + 5L * FRAME_100_HZ_NANOS
        ), TOLERANCE);
    }

    @Test
    void exponentialResponseIsFrameRateIndependent() {
        VrScopeAlignmentGateProfile profile = VrScopeAlignmentGateProfileTest.profile();
        double sixtyHz = progressAfterOneSecond(profile, 60);
        double ninetyHz = progressAfterOneSecond(profile, 90);
        double oneTwentyHz = progressAfterOneSecond(profile, 120);

        assertEquals(sixtyHz, ninetyHz, 1.0E-8D);
        assertEquals(sixtyHz, oneTwentyHz, 1.0E-8D);
        assertTrue(sixtyHz > 0.999D);
    }

    private static double progressAfterOneSecond(VrScopeAlignmentGateProfile profile, int framesPerSecond) {
        VrScopeAlignmentGateState state = new VrScopeAlignmentGateState();
        long frameNanos = 1_000_000_000L / framesPerSecond;
        long time = 1L;
        state.update(aligned(1L), profile, time);
        for (int frame = 1; frame <= framesPerSecond; frame++) {
            time += frameNanos;
            state.update(aligned(frame + 1L), profile, time);
        }
        return state.progress();
    }

    private static VrScopeAlignmentSample aligned(long frameIndex) {
        return VrScopeAlignmentGateProfileTest.sample(frameIndex, 0.20D, 0.01D, 2.0D, 2.0D, 2.0D);
    }

    private static VrScopeAlignmentSample withOpticalLateral(long frameIndex, double opticalLateralOffset) {
        return VrScopeAlignmentGateProfileTest.sample(
                frameIndex,
                0.20D,
                opticalLateralOffset,
                2.0D,
                2.0D,
                2.0D
        );
    }

    private static VrScopeAlignmentSample withScope(VrScopeAlignmentSample sample, String scopeId) {
        return new VrScopeAlignmentSample(
                sample.frameIndex(),
                sample.gunId(),
                ResourceLocation.tryParse(scopeId),
                sample.viewIndex(),
                sample.lensPosition(),
                sample.opticalAxis(),
                sample.lensDistance(),
                sample.viewForwardDistance(),
                sample.viewLateralOffset(),
                sample.opticalAxialDistance(),
                sample.opticalLateralOffset(),
                sample.viewCenterAngleDegrees(),
                sample.opticalAxisAngleDegrees(),
                sample.coaxialAngleDegrees()
        );
    }
}
