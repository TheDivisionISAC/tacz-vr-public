package com.thedivisionisac.taczvr.client.vr;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies full-orientation tremor damping, deliberate-motion lag limits, source isolation, and quaternion signs. */
class AdaptiveRotationFilterTest {
    private static final long FRAME_NANOS = 11_111_111L;

    @Test
    void dampsSmallControllerRollWithoutDiscardingIt() {
        AdaptiveRotationFilter filter = new AdaptiveRotationFilter();
        VrRotation identity = rotation(0.0D);
        VrRotation raw = rotation(0.3D);
        filter.update("gun", identity, 0.96D, 1.0D, 120.0D, 1L);

        VrRotation filtered = filter.update(
                "gun", raw, 0.96D, 1.0D, 120.0D, 1L + FRAME_NANOS
        );

        double filteredAngle = angleDegrees(identity, filtered);
        assertTrue(filteredAngle > 0.0D);
        assertTrue(filteredAngle < 0.3D);
    }

    @Test
    void neverLagsFartherThanTheConfiguredAngularBound() {
        AdaptiveRotationFilter filter = new AdaptiveRotationFilter();
        VrRotation identity = rotation(0.0D);
        VrRotation raw = rotation(10.0D);
        filter.update("gun", identity, 1.0D, 1.0D, 720.0D, 1L);

        VrRotation filtered = filter.update(
                "gun", raw, 1.0D, 1.0D, 720.0D, 1L + 100_000_000L
        );

        assertTrue(angleDegrees(raw, filtered) <= 1.001D);
    }

    @Test
    void sourceChangeReseedsAndEquivalentQuaternionSignDoesNotJump() {
        AdaptiveRotationFilter filter = new AdaptiveRotationFilter();
        VrRotation identity = rotation(0.0D);
        VrRotation negativeIdentity = VrRotation.validated(0.0D, 0.0D, 0.0D, -1.0D).orElseThrow();
        filter.update("first", identity, 1.0D, 1.0D, 120.0D, 1L);

        VrRotation equivalent = filter.update(
                "first", negativeIdentity, 1.0D, 1.0D, 120.0D, 1L + FRAME_NANOS
        );
        VrRotation reseeded = filter.update(
                "second", rotation(20.0D), 1.0D, 1.0D, 120.0D, 1L + 2L * FRAME_NANOS
        );

        assertEquals(1.0D, absoluteDot(identity, equivalent), 1.0E-12D);
        assertEquals(1.0D, absoluteDot(rotation(20.0D), reseeded), 1.0E-12D);
    }

    private static VrRotation rotation(double rollDegrees) {
        return VrRotation.validated(new Quaternionf().rotateZ((float) Math.toRadians(rollDegrees))).orElseThrow();
    }

    private static double angleDegrees(VrRotation first, VrRotation second) {
        double dot = absoluteDot(first, second);
        return Math.toDegrees(2.0D * Math.acos(Math.max(0.0D, Math.min(1.0D, dot))));
    }

    private static double absoluteDot(VrRotation first, VrRotation second) {
        return Math.abs(
                first.x() * second.x()
                        + first.y() * second.y()
                        + first.z() * second.z()
                        + first.w() * second.w()
        );
    }
}
