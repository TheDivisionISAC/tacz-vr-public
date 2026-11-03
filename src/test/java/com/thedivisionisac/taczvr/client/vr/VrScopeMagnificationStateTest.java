package com.thedivisionisac.taczvr.client.vr;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies publication, single execution, identity rejection, and first-eye recovery without a GL context. */
class VrScopeMagnificationStateTest {
    private final Object world = new Object(), item = new Object(), target = new Object();
    private final VrScopeMagnificationState state = new VrScopeMagnificationState();

    private void prepare() {
        assertTrue(state.prepare(11, VrScopeWorldViewTest.request(10), world, item, target));
    }

    @Test
    void onlyACompleteSuccessfulPassPublishesOnce() {
        prepare();
        assertFalse(state.imageReady(11, world, item, target));
        assertTrue(state.begin(11, world, item, target));
        state.cameraApplied();
        state.projectionApplied();
        assertTrue(state.finish(true));
        assertTrue(state.imageReady(11, world, item, target));
        assertFalse(state.begin(11, world, item, target));
        assertFalse(state.imageReady(12, world, item, target));
    }

    @Test
    void missingCameraOrProjectionCannotPublish() {
        for (boolean camera : new boolean[]{false, true}) {
            prepare();
            assertTrue(state.begin(11, world, item, target));
            if (camera) state.cameraApplied(); else state.projectionApplied();
            assertFalse(state.finish(true));
            assertFalse(state.imageReady(11, world, item, target));
        }
    }

    @Test
    void failureStillRestoresFirstOrdinaryEyeObligation() {
        prepare();
        assertTrue(state.begin(11, world, item, target));
        state.cameraApplied();
        state.projectionApplied();
        assertFalse(state.finish(false));
        assertTrue(state.takeFirstEye(11));
        assertFalse(state.takeFirstEye(11));
        assertFalse(state.imageReady(11, world, item, target));
    }

    @Test
    void changedWorldHeldItemOrTargetAndLifecycleResetInvalidate() {
        prepare();
        assertFalse(state.begin(11, new Object(), item, target));
        assertFalse(state.begin(11, world, new Object(), target));
        assertFalse(state.begin(11, world, item, new Object()));
        assertTrue(state.begin(11, world, item, target));
        state.cameraApplied(); state.projectionApplied(); state.finish(true);
        assertFalse(state.imageReady(11, world, new Object(), target));
        state.reset();
        assertFalse(state.scheduled(11));
        assertFalse(state.takeFirstEye(11));
    }
}
