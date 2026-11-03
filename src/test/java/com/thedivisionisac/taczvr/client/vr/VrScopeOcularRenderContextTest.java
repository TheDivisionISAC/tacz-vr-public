package com.thedivisionisac.taczvr.client.vr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrScopeOcularRenderContextTest {
    @AfterEach
    void resetContext() {
        VrScopeOcularRenderContext.resetForCurrentThread();
    }

    @Test
    void preservesTaczAimingProgressOutsidePhysicalOpening() {
        assertEquals(0.35F, VrScopeOcularRenderContext.resolveTaczAimingProgress(0.35F));
        assertFalse(VrScopeOcularRenderContext.isPhysicalOpeningActive());
    }

    @Test
    void physicalOpeningOwnsAimingFactorOnlyInsideScope() {
        try (VrScopeOcularRenderContext.Scope ignored =
                     VrScopeOcularRenderContext.enterPhysicalOpening()) {
            assertTrue(VrScopeOcularRenderContext.isPhysicalOpeningActive());
            assertEquals(1.0F, VrScopeOcularRenderContext.resolveTaczAimingProgress(0.0F));
            assertEquals(1.0F, VrScopeOcularRenderContext.resolveTaczAimingProgress(0.6F));
        }

        assertFalse(VrScopeOcularRenderContext.isPhysicalOpeningActive());
        assertEquals(0.0F, VrScopeOcularRenderContext.resolveTaczAimingProgress(0.0F));
    }

    @Test
    void nestedScopeAndRepeatedCloseCannotLeakOwnership() {
        VrScopeOcularRenderContext.Scope outer = VrScopeOcularRenderContext.enterPhysicalOpening();
        VrScopeOcularRenderContext.Scope inner = VrScopeOcularRenderContext.enterPhysicalOpening();

        inner.close();
        inner.close();
        assertEquals(1.0F, VrScopeOcularRenderContext.resolveTaczAimingProgress(0.2F));

        outer.close();
        assertEquals(0.2F, VrScopeOcularRenderContext.resolveTaczAimingProgress(0.2F));
    }
}
