package com.thedivisionisac.taczvr.network.message;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Verifies the independently versioned VR shot-pose wire layout without invoking TACZ packet handling. */
class C2SVrShotPoseCodecTest {
    private static final double FLOAT_TOLERANCE = 0.000001D;

    @Test
    void roundTripsDirectionAndMuzzle() {
        C2SVrShotPose original = C2SVrShotPose.pose(
                4123L,
                new Vec3(0.25D, -0.5D, 0.75D),
                new Vec3(12.125D, 64.5D, -7.25D)
        );

        C2SVrShotPose decoded = roundTrip(original);

        assertEquals(C2SVrShotPose.PROTOCOL_VERSION, decoded.protocolVersion());
        assertEquals(4123L, decoded.taczTimestamp());
        assertEquals(C2SVrShotPose.FLAG_DIRECTION | C2SVrShotPose.FLAG_MUZZLE, decoded.flags());
        assertVecEquals(original.direction(), decoded.direction(), FLOAT_TOLERANCE);
        assertVecEquals(original.muzzlePosition(), decoded.muzzlePosition(), 0.0D);
    }

    @Test
    void roundTripsDirectionWithoutMuzzle() {
        C2SVrShotPose decoded = roundTrip(C2SVrShotPose.pose(9L, new Vec3(0.0D, 0.0D, -1.0D), null));

        assertEquals(C2SVrShotPose.FLAG_DIRECTION, decoded.flags());
        assertVecEquals(new Vec3(0.0D, 0.0D, -1.0D), decoded.direction(), FLOAT_TOLERANCE);
        assertNull(decoded.muzzlePosition());
    }

    private static C2SVrShotPose roundTrip(C2SVrShotPose message) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            C2SVrShotPose.encode(message, buffer);
            return C2SVrShotPose.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual, double tolerance) {
        assertEquals(expected.x, actual.x, tolerance);
        assertEquals(expected.y, actual.y, tolerance);
        assertEquals(expected.z, actual.z, tolerance);
    }
}
