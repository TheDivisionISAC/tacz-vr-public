package com.thedivisionisac.taczvr.server.shot;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the controller vector conversion expected by TACZ's pitch and yaw suppliers. */
class ValidatedShotDirectionTest {
    private static final float TOLERANCE = 0.0001F;

    @Test
    void convertsHorizontalCardinalDirections() {
        assertAngles(new Vec3(0.0D, 0.0D, 1.0D), 0.0F, 0.0F);
        assertAngles(new Vec3(-1.0D, 0.0D, 0.0D), 0.0F, 90.0F);
        assertAngles(new Vec3(1.0D, 0.0D, 0.0D), 0.0F, -90.0F);
    }

    @Test
    void convertsVerticalDirectionsUsingMinecraftPitchSigns() {
        assertAngles(new Vec3(0.0D, 1.0D, 0.0D), -90.0F, 0.0F);
        assertAngles(new Vec3(0.0D, -1.0D, 0.0D), 90.0F, 0.0F);
    }

    private static void assertAngles(Vec3 direction, float expectedPitch, float expectedYaw) {
        ValidatedShotDirection validated = new ValidatedShotDirection(direction);
        assertEquals(expectedPitch, validated.pitchDegrees(), TOLERANCE);
        assertEquals(expectedYaw, validated.yawDegrees(), TOLERANCE);
    }
}
