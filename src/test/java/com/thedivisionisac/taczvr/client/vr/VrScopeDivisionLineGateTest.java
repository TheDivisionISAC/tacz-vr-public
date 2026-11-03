package com.thedivisionisac.taczvr.client.vr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VrScopeDivisionLineGateTest {
    private static final double TOLERANCE = 1.0E-9D;

    @Test
    void limitsOpeningToCurrentEyeDivisionEnvelope() {
        VrScopeDivisionProjectionSample centered = sampleAtOffset(0.0D);
        VrScopeDivisionProjectionSample offset = sampleAtOffset(0.06D);
        assertEquals(centered.currentSafeTaczRadiusModifier() * VrScopeDivisionLineGate.SAFE_RADIUS_MARGIN,
                VrScopeDivisionLineGate.progress(centered), TOLERANCE);
        assertEquals(offset.currentSafeTaczRadiusModifier() * VrScopeDivisionLineGate.SAFE_RADIUS_MARGIN,
                VrScopeDivisionLineGate.progress(offset), TOLERANCE);
        assertEquals(0.0D, VrScopeDivisionLineGate.progress(sampleAtOffset(0.14D)), TOLERANCE);
    }

    @Test
    void acquisitionHintNeverExceedsTheSafeEnvelope() {
        VrScopeDivisionProjectionSample centered = sampleAtOffset(0.0D);
        double centeredExpected = centered.currentSafeTaczRadiusModifier()
                * VrScopeDivisionLineGate.SAFE_RADIUS_MARGIN
                / VrScopeAuthorEyeBoxGate.MAX_ACQUISITION_RADIUS_SCALE;
        assertEquals(centeredExpected, VrScopeDivisionLineGate.acquisitionProgress(centered), TOLERANCE);
        VrScopeDivisionProjectionSample narrow = sampleAtOffset(0.088D);
        double expected = narrow.currentSafeTaczRadiusModifier()
                * VrScopeDivisionLineGate.SAFE_RADIUS_MARGIN
                / VrScopeAuthorEyeBoxGate.MAX_ACQUISITION_RADIUS_SCALE;
        assertEquals(expected, VrScopeDivisionLineGate.acquisitionProgress(narrow), TOLERANCE);
        assertEquals(0.0D, VrScopeDivisionLineGate.acquisitionProgress(sampleAtOffset(0.14D)), TOLERANCE);
    }

    @Test
    void fullCoverageDoesNotLoseFieldOfViewToPrecisionMargin() {
        VrScopeDivisionProjectionSample sample =
                VrScopeDivisionProjectionSampleTest.measuredWithFullOcularCoverage();
        assertEquals(1.0D, sample.currentSafeTaczRadiusModifier(), TOLERANCE);
        assertEquals(1.0D, VrScopeDivisionLineGate.progress(sample), TOLERANCE);
    }

    private static VrScopeDivisionProjectionSample sampleAtOffset(double offset) {
        return VrScopeDivisionProjectionSampleTest.measuredWithGunOffset(offset);
    }
}
