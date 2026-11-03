package com.thedivisionisac.taczvr.network.message;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class C2SLrTacticalThrowPoseCodecTest {
    @Test
    void roundTripsDirectionAndProtocol() {
        C2SLrTacticalThrowPose original =
                C2SLrTacticalThrowPose.pose(new Vec3(0.25D, -0.5D, 0.75D));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            C2SLrTacticalThrowPose.encode(original, buffer);
            C2SLrTacticalThrowPose decoded = C2SLrTacticalThrowPose.decode(buffer);

            assertEquals(C2SLrTacticalThrowPose.PROTOCOL_VERSION, decoded.protocolVersion());
            assertEquals(original.direction().x, decoded.direction().x, 1.0E-6D);
            assertEquals(original.direction().y, decoded.direction().y, 1.0E-6D);
            assertEquals(original.direction().z, decoded.direction().z, 1.0E-6D);
        } finally {
            buffer.release();
        }
    }
}
