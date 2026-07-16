package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies task-local shot snapshots and cleanup independently of TACZ's scheduler. */
class VrShotPoseTaskContextTest {
    @Test
    void exposesBoundPoseOnlyWhileTheTaskRuns() {
        VrShotPose pose = new VrShotPose(new Vec3(1.0D, 2.0D, 3.0D), new Vec3(0.0D, 0.0D, -1.0D));
        AtomicReference<VrShotPose> observed = new AtomicReference<>();

        VrShotPoseTaskContext.bind(
                pose, () -> observed.set(VrShotPoseTaskContext.current().orElseThrow())
        ).run();

        assertEquals(pose, observed.get());
        assertTrue(VrShotPoseTaskContext.current().isEmpty());
    }

    @Test
    void restoresAnOuterTaskAfterNestedExecution() {
        VrShotPose outer = new VrShotPose(Vec3.ZERO, new Vec3(0.0D, 0.0D, -1.0D));
        VrShotPose inner = new VrShotPose(Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D));
        AtomicReference<VrShotPose> restored = new AtomicReference<>();

        VrShotPoseTaskContext.bind(outer, () -> {
            VrShotPoseTaskContext.bind(inner, () -> assertEquals(
                    inner, VrShotPoseTaskContext.current().orElseThrow()
            )).run();
            restored.set(VrShotPoseTaskContext.current().orElseThrow());
        }).run();

        assertEquals(outer, restored.get());
        assertTrue(VrShotPoseTaskContext.current().isEmpty());
    }
}
