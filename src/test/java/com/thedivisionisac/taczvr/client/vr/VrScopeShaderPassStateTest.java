package com.thedivisionisac.taczvr.client.vr;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.thedivisionisac.taczvr.client.vr.VrScopeShaderPassState.Pass.*;

/** Shader publication requires the whole current pipeline lifecycle, independently for scope and each eye. */
class VrScopeShaderPassStateTest {
    private final VrScopeShaderPassState state = new VrScopeShaderPassState();
    private final Object scope = new Object(), left = new Object(), right = new Object();

    @Test
    void finalCallbackWithoutBeginOrWithInvalidProjectionCannotPublish() {
        state.reset(10, scope, left, right);
        assertFalse(state.finish(10, SCOPE, scope, true));
        assertTrue(state.begin(10, SCOPE, scope));
        assertFalse(state.finish(10, SCOPE, scope, false));
        assertFalse(state.complete(10, SCOPE, scope));
    }

    @Test
    void scopeCompletionCannotGrantEitherEyeFinalComposition() {
        state.reset(10, scope, left, right);
        assertTrue(state.begin(10, SCOPE, scope));
        assertTrue(state.finish(10, SCOPE, scope, true));
        assertFalse(state.complete(10, LEFT, left));
        assertTrue(state.begin(10, LEFT, left));
        assertTrue(state.finish(10, LEFT, left, true));
        assertTrue(state.complete(10, LEFT, left));
        assertFalse(state.complete(10, RIGHT, right));
    }

    @Test
    void pipelineReplacementInvalidatesEarlierCompletionInSameFrame() {
        state.reset(10, scope, left, right);
        assertTrue(state.begin(10, SCOPE, scope));
        assertTrue(state.finish(10, SCOPE, scope, true));
        assertFalse(state.finish(10, SCOPE, new Object(), true));
        assertFalse(state.complete(10, SCOPE, scope));
    }

    @Test
    void nextFrameAndDuplicateExecutionCannotReuseOldCompletion() {
        state.reset(10, scope, left, right);
        assertTrue(state.begin(10, SCOPE, scope));
        assertTrue(state.finish(10, SCOPE, scope, true));
        assertFalse(state.begin(10, SCOPE, scope));
        assertFalse(state.complete(10, SCOPE, scope));
        state.reset(11, scope, left, right);
        assertFalse(state.complete(10, SCOPE, scope));
        assertFalse(state.complete(11, SCOPE, scope));
        assertTrue(state.begin(11, SCOPE, scope));
    }
}
