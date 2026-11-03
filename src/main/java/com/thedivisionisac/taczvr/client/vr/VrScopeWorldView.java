package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Immutable scope camera reconstructed from the previous capture and this frame's HMD. Captured local distances
 * already contain game world scale; apply it only when converting back into a tracking provider's room coordinates.
 * This pure adapter neither changes Vivecraft tracking nor the ordinary eye projection.
 */
public record VrScopeWorldView(Vec3 position, VrRotation rotation) {
    public static Optional<VrScopeWorldView> from(VrScopeRenderRequest request, Vec3 headPosition,
                                                 VrRotation headRotation) {
        if (request == null || !finite(headPosition) || headRotation == null) return Optional.empty();
        Quaternionf head = headRotation.toQuaternion();
        Vector3f offset = head.transform(vector(request.scopeViewPositionInHead()));
        Optional<VrRotation> rotation = VrRotation.validated(head.mul(request.scopeViewRotationInHead().toQuaternion()));
        Vec3 position = headPosition.add(offset.x, offset.y, offset.z);
        return finite(position) && rotation.isPresent()
                ? Optional.of(new VrScopeWorldView(position, rotation.get())) : Optional.empty();
    }

    public Optional<VrScopeWorldView> inTrackingSpace(Vec3 origin, float yaw, float scale) {
        if (!finite(origin) || !Float.isFinite(yaw) || !Float.isFinite(scale) || scale <= 0.0F) {
            return Optional.empty();
        }
        Vector3f local = vector(this.position.subtract(origin)).rotateY(-yaw).div(scale);
        Optional<VrRotation> localRotation = VrRotation.validated(
                new Quaternionf().rotationY(-yaw).mul(this.rotation.toQuaternion())
        );
        return local.isFinite() && localRotation.isPresent()
                ? Optional.of(new VrScopeWorldView(new Vec3(local.x, local.y, local.z), localRotation.get()))
                : Optional.empty();
    }

    /** Replace only the symmetric scope frustum's angular scale; preserve upstream near/far depth mapping. */
    public static Optional<Matrix4f> magnifiedProjection(Matrix4f original, double magnification, double halfTan) {
        if (original == null || !original.isFinite() || !Double.isFinite(magnification) || magnification <= 0.0D
                || !validHalfTan(halfTan) || Math.abs(original.m23() + 1.0F) > 1.0E-5F
                || Math.abs(original.m33()) > 1.0E-5F || Math.abs(original.m20()) > 1.0E-5F
                || Math.abs(original.m21()) > 1.0E-5F) return Optional.empty();
        double scale = magnification / halfTan;
        if (!Double.isFinite(scale) || scale > Float.MAX_VALUE) return Optional.empty();
        return Optional.of(new Matrix4f(original).m00((float) scale).m11((float) scale));
    }

    public static boolean validHalfTan(double halfTan) {
        return Double.isFinite(halfTan) && halfTan > 1.0E-5D && halfTan <= 2.0D;
    }

    static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    static Vector3f vector(Vec3 value) {
        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }
}
