package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrScopeDivisionProjectionSampleTest {
    private static final double TOLERANCE = 1.0E-6D;
    private static final ResourceLocation GUN_ID = ResourceLocation.tryParse("tacz:m95");
    private static final ResourceLocation SCOPE_ID = ResourceLocation.tryParse("tacz:scope_contender");

    @Test
    void projectsDivisionBoundsThroughScopeViewOntoRingPlane() {
        VrScopeDivisionProjectionSample sample = measured(Vec3.ZERO);

        assertTrue(sample.ringBetweenAuthorAndDivision());
        assertEquals(0.5D, sample.centerProjectionRatio(), TOLERANCE);
        assertEquals(0.10D, sample.projectedWidth(), TOLERANCE);
        assertEquals(0.08D, sample.projectedHeight(), TOLERANCE);
        assertEquals(0.0D, sample.projectedCenterU(), TOLERANCE);
        assertEquals(0.0D, sample.projectedCenterV(), TOLERANCE);
        assertTrue(sample.forwardEyeIntersection());
        assertTrue(sample.insideProjectedDivision());
        assertEquals(0.0D, sample.authorDivisionPupilRadius(), TOLERANCE);
        assertEquals(0.0D, sample.currentDivisionPupilRadius(), TOLERANCE);
        assertTrue(sample.insideDivisionSightPupil());
        assertEquals(0.80D, sample.currentSafeOcularRadiusScale(), TOLERANCE);
        assertEquals(0.09D, sample.currentSafeTaczRadiusModifier(), TOLERANCE);
        assertEquals(0.10D, sample.currentProjectedWidth(), TOLERANCE);
        assertEquals(0.08D, sample.currentProjectedHeight(), TOLERANCE);
    }

    @Test
    void currentEyeToDivisionLineUsesRingDistanceAndPhysicalPupil() {
        VrScopeDivisionProjectionSample sample = measuredWithGunOffset(0.06D);

        assertEquals(0.0D, sample.authorDivisionPupilRadius(), TOLERANCE);
        assertEquals(0.5D, sample.currentDivisionPupilRadius(), TOLERANCE);
        assertTrue(sample.ringBetweenCurrentEyeAndDivision());
        assertTrue(sample.insideDivisionSightPupil());
        assertEquals(1.0D / 3.0D, sample.currentSafeOcularRadiusScale(), TOLERANCE);
        assertTrue(sample.currentSafeTaczRadiusModifier() > 0.0D);
    }

    @Test
    void movingEyeCloserShrinksTheSafeCenteredOpening() {
        VrScopeDivisionProjectionSample ordinary = measuredAtRelief(0.50D, 0.50D, 0.0D);
        VrScopeDivisionProjectionSample close = measuredAtRelief(0.25D, 0.50D, 0.0D);

        assertEquals(0.80D, ordinary.currentSafeOcularRadiusScale(), TOLERANCE);
        assertEquals(0.80D * 2.0D / 3.0D, close.currentSafeOcularRadiusScale(), TOLERANCE);
        assertTrue(close.currentSafeOcularRadiusScale() < ordinary.currentSafeOcularRadiusScale());
    }

    @Test
    void lateralEyeOffsetConsumesTheNearestDivisionEdgeMargin() {
        VrScopeDivisionProjectionSample centered = measuredAtRelief(0.50D, 0.50D, 0.0D);
        VrScopeDivisionProjectionSample offset = measuredAtRelief(0.50D, 0.50D, 0.06D);

        assertTrue(offset.currentSafeOcularRadiusScale() < centered.currentSafeOcularRadiusScale());
        assertEquals(-0.03D, offset.currentProjectedCenterU(), TOLERANCE);
    }

    @Test
    void distantDivisionLosesSafeCoverageFasterAtShortEyeRelief() {
        VrScopeDivisionProjectionSample nearDivision = measuredAtRelief(0.10D, 0.50D, 0.0D);
        VrScopeDivisionProjectionSample farDivision = measuredAtRelief(0.10D, 2.00D, 0.0D);

        assertTrue(farDivision.currentSafeOcularRadiusScale()
                < nearDivision.currentSafeOcularRadiusScale());
    }

    @Test
    void rejectsCurrentEyeRayOutsideProjectedDivisionBoundary() {
        VrScopeDivisionProjectionSample sample = measured(new Vec3(0.14D, 0.0D, 0.0D));

        assertTrue(sample.forwardEyeIntersection());
        assertFalse(sample.insideProjectedDivision());
    }

    @Test
    void reportsInvalidOrderingWhenRingIsNotBetweenScopeViewAndDivision() {
        VrScopeDivisionProjectionSample sample = VrScopeDivisionProjectionSample.measured(
                1L,
                GUN_ID,
                SCOPE_ID,
                0,
                plane(new Vec3(0.0D, 0.0D, -0.5D), 0.06D, 0.05D),
                plane(new Vec3(0.0D, 0.0D, -1.5D), 0.07D, 0.06D),
                plane(new Vec3(0.0D, 0.0D, -1.0D), 0.10D, 0.08D),
                Vec3.ZERO,
                false
        ).orElseThrow();

        assertFalse(sample.ringBetweenAuthorAndDivision());
        assertFalse(sample.insideProjectedDivision());
    }

    @Test
    void rejectsInvalidFrameViewAndOcularVectors() {
        assertTrue(VrScopeDivisionProjectionSample.measured(
                -1L,
                GUN_ID,
                SCOPE_ID,
                0,
                plane(new Vec3(0.0D, 0.0D, -0.5D), 0.06D, 0.05D),
                plane(new Vec3(0.0D, 0.0D, -0.5D), 0.07D, 0.06D),
                plane(new Vec3(0.0D, 0.0D, -1.0D), 0.10D, 0.08D),
                Vec3.ZERO,
                false
        ).isEmpty());
        assertTrue(VrScopeDivisionProjectionSample.measured(
                1L,
                GUN_ID,
                SCOPE_ID,
                -1,
                plane(new Vec3(0.0D, 0.0D, -0.5D), 0.06D, 0.05D),
                plane(new Vec3(0.0D, 0.0D, -0.5D), 0.07D, 0.06D),
                plane(new Vec3(0.0D, 0.0D, -1.0D), 0.10D, 0.08D),
                Vec3.ZERO,
                false
        ).isEmpty());
        VrScopeModelAperture.Transformed invalidOcular = new VrScopeModelAperture.Transformed(
                new Vec3(Double.NaN, 0.0D, -0.5D),
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(0.0D, 0.0D, -1.0D),
                0.06D,
                0.05D
        );
        assertTrue(VrScopeDivisionProjectionSample.measured(
                1L,
                GUN_ID,
                SCOPE_ID,
                0,
                invalidOcular,
                plane(new Vec3(0.0D, 0.0D, -0.5D), 0.07D, 0.06D),
                plane(new Vec3(0.0D, 0.0D, -1.0D), 0.10D, 0.08D),
                Vec3.ZERO,
                false
        ).isEmpty());
    }

    private static VrScopeDivisionProjectionSample measured(Vec3 authorEye) {
        return VrScopeDivisionProjectionSample.measured(
                1L,
                GUN_ID,
                SCOPE_ID,
                0,
                plane(new Vec3(0.0D, 0.0D, -0.5D), 0.06D, 0.05D),
                plane(new Vec3(0.0D, 0.0D, -0.5D), 0.07D, 0.06D),
                plane(new Vec3(0.0D, 0.0D, -1.0D), 0.10D, 0.08D),
                authorEye,
                false
        ).orElseThrow();
    }

    static VrScopeDivisionProjectionSample measuredWithGunOffset(double offset) {
        return VrScopeDivisionProjectionSample.measured(
                1L,
                GUN_ID,
                SCOPE_ID,
                0,
                plane(new Vec3(offset, 0.0D, -0.5D), 0.06D, 0.05D),
                plane(new Vec3(offset, 0.0D, -0.5D), 0.07D, 0.06D),
                plane(new Vec3(offset, 0.0D, -1.0D), 0.10D, 0.08D),
                new Vec3(offset, 0.0D, 0.0D),
                false
        ).orElseThrow();
    }

    static VrScopeDivisionProjectionSample measuredWithFullOcularCoverage() {
        return VrScopeDivisionProjectionSample.measured(
                1L,
                GUN_ID,
                SCOPE_ID,
                0,
                plane(new Vec3(0.0D, 0.0D, -0.5D), 0.06D, 0.05D),
                plane(new Vec3(0.0D, 0.0D, -0.5D), 0.07D, 0.06D),
                plane(new Vec3(0.0D, 0.0D, -1.0D), 0.14D, 0.12D),
                Vec3.ZERO,
                false
        ).orElseThrow();
    }

    private static VrScopeDivisionProjectionSample measuredAtRelief(
            double eyeRelief,
            double ringToDivision,
            double offset
    ) {
        return VrScopeDivisionProjectionSample.measured(
                1L,
                GUN_ID,
                SCOPE_ID,
                0,
                plane(new Vec3(offset, 0.0D, -eyeRelief), 0.06D, 0.05D),
                plane(new Vec3(offset, 0.0D, -eyeRelief), 0.07D, 0.06D),
                plane(new Vec3(offset, 0.0D, -(eyeRelief + ringToDivision)), 0.10D, 0.08D),
                new Vec3(offset, 0.0D, 0.0D),
                false
        ).orElseThrow();
    }

    private static VrScopeModelAperture.Transformed plane(Vec3 center, double radiusX, double radiusY) {
        return new VrScopeModelAperture.Transformed(
                center,
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(0.0D, 0.0D, -1.0D),
                radiusX,
                radiusY
        );
    }
}
