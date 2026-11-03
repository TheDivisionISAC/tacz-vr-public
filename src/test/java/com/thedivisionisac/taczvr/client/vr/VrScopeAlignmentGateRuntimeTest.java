package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies current-frame/model/gun ownership and fail-closed resets for the render-thread scope-gate runtime.
 */
class VrScopeAlignmentGateRuntimeTest {
    private static final long FRAME_NANOS = 10_000_000L;
    private static final double TOLERANCE = 1.0E-9D;
    private static final ResourceLocation GUN_ID = ResourceLocation.tryParse("tacz:test_gun");

    @Test
    void matchingCurrentFrameReturnsSmoothedProgress() {
        VrScopeAlignmentGateRuntime runtime = new VrScopeAlignmentGateRuntime();
        Object model = new Object();
        long time = 1L;

        runtime.record(aligned(1L), eyeBox(1L, 0.0D), null, model, profile(), time);
        assertEquals(
                VrScopeAuthorEyeBoxGate.MAX_ACQUISITION_RADIUS_SCALE,
                runtime.progressFor(model, GUN_ID, 1L),
                TOLERANCE
        );
        runtime.record(aligned(2L), eyeBox(2L, 0.0D), null, model, profile(), time + FRAME_NANOS);

        assertTrue(runtime.progressFor(model, GUN_ID, 2L) > 0.0D);
    }

    @Test
    void weakestUsableComponentDoesNotMultiplyPartialFalloffs() {
        VrScopeAlignmentGateRuntime runtime = new VrScopeAlignmentGateRuntime();
        Object model = new Object();
        long time = 1L;

        runtime.record(aligned(1L), eyeBox(1L, 0.0D), null, model, profile(), time);
        runtime.record(aligned(2L), eyeBox(2L, 0.75D), null, model, profile(), time + 100_000_000L);
        double tapered = runtime.progressFor(model, GUN_ID, 2L);

        runtime.reset();
        runtime.record(aligned(1L), eyeBox(1L, 0.0D), null, model, profile(), time);
        runtime.record(aligned(2L), eyeBox(2L, 0.0D), null, model, profile(), time + 100_000_000L);
        double centered = runtime.progressFor(model, GUN_ID, 2L);

        assertEquals(0.5D, tapered, TOLERANCE);
        assertTrue(centered > tapered);
    }

    @Test
    void acquisitionZoneShowsOnlyASmallCurrentFramePreview() {
        VrScopeAlignmentGateRuntime runtime = new VrScopeAlignmentGateRuntime();
        Object model = new Object();
        long time = 1L;

        runtime.record(aligned(1L), eyeBox(1L, 1.20D), null, model, profile(), time);

        assertEquals(0.075D, runtime.progressFor(model, GUN_ID, 1L), TOLERANCE);
        assertEquals(0.0D, runtime.renderActivationProgressFor(model, GUN_ID, 1L), TOLERANCE);
    }

    @Test
    void nearEyeHardCloseBypassesReleaseSmoothingAndAcquisitionPreview() {
        VrScopeAlignmentGateRuntime runtime = new VrScopeAlignmentGateRuntime();
        Object model = new Object();
        long time = 1L;

        runtime.record(aligned(1L), eyeBox(1L, 0.0D), null, model, profile(), time);
        runtime.record(aligned(2L), eyeBox(2L, 0.0D), null, model, profile(), time + FRAME_NANOS);
        assertTrue(runtime.progressFor(model, GUN_ID, 2L) > 0.0D);

        VrScopeAlignmentSample tooClose = VrScopeAlignmentGateProfileTest.sample(
                3L,
                0.04D,
                0.0D,
                0.0D,
                0.0D,
                0.0D
        );
        runtime.record(tooClose, eyeBox(3L, 0.0D), null, model, profile(), time + 2L * FRAME_NANOS);

        assertEquals(0.0D, runtime.progressFor(model, GUN_ID, 3L), TOLERANCE);
    }

    @Test
    void missingAuthorGeometryFailsClosed() {
        VrScopeAlignmentGateRuntime runtime = new VrScopeAlignmentGateRuntime();
        Object model = new Object();
        long time = 1L;

        runtime.record(aligned(1L), null, null, model, profile(), time);
        runtime.record(aligned(2L), null, null, model, profile(), time + FRAME_NANOS);

        assertEquals(0.0D, runtime.progressFor(model, GUN_ID, 2L), TOLERANCE);
    }

