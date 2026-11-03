package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the eye-view to HMD-local C3 camera conversion without Minecraft or Vivecraft render state. */
class VrScopeRenderRequestCaptureTest {
    private static final ResourceLocation DIMENSION_ID = ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation GUN_ID = ResourceLocation.tryParse("tacz:test_gun");
    private static final ResourceLocation SCOPE_ID = ResourceLocation.tryParse("tacz:test_scope");

    @Test
    void convertsScopeViewPoseIntoHeadBasisExactlyOnce() {
        VrEyeToHeadTransform eyeToHead = VrEyeToHeadTransform.validated(
                new Vec3(0.03D, 0.0D, 0.0D),
                new Quaternionf().rotationY((float) (Math.PI / 2.0D))
        ).orElseThrow();
        Matrix4f eyeMatrix = new Matrix4f()
                .translation(0.0F, 0.0F, -0.25F)
                .rotateX((float) (Math.PI / 2.0D))
                .scale(0.5F, 0.75F, 1.25F);

        VrScopeRenderRequest request = VrScopeRenderRequestCapture.capture(
                20L, DIMENSION_ID, GUN_ID, SCOPE_ID, 1,
                eyeMatrix, eyeToHead, 6.0D, 0.8D
        ).orElseThrow();

        assertEquals(-0.22D, request.scopeViewPositionInHead().x, 1.0E-6D);
        assertEquals(0.0D, request.scopeViewPositionInHead().y, 1.0E-6D);
        assertEquals(0.0D, request.scopeViewPositionInHead().z, 1.0E-6D);
        assertEquals(6.0D, request.magnification());
        assertEquals(0.8D, request.apertureProgress());
        Vec3 forward = rotate(request.scopeViewRotationInHead(), new Vec3(0.0D, 0.0D, -1.0D));
        assertEquals(0.0D, forward.x, 1.0E-6D);
        assertEquals(1.0D, forward.y, 1.0E-6D);
        assertEquals(0.0D, forward.z, 1.0E-6D);
    }

    @Test
    void rejectsMissingOrNonFinitePoseInputs() {
        VrEyeToHeadTransform identity = VrEyeToHeadTransform.validated(Vec3.ZERO, new Quaternionf()).orElseThrow();
        assertTrue(VrScopeRenderRequestCapture.capture(
                1L, DIMENSION_ID, GUN_ID, SCOPE_ID, 0,
                null, identity, 4.0D, 1.0D
        ).isEmpty());
        assertTrue(VrScopeRenderRequestCapture.capture(
                1L, DIMENSION_ID, GUN_ID, SCOPE_ID, 0,
                new Matrix4f().m00(Float.NaN), identity, 4.0D, 1.0D
        ).isEmpty());
    }

    private static Vec3 rotate(VrRotation rotation, Vec3 vector) {
        org.joml.Vector3f result = rotation.toQuaternion().transform(new org.joml.Vector3f(
                (float) vector.x,
                (float) vector.y,
                (float) vector.z
        ));
        return new Vec3(result.x, result.y, result.z);
    }
}
