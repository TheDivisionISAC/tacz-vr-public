package com.thedivisionisac.taczvr.client.control;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies that gun HUD retreat is a fixed offset independent of controller or hotbar geometry. */
class VrGunHotbarRetreatTest {
    @Test
    void fixedOffsetNormalizesTheHudBackwardAxis() {
        Vec3 offset = VrGunHotbarRetreat.fixedOffset(new Vec3(0.0D, 0.0D, -2.0D), 0.10D);

        assertEquals(0.0D, offset.x, 1.0E-9D);
        assertEquals(0.0D, offset.y, 1.0E-9D);
        assertEquals(-0.10D, offset.z, 1.0E-9D);
    }

    @Test
    void zeroDistanceLeavesTheHudAtItsVivecraftPosition() {
        assertEquals(Vec3.ZERO, VrGunHotbarRetreat.fixedOffset(new Vec3(0.0D, 0.0D, -1.0D), 0.0D));
    }

    @Test
    void wristAxisMapsToControllerBackForEitherHandLayout() {
        assertWristAxis(false, 1.0F);
        assertWristAxis(true, -1.0F);
    }

    @Test
    void handAxisPointsMostlyBackAfterVivecraftHandTilt() {
        Vec3 localAxis = VrGunHotbarRetreat.localBackwardAxis(false, false);
        Vector3f transformed = new Matrix4f()
                .rotateX((float) Math.PI * -0.2F)
                .rotateY((float) Math.PI * 0.1F)
                .transformDirection(asVector(localAxis));

        assertEquals(1.0F, transformed.length(), 1.0E-6F);
        org.junit.jupiter.api.Assertions.assertTrue(transformed.z > 0.75F);
    }

    private static void assertWristAxis(boolean reverseHands, float side) {
        Vec3 localAxis = VrGunHotbarRetreat.localBackwardAxis(true, reverseHands);
        Vector3f transformed = new Matrix4f()
                .rotateZ((float) Math.PI * 0.5F * side)
                .rotateY((float) Math.PI * 0.5F * side)
                .transformDirection(asVector(localAxis));

        assertEquals(0.0F, transformed.x, 1.0E-6F);
        assertEquals(0.0F, transformed.y, 1.0E-6F);
        assertEquals(1.0F, transformed.z, 1.0E-6F);
    }

    private static Vector3f asVector(Vec3 vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }
}
