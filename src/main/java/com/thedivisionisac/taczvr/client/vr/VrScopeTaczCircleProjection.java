package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Reconstructs TACZ 1.1.8's fixed {@code z=-90} ocular-circle plane in the same pre-model-view coordinates used by
 * {@code BedrockAttachmentModel}. The current camera ray through the animated ocular pivot supplies the corrected
 * circle center. The visible aperture is TACZ's circle intersected with its ocular stencil, so DIVISION bounds that
 * fully cover the projected ocular preserve full field of view; otherwise the nearest DIVISION segment inside that
 * ocular supplies the largest centered circle that cannot expose pixels past the reticle geometry. This class only
 * evaluates immutable matrix/model facts and owns no render state.
 */
record VrScopeTaczCircleProjection(
        double planeCenterX,
        double planeCenterY,
        double safeRadiusModifier
) {
    static final double CIRCLE_PLANE_Z = -90.0D;
    static final double RADIUS_PER_MODIFIER = 80.0D;
    private static final double EPSILON = 1.0E-8D;

    static Optional<VrScopeTaczCircleProjection> measured(
            Matrix4f modelViewMatrix,
            Vec3 ocularPivot,
            VrScopeModelAperture.Transformed ocular,
            VrScopeModelAperture.Transformed division
    ) {
        if (modelViewMatrix == null) {
            return Optional.empty();
        }
        Matrix4f inverse = new Matrix4f(modelViewMatrix);
        double determinant = inverse.determinant();
        if (!Double.isFinite(determinant) || Math.abs(determinant) < EPSILON) {
            return Optional.empty();
        }
        inverse.invert();
        Vector3f camera = inverse.transformPosition(new Vector3f());
        return measured(
                new Vec3(camera.x, camera.y, camera.z),
                ocularPivot,
                ocular,
                division
        );
    }

    static Optional<VrScopeTaczCircleProjection> measured(
            Vec3 cameraPosition,
            Vec3 ocularPivot,
            VrScopeModelAperture.Transformed ocular,
            VrScopeModelAperture.Transformed division
    ) {
        if (!isFinite(cameraPosition) || !isFinite(ocularPivot) || ocular == null || division == null
                || !isFinite(ocular.center()) || !isFinite(ocular.right()) || !isFinite(ocular.up())
                || !positiveFinite(ocular.radiusX()) || !positiveFinite(ocular.radiusY())
                || !isFinite(division.center()) || !isFinite(division.right()) || !isFinite(division.up())
                || !positiveFinite(division.radiusX()) || !positiveFinite(division.radiusY())) {
            return Optional.empty();
        }
        List<Vec3> ocularCorners = corners(ocular);
        List<Vec3> divisionCorners = corners(division);
        if (ocularCorners == null || divisionCorners == null) {
            return Optional.empty();
        }

        PlanePoint center = projectToCirclePlane(cameraPosition, ocularPivot);
        if (center == null) {
            return Optional.empty();
        }
        List<PlanePoint> ocularPolygon = ocularCorners.stream()
                .map(corner -> projectToCirclePlane(cameraPosition, corner))
                .toList();
        List<PlanePoint> divisionPolygon = divisionCorners.stream()
                .map(corner -> projectToCirclePlane(cameraPosition, corner))
                .toList();
        if (ocularPolygon.stream().anyMatch(point -> point == null || !point.isFinite())
                || divisionPolygon.stream().anyMatch(point -> point == null || !point.isFinite())) {
            return Optional.empty();
        }

        double safeRadiusModifier = ocularPolygon.stream().allMatch(point -> containsConvex(divisionPolygon, point))
                ? 1.0D
                : safeIntersectionRadius(divisionPolygon, ocularPolygon, center) / RADIUS_PER_MODIFIER;
        return Optional.of(new VrScopeTaczCircleProjection(
                center.x,
                center.y,
                clamp01(safeRadiusModifier)
        ));
    }

    private static List<Vec3> corners(VrScopeModelAperture.Transformed aperture) {
        Vec3 right = normalize(aperture.right());
        Vec3 up = normalize(aperture.up());
        if (right == null || up == null) {
            return null;
        }
        return List.of(
                aperture.center().add(right.scale(-aperture.radiusX())).add(up.scale(-aperture.radiusY())),
                aperture.center().add(right.scale(aperture.radiusX())).add(up.scale(-aperture.radiusY())),
                aperture.center().add(right.scale(aperture.radiusX())).add(up.scale(aperture.radiusY())),
                aperture.center().add(right.scale(-aperture.radiusX())).add(up.scale(aperture.radiusY()))
        );
    }

    private static PlanePoint projectToCirclePlane(Vec3 origin, Vec3 target) {
        double deltaZ = target.z - origin.z;
        if (!Double.isFinite(deltaZ) || Math.abs(deltaZ) < EPSILON) {
            return null;
        }
        double ratio = (CIRCLE_PLANE_Z - origin.z) / deltaZ;
        if (!Double.isFinite(ratio) || ratio <= EPSILON) {
            return null;
        }
        double x = origin.x + (target.x - origin.x) * ratio;
        double y = origin.y + (target.y - origin.y) * ratio;
        return Double.isFinite(x) && Double.isFinite(y) ? new PlanePoint(x, y) : null;
    }

    private static double safeIntersectionRadius(
            List<PlanePoint> divisionPolygon,
            List<PlanePoint> ocularPolygon,
            PlanePoint center
    ) {
        if (!containsConvex(divisionPolygon, center) || !containsConvex(ocularPolygon, center)) {
            return 0.0D;
        }
        double safeRadius = Double.POSITIVE_INFINITY;
        for (int index = 0; index < divisionPolygon.size(); index++) {
            PlanePoint first = divisionPolygon.get(index);
            PlanePoint second = divisionPolygon.get((index + 1) % divisionPolygon.size());
            Segment clipped = clipToConvex(first, second, ocularPolygon);
            if (clipped != null) {
                safeRadius = Math.min(safeRadius, distanceToSegment(center, clipped.first, clipped.second));
            }
        }
        return Double.isFinite(safeRadius) ? Math.max(0.0D, safeRadius) : 0.0D;
    }

    private static Segment clipToConvex(PlanePoint first, PlanePoint second, List<PlanePoint> polygon) {
        double area = signedArea(polygon);
        if (!Double.isFinite(area) || Math.abs(area) < EPSILON) {
            return null;
        }
        double orientation = Math.signum(area);
        double deltaX = second.x - first.x;
        double deltaY = second.y - first.y;
        double minimum = 0.0D;
        double maximum = 1.0D;
        for (int index = 0; index < polygon.size(); index++) {
            PlanePoint edgeStart = polygon.get(index);
            PlanePoint edgeEnd = polygon.get((index + 1) % polygon.size());
            double edgeX = edgeEnd.x - edgeStart.x;
            double edgeY = edgeEnd.y - edgeStart.y;
            double constant = orientation * (edgeX * (first.y - edgeStart.y) - edgeY * (first.x - edgeStart.x));
            double gradient = orientation * (edgeX * deltaY - edgeY * deltaX);
            if (Math.abs(gradient) < EPSILON) {
                if (constant < -EPSILON) {
                    return null;
                }
                continue;
            }
            double boundary = -constant / gradient;
            if (gradient > 0.0D) {
                minimum = Math.max(minimum, boundary);
            } else {
                maximum = Math.min(maximum, boundary);
            }
            if (minimum > maximum + EPSILON) {
                return null;
            }
        }
        return new Segment(
                new PlanePoint(first.x + deltaX * minimum, first.y + deltaY * minimum),
                new PlanePoint(first.x + deltaX * maximum, first.y + deltaY * maximum)
        );
    }

    private static double distanceToSegment(PlanePoint point, PlanePoint first, PlanePoint second) {
        double deltaX = second.x - first.x;
        double deltaY = second.y - first.y;
        double lengthSquared = deltaX * deltaX + deltaY * deltaY;
        if (!positiveFinite(lengthSquared)) {
            return Math.hypot(point.x - first.x, point.y - first.y);
        }
        double ratio = ((point.x - first.x) * deltaX + (point.y - first.y) * deltaY) / lengthSquared;
        double bounded = Math.max(0.0D, Math.min(1.0D, ratio));
        return Math.hypot(
                point.x - (first.x + deltaX * bounded),
                point.y - (first.y + deltaY * bounded)
        );
    }

    private static double signedArea(List<PlanePoint> polygon) {
        double area = 0.0D;
        for (int index = 0; index < polygon.size(); index++) {
            PlanePoint first = polygon.get(index);
            PlanePoint second = polygon.get((index + 1) % polygon.size());
            area += first.x * second.y - second.x * first.y;
        }
        return area * 0.5D;
    }

    private static boolean containsConvex(List<PlanePoint> polygon, PlanePoint point) {
        double sign = 0.0D;
        for (int index = 0; index < polygon.size(); index++) {
            PlanePoint first = polygon.get(index);
            PlanePoint second = polygon.get((index + 1) % polygon.size());
            double cross = (second.x - first.x) * (point.y - first.y)
                    - (second.y - first.y) * (point.x - first.x);
            if (Math.abs(cross) <= EPSILON) {
                continue;
            }
            if (sign == 0.0D) {
                sign = Math.signum(cross);
            } else if (Math.signum(cross) != sign) {
                return false;
            }
        }
        return sign != 0.0D;
    }

    private static Vec3 normalize(Vec3 vector) {
        if (!isFinite(vector)) {
            return null;
        }
        double lengthSquared = vector.lengthSqr();
        if (!Double.isFinite(lengthSquared) || lengthSquared < EPSILON * EPSILON) {
            return null;
        }
        return vector.scale(1.0D / Math.sqrt(lengthSquared));
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > EPSILON;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static boolean isFinite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private record PlanePoint(double x, double y) {
        private boolean isFinite() {
            return Double.isFinite(this.x) && Double.isFinite(this.y);
        }
    }

    private record Segment(PlanePoint first, PlanePoint second) {
    }
}
