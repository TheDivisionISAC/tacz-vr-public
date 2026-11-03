package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VrScopeAuthorEyeBoxGateTest {
    private static final double TOLERANCE = 1.0E-9D;
    private static final ResourceLocation GUN_ID = ResourceLocation.tryParse("tacz:test_gun");
    private static final ResourceLocation SCOPE_ID = ResourceLocation.tryParse("tacz:test_scope");

    @Test
    void keepsPupilCenterOpenAndSmoothlyClosesBeforeThePhysicalRim() {
        assertEquals(1.0D, VrScopeAuthorEyeBoxGate.pupilProgress(eyeBox(1L, 0.60D)), TOLERANCE);
        assertEquals(0.5D, VrScopeAuthorEyeBoxGate.pupilProgress(eyeBox(1L, 0.75D)), TOLERANCE);
        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.pupilProgress(eyeBox(1L, 0.90D)), TOLERANCE);
        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.pupilProgress(eyeBox(1L, 1.10D)), TOLERANCE);
    }

    @Test
    void scopeViewEnvelopeUsesAuthorReliefAndSeparateAxes() {
        assertEquals(1.0D, VrScopeAuthorEyeBoxGate.scopeViewProgress(
                eyeBox(1L, 0.0D, 0.15D, 0.04D, 0.20D)
        ), TOLERANCE);
        assertEquals(0.5D, VrScopeAuthorEyeBoxGate.scopeViewProgress(
                eyeBox(1L, 0.0D, 0.165D, 0.04D, 0.20D)
        ), TOLERANCE);
        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.scopeViewProgress(
                eyeBox(1L, 0.0D, 0.18D, 0.04D, 0.20D)
        ), TOLERANCE);
        assertEquals(0.5D, VrScopeAuthorEyeBoxGate.scopeViewProgress(
                eyeBox(1L, 0.0D, 0.15D, 0.05D, 0.20D)
        ), TOLERANCE);
        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.scopeViewProgress(
                eyeBox(1L, 0.0D, 0.15D, 0.06D, 0.20D)
        ), TOLERANCE);
    }

    @Test
    void centeredRayIsNotRejectedOnlyBecauseTheVrEyeIsCloserThanScopeView() {
        VrScopeAuthorEyeBoxSample sample = eyeBox(1L, 0.30D, -0.14D, 0.01D, 0.20D);

        assertEquals(0.70D, VrScopeAuthorEyeBoxGate.scopeViewAxialDistanceRatio(sample), TOLERANCE);
        assertEquals(0.05D, VrScopeAuthorEyeBoxGate.scopeViewLateralDistanceRatio(sample), TOLERANCE);
        assertEquals(1.0D, VrScopeAuthorEyeBoxGate.scopeViewProgress(sample), TOLERANCE);
    }

    @Test
    void weakestEyeBoxComponentControlsWithoutMultiplicativeCollapse() {
        VrScopeAuthorEyeBoxSample sample = eyeBox(1L, 0.75D, 0.165D, 0.05D, 0.20D);

        assertEquals(0.5D, VrScopeAuthorEyeBoxGate.progress(sample), TOLERANCE);
        assertEquals(0.172409D, VrScopeAuthorEyeBoxGate.scopeViewDistance(sample), 1.0E-6D);
        assertEquals(0.862047D, VrScopeAuthorEyeBoxGate.scopeViewDistanceRatio(sample), 1.0E-6D);
    }

    @Test
    void acquisitionZoneProvidesARestrictedPreviewOutsideTheUsablePupil() {
        VrScopeAlignmentSample alignment =
                VrScopeAlignmentGateProfileTest.sample(1L, 0.11D, 0.035D, 20.0D, 5.0D, 20.0D);
        VrScopeAuthorEyeBoxSample sample = eyeBox(1L, 1.20D, 0.18D, 0.06D, 0.20D);

        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.progress(sample), TOLERANCE);
        assertEquals(0.5D, VrScopeAuthorEyeBoxGate.acquisitionPupilProgress(sample), TOLERANCE);
        assertEquals(1.0D, VrScopeAuthorEyeBoxGate.acquisitionAxialProgress(sample), TOLERANCE);
        assertEquals(1.0D, VrScopeAuthorEyeBoxGate.acquisitionLateralProgress(sample), TOLERANCE);
        assertEquals(0.5D, VrScopeAuthorEyeBoxGate.acquisitionProgressFor(
                alignment,
                sample,
                1.0D
        ), TOLERANCE);
    }

    @Test
    void acquisitionZoneStillFailsClosedOutsideItsOuterBounds() {
        VrScopeAlignmentSample alignment =
                VrScopeAlignmentGateProfileTest.sample(1L, 0.11D, 0.035D, 20.0D, 5.0D, 20.0D);

        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.acquisitionProgressFor(
                alignment,
                eyeBox(1L, 1.50D, 0.0D, 0.0D, 0.20D),
                1.0D
        ), TOLERANCE);
        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.acquisitionProgressFor(
                alignment,
                eyeBox(1L, 1.20D, 0.0D, 0.0D, 0.20D),
                0.10D
        ), TOLERANCE);
    }

    @Test
    void missingInvalidAndMismatchedFactsFailClosed() {
        VrScopeAlignmentSample alignment =
                VrScopeAlignmentGateProfileTest.sample(1L, 0.11D, 0.035D, 20.0D, 5.0D, 20.0D);

        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.progress(null), TOLERANCE);
        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.progress(eyeBox(1L, Double.NaN)), TOLERANCE);
        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.scopeViewProgress(
                eyeBox(1L, 0.0D, 0.0D, 0.0D, 0.0D)
        ), TOLERANCE);
        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.scopeViewProgress(
                eyeBox(1L, 0.0D, 0.0D, -0.01D, 0.20D)
        ), TOLERANCE);
        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.progressFor(alignment, eyeBox(2L, 0.0D)), TOLERANCE);
        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.acquisitionProgressFor(
                alignment,
                eyeBox(2L, 1.20D),
                1.0D
        ), TOLERANCE);
        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.acquisitionAlignmentProgress(Double.NaN), TOLERANCE);
    }

    @Test
    void matchingSourceUsesTheCombinedAuthorEyeBoxProgress() {
        VrScopeAlignmentSample alignment =
                VrScopeAlignmentGateProfileTest.sample(3L, 0.11D, 0.035D, 20.0D, 5.0D, 20.0D);

        assertEquals(1.0D, VrScopeAuthorEyeBoxGate.progressFor(alignment, eyeBox(3L, 0.0D)), TOLERANCE);
    }

    static VrScopeAuthorEyeBoxSample eyeBox(long frameIndex, double pupilRadius) {
        return eyeBox(frameIndex, pupilRadius, 0.0D, 0.0D, 0.20D);
    }

    private static VrScopeAuthorEyeBoxSample eyeBox(
            long frameIndex,
            double pupilRadius,
            double authorEyeAxialError,
            double authorEyeLateralError,
            double authorEyeRelief
    ) {
        boolean inside = Double.isFinite(pupilRadius) && pupilRadius <= 1.0D;
        return new VrScopeAuthorEyeBoxSample(
                frameIndex,
                GUN_ID,
                SCOPE_ID,
                0,
                authorEyeRelief,
                0.12D,
                authorEyeAxialError,
                authorEyeLateralError,
                0.02D,
                0.02D,
                5.0D,
                0.0D,
                0.0D,
                true,
                pupilRadius,
                0.0D,
                pupilRadius,
                1.0D - pupilRadius,
                inside
        );
    }
}
