package com.thedivisionisac.taczvr.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Checks model identity and resource/session invalidation without loading Minecraft or Vivecraft. */
class ModelHandAnchorHistoryTest {
    @Test void replacementCannotInheritAnchorsBeforeItsFirstRender() {
        var history = new ModelHandAnchorHistory<Object>();
        Object original = new Object();
        Object replacement = new Object();
        history.recordLeft(original);
        history.recordRight(original);
        assertTrue(history.hasBoth(original));
        // HEAD visibility may run before beginGunPass or any replacement capture.
        assertFalse(history.hasBoth(replacement));
        history.recordRight(replacement);
        assertFalse(history.hasBoth(replacement));
        assertTrue(history.hasBoth(original));
    }

    @Test void differentModelsCannotContributeOneHandEach() {
        var history = new ModelHandAnchorHistory<Object>();
        Object first = new Object();
        Object second = new Object();
        history.recordLeft(first);
        history.recordRight(second);
        assertFalse(history.hasBoth(first));
        assertFalse(history.hasBoth(second));
        history.recordRight(first);
        assertTrue(history.hasBoth(first));
        assertFalse(history.hasBoth(second));
    }

    @Test void ordinaryFramesRetainCapturedAnchors() {
        var history = new ModelHandAnchorHistory<Object>();
        Object model = new Object();
        history.recordLeft(model);
        assertFalse(history.hasBoth(model));
        history.recordRight(model);
        assertTrue(history.hasBoth(model));
        history.recordLeft(model);
        history.recordLeft(model);
        assertTrue(history.hasBoth(model));
    }

    @Test void resourceOrSessionResetRequiresFreshEvidenceForEveryModel() {
        var history = new ModelHandAnchorHistory<Object>();
        Object first = new Object();
        Object second = new Object();
        history.recordLeft(first);
        history.recordRight(first);
        history.recordLeft(second);
        history.recordRight(second);
        history.reset();
        assertFalse(history.hasBoth(first));
        assertFalse(history.hasBoth(second));
        history.recordRight(first);
        assertFalse(history.hasBoth(first));
        history.recordLeft(first);
        assertTrue(history.hasBoth(first));
        assertFalse(history.hasBoth(second));
    }

    @Test void equalButDistinctModelsCannotShareEvidence() {
        var history = new ModelHandAnchorHistory<EqualModel>();
        EqualModel first = new EqualModel("same");
        EqualModel second = new EqualModel("same");
        assertEquals(first, second);
        assertNotSame(first, second);
        history.recordLeft(first);
        history.recordRight(first);
        assertTrue(history.hasBoth(first));
        assertFalse(history.hasBoth(second));
        history.recordRight(second);
        assertFalse(history.hasBoth(second));
    }

    @Test void missingModelCannotDisableFallback() {
        var history = new ModelHandAnchorHistory<Object>();
        history.recordLeft(null);
        history.recordRight(null);
        assertFalse(history.hasBoth(null));
        assertFalse(history.hasBoth(new Object()));
    }

    private record EqualModel(String description) {}
}
