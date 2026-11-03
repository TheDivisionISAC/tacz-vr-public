package com.thedivisionisac.taczvr.client.vr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrScopeRenderPassTest {
    @Test
    void classifiesOnlyCanonicalStereoEyes() {
        assertEquals(VrScopeRenderPass.LEFT, VrScopeRenderPass.fromName("LEFT"));
        assertEquals(VrScopeRenderPass.RIGHT, VrScopeRenderPass.fromName("RIGHT"));
        assertEquals(VrScopeRenderPass.OTHER, VrScopeRenderPass.fromName("SCOPER"));
        assertEquals(VrScopeRenderPass.OTHER, VrScopeRenderPass.fromName(null));
    }

    @Test
    void unknownPassFailsClosedAndEyesHaveStableIndices() {
        assertTrue(VrScopeRenderPass.LEFT.isEye());
        assertTrue(VrScopeRenderPass.RIGHT.isEye());
        assertFalse(VrScopeRenderPass.OTHER.isEye());
        assertEquals(0, VrScopeRenderPass.LEFT.eyeIndex());
        assertEquals(1, VrScopeRenderPass.RIGHT.eyeIndex());
        assertEquals(-1, VrScopeRenderPass.OTHER.eyeIndex());
        assertEquals(VrScopeRenderPass.RIGHT, VrScopeRenderPass.LEFT.opposite());
        assertEquals(VrScopeRenderPass.LEFT, VrScopeRenderPass.RIGHT.opposite());
        assertEquals(VrScopeRenderPass.OTHER, VrScopeRenderPass.OTHER.opposite());
    }

    @Test
    void acceptsEnumLikeOptionalPassObjects() {
        assertEquals(VrScopeRenderPass.LEFT, VrScopeRenderPass.fromObject(TestPass.LEFT));
        assertEquals(VrScopeRenderPass.RIGHT, VrScopeRenderPass.fromObject(TestPass.RIGHT));
        assertEquals(VrScopeRenderPass.OTHER, VrScopeRenderPass.fromObject(TestPass.MIRROR));
    }

    private enum TestPass {
        LEFT,
        RIGHT,
        MIRROR
    }
}
