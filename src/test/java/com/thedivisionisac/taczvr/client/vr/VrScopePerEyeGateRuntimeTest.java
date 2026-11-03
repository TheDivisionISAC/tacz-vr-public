package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrScopePerEyeGateRuntimeTest {
    private static final long FRAME_NANOS = 10_000_000L;
    private static final double TOLERANCE = 1.0E-9D;
    private static final ResourceLocation GUN_ID = ResourceLocation.tryParse("tacz:test_gun");

    @Test
    void rightEyeAlignmentCannotOpenLeftAperture() {
        VrScopePerEyeGateRuntime runtime = new VrScopePerEyeGateRuntime();
        Object model = new Object();
        long time = 1L;

        runtime.record("RIGHT", aligned(1L), eyeBox(1L), null, model, profile(), time);
        runtime.record("RIGHT", aligned(2L), eyeBox(2L), null, model, profile(), time + FRAME_NANOS);

        assertTrue(runtime.progressFor("RIGHT", model, GUN_ID, 2L) > 0.0D);
        assertEquals(0.0D, runtime.progressFor("LEFT", model, GUN_ID, 2L), TOLERANCE);
    }

    @Test
    void leftAndRightMaintainIndependentGateStates() {
        VrScopePerEyeGateRuntime runtime = new VrScopePerEyeGateRuntime();
        Object model = new Object();
        long time = 1L;

        runtime.record("LEFT", oblique(1L), eyeBox(1L), null, model, profile(), time);
        runtime.record("RIGHT", aligned(1L), eyeBox(1L), null, model, profile(), time);
        runtime.record("LEFT", oblique(2L), eyeBox(2L), null, model, profile(), time + FRAME_NANOS);
        runtime.record("RIGHT", aligned(2L), eyeBox(2L), null, model, profile(), time + FRAME_NANOS);

        assertEquals(0.0D, runtime.progressFor("LEFT", model, GUN_ID, 2L), TOLERANCE);
        assertTrue(runtime.progressFor("RIGHT", model, GUN_ID, 2L) > 0.0D);
    }

    @Test
    void nonEyePassFailsClosedWithoutClearingEyeProgress() {
        VrScopePerEyeGateRuntime runtime = new VrScopePerEyeGateRuntime();
        Object model = new Object();
        long time = 1L;

        runtime.record("RIGHT", aligned(1L), eyeBox(1L), null, model, profile(), time);
        runtime.record("RIGHT", aligned(2L), eyeBox(2L), null, model, profile(), time + FRAME_NANOS);

        assertEquals(0.0D, runtime.progressFor("CENTER", model, GUN_ID, 2L), TOLERANCE);
        assertTrue(runtime.progressFor("RIGHT", model, GUN_ID, 2L) > 0.0D);
    }

    @Test
    void resetClosesBothEyes() {
        VrScopePerEyeGateRuntime runtime = new VrScopePerEyeGateRuntime();
        Object model = new Object();
        long time = 1L;
        runtime.record("LEFT", aligned(1L), eyeBox(1L), null, model, profile(), time);
        runtime.record("RIGHT", aligned(1L), eyeBox(1L), null, model, profile(), time);
        runtime.record("LEFT", aligned(2L), eyeBox(2L), null, model, profile(), time + FRAME_NANOS);
        runtime.record("RIGHT", aligned(2L), eyeBox(2L), null, model, profile(), time + FRAME_NANOS);

        runtime.reset();

        assertEquals(0.0D, runtime.progressFor("LEFT", model, GUN_ID, 2L), TOLERANCE);
        assertEquals(0.0D, runtime.progressFor("RIGHT", model, GUN_ID, 2L), TOLERANCE);
    }

    @Test
    void nonEyePassCannotReadCircleCenter() {
        VrScopePerEyeGateRuntime runtime = new VrScopePerEyeGateRuntime();
        Object model = new Object();
        runtime.record("RIGHT", aligned(1L), eyeBox(1L), null, model, profile(), 1L);

        assertEquals(
                VrScopePerEyeGateRuntime.OptionalCenter.empty(),
                runtime.circleCenterFor("CENTER", model, GUN_ID, 1L)
        );
    }

    private static VrScopeAlignmentGateProfile profile() {
        return VrScopeAlignmentGateTuning.observationProfile();
    }

    private static VrScopeAlignmentSample aligned(long frameIndex) {
        return VrScopeAlignmentGateProfileTest.sample(frameIndex, 0.11D, 0.035D, 20.0D, 5.0D, 20.0D);
    }

    private static VrScopeAlignmentSample oblique(long frameIndex) {
        return VrScopeAlignmentGateProfileTest.sample(frameIndex, 0.11D, 0.20D, 70.0D, 40.0D, 80.0D);
    }

    private static VrScopeAuthorEyeBoxSample eyeBox(long frameIndex) {
        VrScopeAuthorEyeBoxSample source = VrScopeAuthorEyeBoxGateTest.eyeBox(frameIndex, 0.0D);
        return new VrScopeAuthorEyeBoxSample(
                source.frameIndex(),
                source.gunId(),
                source.scopeId(),
                source.viewIndex(),
                0.12D,
                0.12D,
                0.0D,
                source.authorEyeLateralError(),
                source.apertureRadiusX(),
                source.apertureRadiusY(),
                source.authorConeHalfAngleDegrees(),
                source.authorRayAxisAngleDegrees(),
                source.authorViewAxisAngleDegrees(),
                source.forwardIntersection(),
                source.pupilU(),
                source.pupilV(),
                source.pupilRadius(),
                source.pupilMargin(),
                source.insideAperture()
        );
    }
}
