package com.thedivisionisac.taczvr.server.shot;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies that wall-clamped muzzles stay on the eye-visible side of the collision surface. */
class MuzzleOriginClampTest {
    private static final double TOLERANCE = 1.0E-9D;

    @Test
    void retreatsFromTheSurfaceTowardTheSafeOrigin() {
        Vec3 clamped = MuzzleOriginClamp.beforeSurface(Vec3.ZERO, new Vec3(0.0D, 0.0D, -1.0D));

        assertEquals(0.0D, clamped.x, TOLERANCE);
        assertEquals(0.0D, clamped.y, TOLERANCE);
        assertEquals(-1.0D + MuzzleOriginClamp.SURFACE_RETREAT_BLOCKS, clamped.z, TOLERANCE);
    }

    @Test
    void neverRetreatsPastAnExtremelyCloseSafeOrigin() {
        Vec3 safeOrigin = new Vec3(0.0D, 0.0D, -0.001D);
        Vec3 clamped = MuzzleOriginClamp.beforeSurface(safeOrigin, Vec3.ZERO);

        assertEquals(safeOrigin.x, clamped.x, TOLERANCE);
        assertEquals(safeOrigin.y, clamped.y, TOLERANCE);
        assertEquals(safeOrigin.z, clamped.z, TOLERANCE);
    }
}
