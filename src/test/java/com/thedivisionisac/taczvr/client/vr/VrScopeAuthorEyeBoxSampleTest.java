package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrScopeAuthorEyeBoxSampleTest {
    private static final double TOLERANCE = 1.0E-6D;
    private static final ResourceLocation GUN_ID = ResourceLocation.tryParse("tacz:m95");
    private static final ResourceLocation SCOPE_ID = ResourceLocation.tryParse("tacz:scope_contender");

    @Test
    void derivesAuthorConeAndCenteredPupilFromModelGeometry() {
        VrScopeAuthorEyeBoxSample sample = measured(
                new Vec3(0.0D, 0.0D, -0.69921875D),
                Vec3.ZERO,
                0.046875D
        );

        assertEquals(3.835D, sample.authorConeHalfAngleDegrees(), 0.01D);
        assertEquals(0.0D, sample.pupilRadius(), TOLERANCE);
        assertEquals(1.0D, sample.pupilMargin(), TOLERANCE);
        assertTrue(sample.forwardIntersection());
        assertTrue(sample.insideAperture());
    }

    @Test
    void rejectsViewRayOutsideTheAuthoredOcular() {
        VrScopeAuthorEyeBoxSample sample = measured(
                new Vec3(0.06D, 0.0D, -0.69921875D),
                new Vec3(0.06D, 0.0D, 0.0D),
                0.046875D
        );

        assertTrue(sample.pupilRadius() > 1.0D);
        assertTrue(sample.pupilMargin() < 0.0D);
        assertFalse(sample.insideAperture());
    }

    @Test
    void reportsLensBehindTheEyeWithoutInventingAnIntersection() {
        VrScopeAuthorEyeBoxSample sample = measured(
                new Vec3(0.0D, 0.0D, 0.7D),
                new Vec3(0.0D, 0.0D, 1.4D),
                0.05D
        );

        assertFalse(sample.forwardIntersection());
        assertFalse(sample.insideAperture());
        assertTrue(Double.isInfinite(sample.pupilRadius()));
    }

    @Test
    void rejectsInvalidApertureRadius() {
        assertTrue(VrScopeAuthorEyeBoxSample.measured(
                1L,
                GUN_ID,
                SCOPE_ID,
                1,
                aperture(Vec3.ZERO, 0.0D),
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, -1.0D)
        ).isEmpty());
        assertTrue(VrScopeAuthorEyeBoxSample.measured(
                -1L,
                GUN_ID,
                SCOPE_ID,
                1,
                aperture(Vec3.ZERO, 0.04D),
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, -1.0D)
        ).isEmpty());
    }

    private static VrScopeAuthorEyeBoxSample measured(
            Vec3 ocularCenter,
            Vec3 authorEyePosition,
            double radius
    ) {
        return VrScopeAuthorEyeBoxSample.measured(
                1L,
                GUN_ID,
                SCOPE_ID,
                1,
                aperture(ocularCenter, radius),
                authorEyePosition,
                new Vec3(0.0D, 0.0D, -1.0D)
        ).orElseThrow();
    }

    private static VrScopeModelAperture.Transformed aperture(Vec3 center, double radius) {
        return new VrScopeModelAperture.Transformed(
                center,
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(0.0D, 0.0D, -1.0D),
                radius,
                radius
        );
    }
}
