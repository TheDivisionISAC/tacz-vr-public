package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies fail-closed geometry scoring without choosing a production tuning preset.
 */
class VrScopeAlignmentGateProfileTest {
    private static final double TOLERANCE = 1.0E-9D;
    private static final ResourceLocation GUN_ID = ResourceLocation.tryParse("tacz:test_gun");
    private static final ResourceLocation SCOPE_ID = ResourceLocation.tryParse("tacz:test_scope");

    @Test
    void fullyAlignedFactsScoreOne() {
        assertEquals(1.0D, profile().score(sample(1L, 0.20D, 0.01D, 2.0D, 2.0D, 2.0D)), TOLERANCE);
    }

    @Test
    void everyClosedBoundaryFailsClosed() {
        VrScopeAlignmentGateProfile profile = profile();

        assertEquals(0.0D, profile.score(sample(1L, 0.03D, 0.01D, 2.0D, 2.0D, 2.0D)), TOLERANCE);
        assertEquals(0.0D, profile.score(sample(1L, 0.45D, 0.01D, 2.0D, 2.0D, 2.0D)), TOLERANCE);
        assertEquals(0.0D, profile.score(sample(1L, 0.20D, 0.12D, 2.0D, 2.0D, 2.0D)), TOLERANCE);
        assertEquals(0.0D, profile.score(sample(1L, 0.20D, 0.01D, 30.0D, 2.0D, 2.0D)), TOLERANCE);
        assertEquals(0.0D, profile.score(sample(1L, 0.20D, 0.01D, 2.0D, 30.0D, 2.0D)), TOLERANCE);
        assertEquals(0.0D, profile.score(sample(1L, 0.20D, 0.01D, 2.0D, 2.0D, 30.0D)), TOLERANCE);
    }

    @Test
    void falloffIsSmoothAndMonotonic() {
        VrScopeAlignmentGateProfile profile = profile();
        double full = profile.score(sample(1L, 0.20D, 0.02D, 2.0D, 2.0D, 2.0D));
        double middle = profile.score(sample(1L, 0.20D, 0.07D, 2.0D, 2.0D, 2.0D));
        double edge = profile.score(sample(1L, 0.20D, 0.11D, 2.0D, 2.0D, 2.0D));

        assertEquals(1.0D, full, TOLERANCE);
        assertEquals(0.5D, middle, TOLERANCE);
        assertTrue(edge < middle);
        assertTrue(edge > 0.0D);
    }

    @Test
    void invalidFactsAndProfilesAreRejectedSafely() {
        VrScopeAlignmentSample invalid = sample(1L, 0.20D, Double.NaN, 2.0D, 2.0D, 2.0D);

        assertEquals(0.0D, profile().score(invalid), TOLERANCE);
        assertEquals(0.0D, profile().score(null), TOLERANCE);
        assertThrows(
                IllegalArgumentException.class,
                () -> new VrScopeAlignmentGateProfile.EyeReliefBand(0.08D, 0.03D, 0.30D, 0.45D)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new VrScopeAlignmentGateProfile.UpperFalloff(0.12D, 0.02D)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new VrScopeAlignmentGateProfile(
                        profile().eyeRelief(),
                        profile().opticalLateralOffset(),
                        profile().viewCenterAngleDegrees(),
                        profile().opticalAxisAngleDegrees(),
                        profile().coaxialAngleDegrees(),
                        0.25D,
                        0.75D,
                        0.08D,
                        0.12D,
                        0.25D
                )
        );
    }

    static VrScopeAlignmentGateProfile profile() {
        return new VrScopeAlignmentGateProfile(
                new VrScopeAlignmentGateProfile.EyeReliefBand(0.03D, 0.08D, 0.30D, 0.45D),
                new VrScopeAlignmentGateProfile.UpperFalloff(0.02D, 0.12D),
                new VrScopeAlignmentGateProfile.UpperFalloff(5.0D, 30.0D),
                new VrScopeAlignmentGateProfile.UpperFalloff(5.0D, 30.0D),
                new VrScopeAlignmentGateProfile.UpperFalloff(5.0D, 30.0D),
                0.75D,
                0.25D,
                0.08D,
                0.12D,
                0.25D
        );
    }

    static VrScopeAlignmentSample sample(
            long frameIndex,
            double opticalAxialDistance,
            double opticalLateralOffset,
            double viewCenterAngle,
            double opticalAxisAngle,
            double coaxialAngle
    ) {
        return new VrScopeAlignmentSample(
                frameIndex,
                GUN_ID,
                SCOPE_ID,
                0,
                new Vec3(0.0D, 0.0D, -0.20D),
                new Vec3(0.0D, 0.0D, -1.0D),
                0.20D,
                0.20D,
                0.0D,
                opticalAxialDistance,
                opticalLateralOffset,
                viewCenterAngle,
                opticalAxisAngle,
                coaxialAngle
        );
    }
}
