package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies HMD-centered optical metrics independently from TACZ rendering and from any future visibility threshold.
 */
class VrScopeAlignmentSampleTest {
    private static final ResourceLocation GUN_ID = ResourceLocation.tryParse("tacz:test_gun");
    private static final ResourceLocation SCOPE_ID = ResourceLocation.tryParse("tacz:test_scope");

    @Test
    void centeredForwardScopeProducesZeroAlignmentError() {
        VrScopeAlignmentSample sample = VrScopeAlignmentSample.measured(
                12L,
                GUN_ID,
                SCOPE_ID,
                0,
                new Vec3(0.0D, 0.0D, -0.25D),
                new Vec3(0.0D, 0.0D, -4.0D)
        ).orElseThrow();

        assertEquals(0.25D, sample.lensDistance(), 1.0E-9D);
        assertEquals(0.25D, sample.viewForwardDistance(), 1.0E-9D);
        assertEquals(0.0D, sample.viewLateralOffset(), 1.0E-9D);
        assertEquals(0.0D, sample.viewCenterAngleDegrees(), 1.0E-9D);
        assertEquals(0.0D, sample.opticalAxisAngleDegrees(), 1.0E-9D);
        assertEquals(0.0D, sample.coaxialAngleDegrees(), 1.0E-9D);
        assertEquals(-1.0D, sample.opticalAxis().z, 1.0E-9D);
    }

    @Test
    void lateralOffsetAndAnglesRemainObservableWithoutApplyingPolicy() {
        VrScopeAlignmentSample sample = VrScopeAlignmentSample.measured(
                13L,
                GUN_ID,
                SCOPE_ID,
                2,
                new Vec3(0.03D, 0.04D, -0.20D),
                new Vec3(0.0D, 0.0D, -1.0D)
        ).orElseThrow();

        double expectedAngle = Math.toDegrees(Math.atan2(0.05D, 0.20D));
        assertEquals(0.05D, sample.viewLateralOffset(), 1.0E-9D);
        assertEquals(0.05D, sample.opticalLateralOffset(), 1.0E-9D);
        assertEquals(expectedAngle, sample.viewCenterAngleDegrees(), 1.0E-9D);
        assertEquals(expectedAngle, sample.coaxialAngleDegrees(), 1.0E-9D);
        assertEquals(0.0D, sample.opticalAxisAngleDegrees(), 1.0E-9D);
    }

    @Test
    void invalidOrDegenerateVectorsAreRejected() {
        assertTrue(VrScopeAlignmentSample.measured(
                1L, GUN_ID, SCOPE_ID, 0, Vec3.ZERO, new Vec3(0.0D, 0.0D, -1.0D)
        ).isEmpty());
        assertTrue(VrScopeAlignmentSample.measured(
                1L, GUN_ID, SCOPE_ID, 0,
                new Vec3(Double.NaN, 0.0D, -1.0D),
                new Vec3(0.0D, 0.0D, -1.0D)
        ).isEmpty());
        assertTrue(VrScopeAlignmentSample.measured(
                -1L, GUN_ID, SCOPE_ID, 0,
                new Vec3(0.0D, 0.0D, -1.0D),
                new Vec3(0.0D, 0.0D, -1.0D)
        ).isEmpty());
        assertTrue(VrScopeAlignmentSample.measured(
                1L, GUN_ID, SCOPE_ID, -1,
                new Vec3(0.0D, 0.0D, -1.0D),
                new Vec3(0.0D, 0.0D, -1.0D)
        ).isEmpty());
    }
}
