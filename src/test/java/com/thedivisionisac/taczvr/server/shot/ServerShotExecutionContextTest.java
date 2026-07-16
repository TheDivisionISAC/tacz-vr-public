package com.thedivisionisac.taczvr.server.shot;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that call-scoped shot data cannot leak into later server shots. */
class ServerShotExecutionContextTest {
    @Test
    void exposesPoseOnlyInsideTheBoundCall() {
        ValidatedShotPose pose = pose(Vec3.ZERO);

        String result = ServerShotExecutionContext.runWith(pose, () -> {
            assertSame(pose, ServerShotExecutionContext.current().orElseThrow());
            return "shot";
        });

        assertEquals("shot", result);
        assertTrue(ServerShotExecutionContext.current().isEmpty());
    }

    @Test
    void restoresOuterPoseAfterNestedCall() {
        ValidatedShotPose outer = pose(Vec3.ZERO);
        ValidatedShotPose inner = pose(new Vec3(1.0D, 2.0D, 3.0D));

        ServerShotExecutionContext.runWith(outer, () -> {
            ServerShotExecutionContext.runWith(inner, () -> {
                assertSame(inner, ServerShotExecutionContext.current().orElseThrow());
                return null;
            });
            assertSame(outer, ServerShotExecutionContext.current().orElseThrow());
            return null;
        });

        assertTrue(ServerShotExecutionContext.current().isEmpty());
    }

    @Test
    void clearsPoseWhenShotExecutionThrows() {
        ValidatedShotPose pose = pose(Vec3.ZERO);

        assertThrows(IllegalStateException.class, () -> ServerShotExecutionContext.runWith(pose, () -> {
            throw new IllegalStateException("test failure");
        }));

        assertTrue(ServerShotExecutionContext.current().isEmpty());
    }

    private static ValidatedShotPose pose(Vec3 muzzlePosition) {
        return new ValidatedShotPose(
                new ValidatedShotDirection(new Vec3(0.0D, 0.0D, 1.0D)),
                muzzlePosition
        );
    }
}
