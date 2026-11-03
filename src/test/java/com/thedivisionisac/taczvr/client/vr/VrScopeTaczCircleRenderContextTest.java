package com.thedivisionisac.taczvr.client.vr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that TACZ's original circle center is untouched outside the bounded physical-scope render context.
 */
class VrScopeTaczCircleRenderContextTest {
    @AfterEach
    void resetContext() {
        VrScopeTaczCircleRenderContext.resetForCurrentThread();
    }

    @Test
    void preservesOriginalCenterWithoutPhysicalScopeContext() {
        assertEquals(12.5F, VrScopeTaczCircleRenderContext.resolveCenterX(12.5F));
        assertEquals(-8.0F, VrScopeTaczCircleRenderContext.resolveCenterY(-8.0F));
    }

    @Test
    void freezesOneCenterAndRestoresNestedContext() {
        VrScopeAlignmentCapture.VrScopeCircleCenter outerCenter =
                new VrScopeAlignmentCapture.VrScopeCircleCenter(18.0D, -9.0D);
        VrScopeAlignmentCapture.VrScopeCircleCenter innerCenter =
                new VrScopeAlignmentCapture.VrScopeCircleCenter(-4.0D, 7.0D);

        try (VrScopeTaczCircleRenderContext.Scope outer = VrScopeTaczCircleRenderContext.enter(outerCenter)) {
            assertEquals(18.0F, VrScopeTaczCircleRenderContext.resolveCenterX(0.0F));
            assertEquals(-9.0F, VrScopeTaczCircleRenderContext.resolveCenterY(0.0F));
            try (VrScopeTaczCircleRenderContext.Scope inner = VrScopeTaczCircleRenderContext.enter(innerCenter)) {
                assertEquals(-4.0F, VrScopeTaczCircleRenderContext.resolveCenterX(0.0F));
                assertEquals(7.0F, VrScopeTaczCircleRenderContext.resolveCenterY(0.0F));
            }
            assertEquals(18.0F, VrScopeTaczCircleRenderContext.resolveCenterX(0.0F));
            assertEquals(-9.0F, VrScopeTaczCircleRenderContext.resolveCenterY(0.0F));
        }

        assertEquals(2.0F, VrScopeTaczCircleRenderContext.resolveCenterX(2.0F));
    }
}
