package com.thedivisionisac.taczvr.client.vr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrScopeNearEyeGateTest {
    @Test
    void physicalMinimumIsStrictAndFailClosed() {
        VrScopeAuthorEyeBoxSample eyeBox = eyeBox(0.02D, 8.0D);
        assertFalse(VrScopeNearEyeGate.permits(null, eyeBox, null));
        assertFalse(VrScopeNearEyeGate.permits(sample(Double.NaN), eyeBox, null));
        assertFalse(VrScopeNearEyeGate.permits(
                sample(VrScopeNearEyeGate.MINIMUM_EYE_RELIEF_METERS), eyeBox, null
        ));
        assertTrue(VrScopeNearEyeGate.permits(
                sample(VrScopeNearEyeGate.MINIMUM_EYE_RELIEF_METERS + 0.001D), eyeBox, null
        ));
    }

    @Test
    void divisionEnvelopeOwnsNearContractionAboveTheAbsoluteFloor() {
        VrScopeAuthorEyeBoxSample contender = eyeBox(0.01875D, 3.8353D);
        VrScopeDivisionProjectionSample division = contenderDivisionProjection();
        double minimum = VrScopeNearEyeGate.minimumEyeRelief(sample(0.10D), contender, division);

        assertTrue(Math.abs(minimum - VrScopeNearEyeGate.MINIMUM_EYE_RELIEF_METERS) < 1.0E-9D);
        assertTrue(VrScopeNearEyeGate.permits(sample(0.08D), contender, division));
        assertTrue(VrScopeNearEyeGate.permits(sample(0.10D), contender, division));
    }

    private static VrScopeDivisionProjectionSample contenderDivisionProjection() {
        VrScopeAlignmentSample sample = sample(0.10D);
        double authorToRing = 0.278125D;
        double ringToDivision = 3.506238D;
        return VrScopeDivisionProjectionSample.measured(
                sample.frameIndex(),
                sample.gunId(),
                sample.scopeId(),
                sample.viewIndex(),
                plane(-authorToRing, 0.01875D),
                plane(-authorToRing, 0.020D),
                plane(-(authorToRing + ringToDivision), 1.049D),
                net.minecraft.world.phys.Vec3.ZERO,
                false
        ).orElseThrow();
    }

    private static VrScopeModelAperture.Transformed plane(double z, double radius) {
        return new VrScopeModelAperture.Transformed(
                new net.minecraft.world.phys.Vec3(0.0D, 0.0D, z),
                new net.minecraft.world.phys.Vec3(1.0D, 0.0D, 0.0D),
                new net.minecraft.world.phys.Vec3(0.0D, 1.0D, 0.0D),
                new net.minecraft.world.phys.Vec3(0.0D, 0.0D, -1.0D),
                radius,
                radius
        );
    }

    private static VrScopeAlignmentSample sample(double axialDistance) {
        return VrScopeAlignmentGateProfileTest.sample(1L, axialDistance, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static VrScopeAuthorEyeBoxSample eyeBox(double apertureRadius, double authorConeDegrees) {
        return new VrScopeAuthorEyeBoxSample(
                1L,
                sample(0.10D).gunId(),
                sample(0.10D).scopeId(),
                sample(0.10D).viewIndex(),
                0.20D,
                0.10D,
                -0.10D,
                0.0D,
                apertureRadius,
                apertureRadius,
                authorConeDegrees,
                0.0D,
                0.0D,
                true,
                0.0D,
                0.0D,
                0.0D,
                1.0D,
                true
        );
    }
}
