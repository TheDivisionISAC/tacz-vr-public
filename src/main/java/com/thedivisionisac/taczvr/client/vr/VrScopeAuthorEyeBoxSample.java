package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Read-only comparison between the current render eye, TACZ's authored {@code scope_view}, and the actual ocular
 * stencil geometry. It intentionally does not produce an aperture multiplier.
 */
record VrScopeAuthorEyeBoxSample(
        long frameIndex,
        ResourceLocation gunId,
        ResourceLocation scopeId,
        int viewIndex,
        double authorEyeRelief,
        double currentEyeRelief,
        double authorEyeAxialError,
        double authorEyeLateralError,
        double apertureRadiusX,
        double apertureRadiusY,
        double authorConeHalfAngleDegrees,
        double authorRayAxisAngleDegrees,
        double authorViewAxisAngleDegrees,
        boolean forwardIntersection,
        double pupilU,
        double pupilV,
        double pupilRadius,
        double pupilMargin,
        boolean insideAperture
) {
    private static final Vec3 EYE_FORWARD = new Vec3(0.0D, 0.0D, -1.0D);
    private static final double MINIMUM_LENGTH_SQUARED = 1.0E-12D;
    private static final double MINIMUM_DENOMINATOR = 1.0E-6D;

    static Optional<VrScopeAuthorEyeBoxSample> measured(
            long frameIndex,
            ResourceLocation gunId,
            ResourceLocation scopeId,
            int viewIndex,
            VrScopeModelAperture.Transformed aperture,
            Vec3 authorEyePosition,
            Vec3 authorViewAxis
    ) {
        if (frameIndex < 0L || viewIndex < 0 || gunId == null || scopeId == null || aperture == null
                || authorEyePosition == null || authorViewAxis == null
                || !isFinite(aperture.center()) || !isFinite(aperture.right())
                || !isFinite(aperture.up()) || !isFinite(aperture.axis())
                || !isFinite(authorEyePosition) || !isFinite(authorViewAxis)
                || !Double.isFinite(aperture.radiusX()) || !Double.isFinite(aperture.radiusY())
                || aperture.radiusX() <= 0.0D || aperture.radiusY() <= 0.0D) {
            return Optional.empty();
        }

        Vec3 axis = normalize(aperture.axis());
        Vec3 right = orthogonalize(aperture.right(), axis);
        Vec3 up = orthogonalize(aperture.up(), axis, right);
        Vec3 viewAxis = normalize(authorViewAxis);
        if (axis == null || right == null || up == null || viewAxis == null) {
            return Optional.empty();
        }

        Vec3 authorToOcular = aperture.center().subtract(authorEyePosition);
        double authorEyeRelief = authorToOcular.dot(axis);
        if (!Double.isFinite(authorEyeRelief) || authorEyeRelief <= 0.0D) {
            return Optional.empty();
        }
        double currentEyeRelief = aperture.center().dot(axis);
        double authorEyeAxialError = authorEyePosition.dot(axis);
        double authorEyeLateralSquared = Math.max(
                0.0D,
                authorEyePosition.lengthSqr() - authorEyeAxialError * authorEyeAxialError
        );
        double authorEyeLateralError = Math.sqrt(authorEyeLateralSquared);
        Vec3 authorRay = normalize(authorToOcular);
        if (authorRay == null) {
            return Optional.empty();
        }

        double denominator = EYE_FORWARD.dot(axis);
        double intersectionDistance = Math.abs(denominator) < MINIMUM_DENOMINATOR
                ? Double.NaN
                : aperture.center().dot(axis) / denominator;
        boolean forwardIntersection = Double.isFinite(intersectionDistance) && intersectionDistance > 0.0D;
        double pupilU = Double.POSITIVE_INFINITY;
        double pupilV = Double.POSITIVE_INFINITY;
        double pupilRadius = Double.POSITIVE_INFINITY;
        double pupilMargin = Double.NEGATIVE_INFINITY;
        boolean insideAperture = false;
        if (forwardIntersection) {
            Vec3 intersection = EYE_FORWARD.scale(intersectionDistance);
            Vec3 delta = intersection.subtract(aperture.center());
            pupilU = delta.dot(right) / aperture.radiusX();
            pupilV = delta.dot(up) / aperture.radiusY();
            pupilRadius = Math.hypot(pupilU, pupilV);
            pupilMargin = 1.0D - pupilRadius;
            insideAperture = Double.isFinite(pupilRadius) && pupilRadius <= 1.0D;
        }

        return Optional.of(new VrScopeAuthorEyeBoxSample(
                frameIndex,
                gunId,
                scopeId,
                viewIndex,
                authorEyeRelief,
                currentEyeRelief,
                authorEyeAxialError,
                authorEyeLateralError,
                aperture.radiusX(),
                aperture.radiusY(),
                Math.toDegrees(Math.atan2(
                        Math.min(aperture.radiusX(), aperture.radiusY()),
                        authorEyeRelief
                )),
                angleDegrees(authorRay, axis),
                angleDegrees(viewAxis, axis),
                forwardIntersection,
                pupilU,
                pupilV,
                pupilRadius,
                pupilMargin,
                insideAperture
        ));
    }

    private static Vec3 orthogonalize(Vec3 vector, Vec3... bases) {
        Vec3 result = vector;
        for (Vec3 basis : bases) {
            result = result.subtract(basis.scale(result.dot(basis)));
        }
        return normalize(result);
    }

    private static Vec3 normalize(Vec3 vector) {
        double lengthSquared = vector.lengthSqr();
        if (!Double.isFinite(lengthSquared) || lengthSquared < MINIMUM_LENGTH_SQUARED) {
            return null;
        }
        return vector.scale(1.0D / Math.sqrt(lengthSquared));
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static double angleDegrees(Vec3 first, Vec3 second) {
        double dot = Math.max(-1.0D, Math.min(1.0D, first.dot(second)));
        return Math.toDegrees(Math.acos(dot));
    }
}
