package com.thedivisionisac.taczvr.server.lrtactical;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LrTacticalThrowMotionTest {
    @Test
    void redirectsToControllerDirectionAndPreservesLrTacticalSpeed() {
        Vec3 original = new Vec3(0.0D, 0.0D, 1.5D);

        Vec3 redirected = LrTacticalThrowMotion.redirect(original, new Vec3(2.0D, 0.0D, 0.0D));

        assertEquals(1.5D, redirected.length(), 1.0E-9D);
        assertEquals(1.5D, redirected.x, 1.0E-9D);
        assertEquals(0.0D, redirected.y, 1.0E-9D);
        assertEquals(0.0D, redirected.z, 1.0E-9D);
    }

    @Test
    void leavesInvalidOrStationaryMotionUntouched() {
        Vec3 stationary = Vec3.ZERO;
        Vec3 nonFinite = new Vec3(Double.NaN, 0.0D, 0.0D);

        assertSame(stationary, LrTacticalThrowMotion.redirect(stationary, new Vec3(1.0D, 0.0D, 0.0D)));
        assertSame(nonFinite, LrTacticalThrowMotion.redirect(nonFinite, new Vec3(1.0D, 0.0D, 0.0D)));
    }
}
