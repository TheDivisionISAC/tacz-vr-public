package com.thedivisionisac.taczvr.client.vr;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrRotationTest {
    @Test
    void validationCopiesAndNormalizesMutableQuaternion() {
        Quaternionf source = new Quaternionf(0.0F, 0.0F, 2.0F, 2.0F);
        VrRotation rotation = VrRotation.validated(source).orElseThrow();
        source.identity();

        assertEquals(Math.sqrt(0.5D), rotation.z(), 1.0E-6D);
        assertEquals(Math.sqrt(0.5D), rotation.w(), 1.0E-6D);
    }

    @Test
    void validationRejectsNonFiniteAndDegenerateRotations() {
        assertTrue(VrRotation.validated(0.0D, 0.0D, 0.0D, 0.0D).isEmpty());
        assertTrue(VrRotation.validated(Double.NaN, 0.0D, 0.0D, 1.0D).isEmpty());
    }
}