    @Test
    void closeEyeAngleGateCapsTheRenderedAperture() {
        VrScopeAlignmentGateRuntime runtime = new VrScopeAlignmentGateRuntime();
        Object model = new Object();
        long time = 1L;
        VrScopeAlignmentSample outsideAuthoredCone = VrScopeAlignmentGateProfileTest.sample(
                1L,
                0.08D,
                0.0D,
                5.5D,
                5.5D,
                0.0D
        );
        VrScopeAuthorEyeBoxSample closeEyeBox = withEyeRelief(eyeBox(1L, 0.80D), 0.20D, 0.08D);

        runtime.record(outsideAuthoredCone, closeEyeBox, null, model, profile(), time);

        assertEquals(0.0D, runtime.progressFor(model, GUN_ID, 1L), TOLERANCE);
    }

    @Test
    void modelGunAndFrameMismatchesFailClosedAndReset() {
        assertMismatchCloses(new Object(), GUN_ID, 2L);
        assertMismatchCloses(null, ResourceLocation.tryParse("tacz:other_gun"), 2L);
        assertMismatchCloses(null, GUN_ID, 3L);
    }

    @Test
    void mismatchedDivisionSourceCannotPublishCircleCenter() {
        VrScopeAlignmentGateRuntime runtime = new VrScopeAlignmentGateRuntime();
        Object model = new Object();
        VrScopeDivisionProjectionSample division = VrScopeDivisionProjectionSampleTest.measuredWithGunOffset(0.0D);

        runtime.record(aligned(1L), eyeBox(1L, 0.0D), division, model, profile(), 1L);

        assertEquals(
                VrScopePerEyeGateRuntime.OptionalCenter.empty(),
                runtime.circleCenterFor(model, GUN_ID, 1L)
        );
    }

    @Test
    void resetAllowsPreviewButRequiresAClosedReseedBeforeFullProgressReturns() {
        VrScopeAlignmentGateRuntime runtime = new VrScopeAlignmentGateRuntime();
        Object model = new Object();
        long time = 1L;
        runtime.record(aligned(1L), eyeBox(1L, 0.0D), null, model, profile(), time);
        runtime.record(aligned(2L), eyeBox(2L, 0.0D), null, model, profile(), time + FRAME_NANOS);
        assertTrue(runtime.progressFor(model, GUN_ID, 2L) > 0.0D);

        runtime.reset();
        runtime.record(aligned(3L), eyeBox(3L, 0.0D), null, model, profile(), time + 2L * FRAME_NANOS);

        assertEquals(
                VrScopeAuthorEyeBoxGate.MAX_ACQUISITION_RADIUS_SCALE,
                runtime.progressFor(model, GUN_ID, 3L),
                TOLERANCE
        );
    }

    private static void assertMismatchCloses(Object mismatchedModel, ResourceLocation gunId, long frameIndex) {
        VrScopeAlignmentGateRuntime runtime = new VrScopeAlignmentGateRuntime();
        Object model = new Object();
        long time = 1L;
        runtime.record(aligned(1L), eyeBox(1L, 0.0D), null, model, profile(), time);
        runtime.record(aligned(2L), eyeBox(2L, 0.0D), null, model, profile(), time + FRAME_NANOS);
        Object queriedModel = mismatchedModel == null ? model : mismatchedModel;

        assertEquals(0.0D, runtime.progressFor(queriedModel, gunId, frameIndex), TOLERANCE);
        assertEquals(0.0D, runtime.progressFor(model, GUN_ID, 2L), TOLERANCE);
    }

    private static VrScopeAlignmentGateProfile profile() {
        return VrScopeAlignmentGateTuning.observationProfile();
    }

    private static VrScopeAlignmentSample aligned(long frameIndex) {
        return VrScopeAlignmentGateProfileTest.sample(frameIndex, 0.11D, 0.035D, 20.0D, 5.0D, 20.0D);
    }

    private static VrScopeAuthorEyeBoxSample eyeBox(long frameIndex, double pupilRadius) {
        return withEyeRelief(VrScopeAuthorEyeBoxGateTest.eyeBox(frameIndex, pupilRadius), 0.12D, 0.12D);
    }

    private static VrScopeAuthorEyeBoxSample withEyeRelief(
            VrScopeAuthorEyeBoxSample source,
            double authorEyeRelief,
            double currentEyeRelief
    ) {
        return new VrScopeAuthorEyeBoxSample(
                source.frameIndex(),
                source.gunId(),
                source.scopeId(),
                source.viewIndex(),
                authorEyeRelief,
                currentEyeRelief,
                currentEyeRelief - authorEyeRelief,
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
