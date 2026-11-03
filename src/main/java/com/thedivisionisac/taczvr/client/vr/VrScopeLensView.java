package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Current-eye, camera-space image plane anchored to the authored division center. The full unshrunk ocular bounds
 * choose an overscanned angular field; the existing stencil alone animates the aperture. Projection and composition
 * use the SAME captured field, so distance and aperture animation do not multiply the selected magnification.
 */
public record VrScopeLensView(Vec3 center, VrRotation rotation, double halfTan) {
    private static final double OVERSCAN = 2.5D;

    static Optional<VrScopeLensView> measured(Matrix4f modelView, Matrix4f scopeView,
                                             VrScopeModelAperture.Transformed ocular,
                                             VrScopeModelAperture.Transformed division) {
        if (modelView == null || scopeView == null || ocular == null || division == null
                || !modelView.isFinite() || !scopeView.isFinite()) return Optional.empty();
        Vector3f center = modelView.transformPosition(VrScopeWorldView.vector(division.center()));
        Vector3f lens = modelView.transformPosition(VrScopeWorldView.vector(ocular.center()));
        Optional<VrRotation> orientation = VrRotation.validated(
                new Matrix4f(modelView).mul(scopeView).getUnnormalizedRotation(new Quaternionf())
        );
        Vector3f right = modelView.transformDirection(VrScopeWorldView.vector(ocular.right()).normalize());
        Vector3f up = modelView.transformDirection(VrScopeWorldView.vector(ocular.up()).normalize());
        double radius = Math.max(ocular.radiusX() * right.length(), ocular.radiusY() * up.length());
        double halfTan = OVERSCAN * radius / -lens.z;
        if (!center.isFinite() || !lens.isFinite() || center.z >= -1.0E-4F || lens.z >= -1.0E-4F
                || orientation.isEmpty() || !VrScopeWorldView.validHalfTan(halfTan)) return Optional.empty();
        return Optional.of(new VrScopeLensView(new Vec3(center.x, center.y, center.z), orientation.get(), halfTan));
    }

    /** Bottom-left, bottom-right, top-right, top-left. Texture v=0 is the bottom of an OpenGL render target. */
    public List<Vec3> corners(double capturedHalfTan) {
        if (!VrScopeWorldView.validHalfTan(capturedHalfTan) || !VrScopeWorldView.finite(this.center)
                || this.center.z >= -1.0E-4D || this.rotation == null) return List.of();
        float size = (float) (-this.center.z * capturedHalfTan);
        Quaternionf orientation = this.rotation.toQuaternion();
        Vector3f right = orientation.transform(new Vector3f(size, 0, 0));
        Vector3f up = orientation.transform(new Vector3f(0, size, 0));
        Vec3 r = new Vec3(right.x, right.y, right.z);
        Vec3 u = new Vec3(up.x, up.y, up.z);
        List<Vec3> corners = List.of(this.center.subtract(r).subtract(u), this.center.add(r).subtract(u),
                this.center.add(r).add(u), this.center.subtract(r).add(u));
        return corners.stream().allMatch(v -> VrScopeWorldView.finite(v) && v.z < -1.0E-4D)
                ? corners : List.of();
    }
}
