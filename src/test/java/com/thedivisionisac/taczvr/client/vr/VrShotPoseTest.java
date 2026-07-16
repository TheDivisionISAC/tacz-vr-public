package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that transient invalid tracking frames cannot enter rendering or the shot protocol. */
class VrShotPoseTest {
    @Test
    void validatedNormalizesFiniteDirection() {
        Vec3 position = new Vec3(1.0, 2.0, 3.0);
        VrShotPose pose = VrShotPose.validated(position, new Vec3(0.0, 0.0, 4.0)).orElseThrow();

        assertEquals(position, pose.position());
        assertEquals(new Vec3(0.0, 0.0, 1.0), pose.direction());
    }

    @Test
    void validatedRejectsNonFinitePosition() {
        assertTrue(VrShotPose.validated(
                new Vec3(Double.NaN, 0.0, 0.0),
                new Vec3(0.0, 0.0, 1.0)
        ).isEmpty());
    }

    @Test
    void validatedRejectsNonFiniteDirection() {
        assertTrue(VrShotPose.validated(
                Vec3.ZERO,
                new Vec3(0.0, Double.POSITIVE_INFINITY, 1.0)
        ).isEmpty());
    }

    @Test
    void validatedRejectsDegenerateDirection() {
        assertTrue(VrShotPose.validated(Vec3.ZERO, Vec3.ZERO).isEmpty());
    }
}
