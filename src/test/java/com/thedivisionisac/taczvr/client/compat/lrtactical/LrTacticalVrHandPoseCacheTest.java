package com.thedivisionisac.taczvr.client.compat.lrtactical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrTacticalVrHandPoseCacheTest {
    @Test
    void acceptsCurrentFrame() {
        assertTrue(LrTacticalVrHandPoseCache.isUsablePostRenderFrame(42L, 42L));
    }

    @Test
    void acceptsImmediatelyPreviousFrameForVivecraftPostRender() {
        assertTrue(LrTacticalVrHandPoseCache.isUsablePostRenderFrame(42L, 43L));
    }

    @Test
    void rejectsOlderPose() {
        assertFalse(LrTacticalVrHandPoseCache.isUsablePostRenderFrame(42L, 44L));
    }

    @Test
    void rejectsPoseFromFutureFrame() {
        assertFalse(LrTacticalVrHandPoseCache.isUsablePostRenderFrame(43L, 42L));
    }
}
