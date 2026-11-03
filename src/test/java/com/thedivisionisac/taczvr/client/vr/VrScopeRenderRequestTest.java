package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies immutable request validation independently from capture, caching, and Vivecraft render state. */
class VrScopeRenderRequestTest {
    private static final ResourceLocation DIMENSION_ID = ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation GUN_ID = ResourceLocation.tryParse("tacz:test_gun");
    private static final ResourceLocation SCOPE_ID = ResourceLocation.tryParse("tacz:test_scope");

    @Test
    void validatesAndPreservesHmdLocalCameraFacts() {
        VrRotation rotation = new VrRotation(0.0D, 0.0D, 0.0D, 2.0D);
        VrScopeRenderRequest request = VrScopeRenderRequest.validated(
                31L, DIMENSION_ID, GUN_ID, SCOPE_ID, 2,
                new Vec3(0.03D, -0.01D, -0.24D), rotation, 6.0D, 0.75D
        ).orElseThrow();

        assertEquals(31L, request.sourceFrameIndex());
        assertEquals(2, request.viewIndex());
        assertEquals(6.0D, request.magnification());
        assertEquals(0.75D, request.apertureProgress());
        assertEquals(1.0D, request.scopeViewRotationInHead().w());
    }

    @Test
    void rejectsInvalidIdentityPoseMagnificationAndAperture() {
        VrRotation rotation = VrRotation.validated(0.0D, 0.0D, 0.0D, 1.0D).orElseThrow();
        assertTrue(VrScopeRenderRequest.validated(
                -1L, DIMENSION_ID, GUN_ID, SCOPE_ID, 0, Vec3.ZERO, rotation, 4.0D, 1.0D
        ).isEmpty());
        assertTrue(VrScopeRenderRequest.validated(
                1L, DIMENSION_ID, GUN_ID, SCOPE_ID, -1, Vec3.ZERO, rotation, 4.0D, 1.0D
        ).isEmpty());
        assertTrue(VrScopeRenderRequest.validated(
                1L, DIMENSION_ID, GUN_ID, SCOPE_ID, 0,
                new Vec3(Double.NaN, 0.0D, 0.0D), rotation, 4.0D, 1.0D
        ).isEmpty());
        assertTrue(VrScopeRenderRequest.validated(
                1L, DIMENSION_ID, GUN_ID, SCOPE_ID, 0, Vec3.ZERO, rotation, 0.0D, 1.0D
        ).isEmpty());
        assertTrue(VrScopeRenderRequest.validated(
                1L, DIMENSION_ID, GUN_ID, SCOPE_ID, 0, Vec3.ZERO, rotation, 4.0D, -0.1D
        ).isEmpty());
    }

    @Test
    void closedPrewarmCanPublishBeforeAnyVisibleOpening() {
        VrScopeRenderRequest request = VrScopeRenderRequest.validated(
                10L, DIMENSION_ID, GUN_ID, SCOPE_ID, 0, Vec3.ZERO,
                VrRotation.validated(0, 0, 0, 1).orElseThrow(), 4.0D, 0.0D
        ).orElseThrow();
        assertEquals(0.0D, request.apertureProgress());
        Object world = new Object(), item = new Object(), target = new Object();
        VrScopeMagnificationState state = new VrScopeMagnificationState();
        assertTrue(state.prepare(11, request, world, item, target));
        assertTrue(state.begin(11, world, item, target));
        state.cameraApplied();
        state.projectionApplied();
        assertTrue(state.finish(true));
        assertTrue(state.imageReady(11, world, item, target));
    }
}
