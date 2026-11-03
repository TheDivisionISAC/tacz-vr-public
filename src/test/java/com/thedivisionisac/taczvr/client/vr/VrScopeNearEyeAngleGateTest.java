package com.thedivisionisac.taczvr.client.vr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VrScopeNearEyeAngleGateTest {
    private static final double TOLERANCE = 1.0E-9D;

    @Test
    void closeEyeUsesAnOcularCenteredAuthoredCone() {
        double fullPupil = pupilForAngle(0.08D, 0.02D, 8.0D * 0.55D);
        double closedPupil = pupilForAngle(0.08D, 0.02D, 8.0D * 0.90D);

        assertEquals(1.0D, VrScopeNearEyeAngleGate.progress(
                alignment(40.0D, 25.0D),
                eyeBox(0.08D, 0.16D, 8.0D, fullPupil, 0.0D)
        ), TOLERANCE);
        assertEquals(0.0D, VrScopeNearEyeAngleGate.progress(
                alignment(0.0D, 0.0D),
                eyeBox(0.08D, 0.16D, 8.0D, closedPupil, 0.0D)
        ), TOLERANCE);
    }

    @Test
    void oppositePupilDirectionsHaveTheSameBoundary() {
        VrScopeAuthorEyeBoxSample positiveU = eyeBox(0.08D, 0.16D, 8.0D, 0.40D, 0.0D);
        VrScopeAuthorEyeBoxSample negativeU = eyeBox(0.08D, 0.16D, 8.0D, -0.40D, 0.0D);
        VrScopeAuthorEyeBoxSample positiveV = eyeBox(0.08D, 0.16D, 8.0D, 0.0D, 0.40D);
        VrScopeAuthorEyeBoxSample negativeV = eyeBox(0.08D, 0.16D, 8.0D, 0.0D, -0.40D);

        double expected = VrScopeNearEyeAngleGate.progress(alignment(0.0D, 0.0D), positiveU);
        assertEquals(expected, VrScopeNearEyeAngleGate.progress(alignment(20.0D, 12.0D), negativeU), TOLERANCE);
        assertEquals(expected, VrScopeNearEyeAngleGate.progress(alignment(30.0D, 5.0D), positiveV), TOLERANCE);
        assertEquals(expected, VrScopeNearEyeAngleGate.progress(alignment(5.0D, 30.0D), negativeV), TOLERANCE);
    }

    @Test
    void additionalAngleRestrictionReleasesBeforeAuthoredEyeRelief() {
        VrScopeAlignmentSample outsideStrictCone = alignment(20.0D, 20.0D);

        assertEquals(1.0D, VrScopeNearEyeAngleGate.progress(
                outsideStrictCone,
                eyeBox(0.136D, 0.16D, 8.0D, 1.0D, 0.0D)
        ), TOLERANCE);
        assertEquals(0.5D, VrScopeNearEyeAngleGate.progress(
                outsideStrictCone,
                eyeBox(0.116D, 0.16D, 8.0D, 1.0D, 0.0D)
        ), TOLERANCE);
    }

    @Test
    void mismatchedSourcesFailClosed() {
        VrScopeAuthorEyeBoxSample eyeBox = eyeBox(0.08D, 0.16D, 8.0D, 0.0D, 0.0D);
        VrScopeAlignmentSample otherFrame = VrScopeAlignmentGateProfileTest.sample(
                2L, 0.08D, 0.0D, 0.0D, 0.0D, 0.0D
        );

        assertEquals(0.0D, VrScopeNearEyeAngleGate.progressFor(otherFrame, eyeBox), TOLERANCE);
    }

    private static VrScopeAlignmentSample alignment(double viewAngle, double axisAngle) {
        return VrScopeAlignmentGateProfileTest.sample(1L, 0.08D, 0.0D, viewAngle, axisAngle, 0.0D);
    }

    private static VrScopeAuthorEyeBoxSample eyeBox(
            double currentEyeRelief,
            double authorEyeRelief,
            double authorConeDegrees,
            double pupilU,
            double pupilV
    ) {
        VrScopeAlignmentSample source = alignment(0.0D, 0.0D);
        double pupilRadius = Math.hypot(pupilU, pupilV);
        return new VrScopeAuthorEyeBoxSample(
                source.frameIndex(),
                source.gunId(),
                source.scopeId(),
                source.viewIndex(),
                authorEyeRelief,
                currentEyeRelief,
                currentEyeRelief - authorEyeRelief,
                0.0D,
                0.02D,
                0.02D,
                authorConeDegrees,
                0.0D,
                0.0D,
                true,
                pupilU,
                pupilV,
                pupilRadius,
                1.0D - pupilRadius,
                pupilRadius <= 1.0D
        );
    }

    private static double pupilForAngle(double eyeRelief, double apertureRadius, double angleDegrees) {
        return Math.tan(Math.toRadians(angleDegrees)) * eyeRelief / apertureRadius;
    }
}
