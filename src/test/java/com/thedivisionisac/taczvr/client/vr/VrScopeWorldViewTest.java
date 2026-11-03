package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Camera-space reconstruction, tracking round trip and optical angular scale, independent of Vivecraft and GL. */
class VrScopeWorldViewTest {
    static VrScopeRenderRequest request(long frame) {
        return VrScopeRenderRequest.validated(frame, new ResourceLocation("minecraft:overworld"),
                new ResourceLocation("tacz:test"), new ResourceLocation("tacz:scope"), 0,
                new Vec3(0, 0, -0.25), new VrRotation(0, 0, 0, 1), 4, 0.25, 1).orElseThrow();
    }

    @Test
    void usesCurrentFullHeadPoseWithoutScalingCapturedOffsetsTwice() {
        VrRotation head = VrRotation.validated(new Quaternionf().rotationY((float) Math.PI / 2)).orElseThrow();
        VrScopeWorldView view = VrScopeWorldView.from(request(10), new Vec3(100, 50, -20), head).orElseThrow();
        assertEquals(99.75, view.position().x, 1.0E-5);
        assertEquals(50, view.position().y, 1.0E-5);
        assertEquals(-20, view.position().z, 1.0E-5);
        assertEquals(1, Math.abs(view.rotation().toQuaternion().dot(head.toQuaternion())), 1.0E-6);
    }

    @Test
    void trackingConversionRoundTripsTranslationYawScaleAndRoll() {
        VrScopeWorldView view = new VrScopeWorldView(new Vec3(100, 50, -20),
                VrRotation.validated(new Quaternionf().rotationXYZ(0.2F, -0.6F, 0.7F)).orElseThrow());
        Vec3 origin = new Vec3(98, 48, -24);
        float yaw = 0.9F;
        float scale = 3.0F;
        VrScopeWorldView local = view.inTrackingSpace(origin, yaw, scale).orElseThrow();
        Vector3f position = VrScopeWorldView.vector(local.position()).mul(scale).rotateY(yaw);
        assertEquals(view.position().x, origin.x + position.x, 1.0E-5);
        assertEquals(view.position().y, origin.y + position.y, 1.0E-5);
        assertEquals(view.position().z, origin.z + position.z, 1.0E-5);
        Quaternionf rotation = new Quaternionf().rotationY(yaw).mul(local.rotation().toQuaternion());
        assertEquals(1, Math.abs(rotation.dot(view.rotation().toQuaternion())), 1.0E-6);
        assertTrue(view.inTrackingSpace(origin, yaw, 0).isEmpty());
    }

    @Test
    void magnificationPreservesDepthAndDoesNotMutateTheOriginalProjection() {
        Matrix4f original = new Matrix4f().perspective((float) Math.toRadians(8.75), 1, 0.02F, 1024);
        Matrix4f before = new Matrix4f(original);
        Matrix4f projected = VrScopeWorldView.magnifiedProjection(original, 4, 0.25).orElseThrow();
        assertEquals(16, projected.m00(), 1.0E-6);
        assertEquals(16, projected.m11(), 1.0E-6);
        assertEquals(original.m22(), projected.m22());
        assertEquals(original.m32(), projected.m32());
        assertEquals(before, original);
        double baseFov = Math.toDegrees(2 * Math.atan(0.25));
        double taczFov = com.tacz.guns.util.math.MathUtil.magnificationToFov(4, baseFov);
        assertEquals(1 / Math.tan(Math.toRadians(taczFov / 2)), projected.m11(), 1.0E-5);
    }

    @Test
    void invalidOrOffAxisProjectionFailsClosed() {
        Matrix4f normal = new Matrix4f().perspective(1, 1, 0.02F, 100);
        assertTrue(VrScopeWorldView.magnifiedProjection(new Matrix4f(), 4, 0.25).isEmpty());
        assertTrue(VrScopeWorldView.magnifiedProjection(new Matrix4f(normal).m20(0.1F), 4, 0.25).isEmpty());
        for (double value : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertTrue(VrScopeWorldView.magnifiedProjection(normal, value, 0.25).isEmpty());
            assertTrue(VrScopeWorldView.magnifiedProjection(normal, 4, value).isEmpty());
        }
    }
}
