package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Optional;

/**
 * Read-only reconstruction of the authored {@code scope_view -> division} cone on the rear ring plane and the
 * current-eye DIVISION/ocular intersection on TACZ 1.1.8's fixed circle plane. No live render state or model
 * transform is changed; the latter projection is the final aperture-radius evidence used by the physical gate.
 */
record VrScopeDivisionProjectionSample(
        long frameIndex,
        ResourceLocation gunId,
        ResourceLocation scopeId,
        int viewIndex,
        boolean ocularPlaneFallback,
        boolean ringBetweenAuthorAndDivision,
        double authorToRingDistance,
        double ringToDivisionDistance,
        double authorToDivisionDistance,
        double centerProjectionRatio,
        PlanePoint corner0,
        PlanePoint corner1,
        PlanePoint corner2,
        PlanePoint corner3,
        PlanePoint currentCorner0,
        PlanePoint currentCorner1,
        PlanePoint currentCorner2,
        PlanePoint currentCorner3,
        PlanePoint currentPupil,
        PlanePoint authorDivisionPupil,
        PlanePoint currentDivisionPupil,
        double authorDivisionPupilRadius,
        double currentDivisionPupilRadius,
        boolean ringBetweenCurrentEyeAndDivision,
        boolean insideDivisionSightPupil,
        double projectedHalfWidthToOcularRatio,
        double projectedHalfHeightToOcularRatio,
        double currentSafeOcularRadiusScale,
        double currentSafeTaczRadiusModifier,
        PlanePoint currentTaczCircleCenter,
        boolean forwardEyeIntersection,
        boolean insideProjectedDivision
) {
    private static final Vec3 EYE_FORWARD = new Vec3(0.0D, 0.0D, -1.0D);
    private static final double EPSILON = 1.0E-8D;

    static Optional<VrScopeDivisionProjectionSample> measured(
            long frameIndex,
            ResourceLocation gunId,
            ResourceLocation scopeId,
            int viewIndex,
            VrScopeModelAperture.Transformed ocular,
            VrScopeModelAperture.Transformed ringPlane,
            VrScopeModelAperture.Transformed division,
            Vec3 authorEye,
            boolean ocularPlaneFallback
    ) {
        return measured(
                frameIndex,
                gunId,
                scopeId,
                viewIndex,
                ocular,
                ringPlane,
                division,
                authorEye,
                ocularPlaneFallback,
                ocular == null ? null : ocular.center(),
                new Matrix4f()
        );
    }

    static Optional<VrScopeDivisionProjectionSample> measured(
            long frameIndex,
            ResourceLocation gunId,
            ResourceLocation scopeId,
            int viewIndex,
            VrScopeModelAperture.Transformed ocular,
            VrScopeModelAperture.Transformed ringPlane,
            VrScopeModelAperture.Transformed division,
            Vec3 authorEye,
            boolean ocularPlaneFallback,
            Vec3 ocularPivot,
            Matrix4f modelViewMatrix
    ) {
        if (frameIndex < 0L || viewIndex < 0 || gunId == null || scopeId == null
                || ocular == null || ringPlane == null || division == null
                || !isFinite(authorEye)
                || !isFinite(ocular.center()) || !isFinite(ocular.right())
                || !isFinite(ocular.up()) || !isFinite(ocular.axis())
                || !isFinite(ringPlane.center()) || !isFinite(ringPlane.right())
                || !isFinite(ringPlane.up()) || !isFinite(ringPlane.axis())
                || !isFinite(division.center()) || !isFinite(division.right())
                || !isFinite(division.up()) || !isFinite(division.axis())) {
            return Optional.empty();
        }

        Vec3 normal = normalize(ringPlane.axis());
        Vec3 right = orthogonalize(ringPlane.right(), normal);
        Vec3 up = orthogonalize(ringPlane.up(), normal, right);
        Vec3 divisionNormal = normalize(division.axis());
        Vec3 divisionRight = orthogonalize(division.right(), divisionNormal);
        Vec3 divisionUp = orthogonalize(division.up(), divisionNormal, divisionRight);
        if (normal == null || right == null || up == null
                || divisionNormal == null || divisionRight == null || divisionUp == null
                || !positiveFinite(ocular.radiusX()) || !positiveFinite(ocular.radiusY())
                || !positiveFinite(division.radiusX()) || !positiveFinite(division.radiusY())) {
            return Optional.empty();
        }

        List<Vec3> divisionCorners = List.of(
                division.center().add(divisionRight.scale(-division.radiusX())).add(divisionUp.scale(-division.radiusY())),
                division.center().add(divisionRight.scale(division.radiusX())).add(divisionUp.scale(-division.radiusY())),
                division.center().add(divisionRight.scale(division.radiusX())).add(divisionUp.scale(division.radiusY())),
                division.center().add(divisionRight.scale(-division.radiusX())).add(divisionUp.scale(division.radiusY()))
        );
        PlaneIntersection center = project(authorEye, division.center(), ringPlane.center(), normal, right, up);
        PlaneIntersection first = project(authorEye, divisionCorners.get(0), ringPlane.center(), normal, right, up);
        PlaneIntersection second = project(authorEye, divisionCorners.get(1), ringPlane.center(), normal, right, up);
        PlaneIntersection third = project(authorEye, divisionCorners.get(2), ringPlane.center(), normal, right, up);
        PlaneIntersection fourth = project(authorEye, divisionCorners.get(3), ringPlane.center(), normal, right, up);
        PlaneIntersection currentFirst = project(
                Vec3.ZERO, divisionCorners.get(0), ringPlane.center(), normal, right, up
        );
        PlaneIntersection currentSecond = project(
                Vec3.ZERO, divisionCorners.get(1), ringPlane.center(), normal, right, up
        );
        PlaneIntersection currentThird = project(
                Vec3.ZERO, divisionCorners.get(2), ringPlane.center(), normal, right, up
        );
        PlaneIntersection currentFourth = project(
                Vec3.ZERO, divisionCorners.get(3), ringPlane.center(), normal, right, up
        );
        if (center == null || first == null || second == null || third == null || fourth == null
                || currentFirst == null || currentSecond == null
                || currentThird == null || currentFourth == null) {
            return Optional.empty();
        }

        PlaneIntersection eye = intersectRay(Vec3.ZERO, EYE_FORWARD, ringPlane.center(), normal, right, up);
        boolean forwardEyeIntersection = eye != null && eye.ratio() > 0.0D;
        PlanePoint pupil = forwardEyeIntersection
                ? eye.point()
                : new PlanePoint(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        List<PlanePoint> polygon = List.of(first.point(), second.point(), third.point(), fourth.point());
        double minU = polygon.stream().mapToDouble(PlanePoint::u).min().orElse(Double.NaN);
        double maxU = polygon.stream().mapToDouble(PlanePoint::u).max().orElse(Double.NaN);
        double minV = polygon.stream().mapToDouble(PlanePoint::v).min().orElse(Double.NaN);
        double maxV = polygon.stream().mapToDouble(PlanePoint::v).max().orElse(Double.NaN);
        double projectedHalfWidth = (maxU - minU) * 0.5D;
        double projectedHalfHeight = (maxV - minV) * 0.5D;
        if (!positiveFinite(projectedHalfWidth) || !positiveFinite(projectedHalfHeight)) {
            return Optional.empty();
        }

        boolean ringBetween = between(first.ratio()) && between(second.ratio())
                && between(third.ratio()) && between(fourth.ratio());
        boolean inside = forwardEyeIntersection && ringBetween && containsConvex(polygon, pupil);
        PlaneIntersection currentDivision = project(
                Vec3.ZERO,
                division.center(),
                ringPlane.center(),
                normal,
                right,
                up
        );
        PlanePoint ocularCenter = new PlanePoint(
                ocular.center().subtract(ringPlane.center()).dot(right),
                ocular.center().subtract(ringPlane.center()).dot(up)
        );
        List<PlanePoint> currentPolygon = List.of(
                currentFirst.point(), currentSecond.point(), currentThird.point(), currentFourth.point()
        );
        boolean currentCornersBetween = between(currentFirst.ratio()) && between(currentSecond.ratio())
                && between(currentThird.ratio()) && between(currentFourth.ratio());
        double currentSafeOcularRadiusScale = currentCornersBetween
                ? centeredNormalizedInradius(currentPolygon, ocularCenter, ocular)
                : 0.0D;
        double authorDivisionPupilRadius = normalizedRadius(center.point(), ocularCenter, ocular);
        double currentDivisionPupilRadius = currentDivision == null
                ? Double.POSITIVE_INFINITY
                : normalizedRadius(currentDivision.point(), ocularCenter, ocular);
        boolean ringBetweenCurrentEyeAndDivision = currentDivision != null && between(currentDivision.ratio());
        boolean insideDivisionSightPupil = ringBetween
                && ringBetweenCurrentEyeAndDivision
                && Double.isFinite(currentDivisionPupilRadius)
                && currentDivisionPupilRadius <= 1.0D;
        Optional<VrScopeTaczCircleProjection> taczCircle = VrScopeTaczCircleProjection.measured(
                modelViewMatrix,
                ocularPivot,
                ocular,
                division
        );
        double currentSafeTaczRadiusModifier = taczCircle
                .map(VrScopeTaczCircleProjection::safeRadiusModifier)
                .orElse(0.0D);
        PlanePoint currentTaczCircleCenter = taczCircle
                .map(candidate -> new PlanePoint(candidate.planeCenterX(), candidate.planeCenterY()))
                .orElseGet(() -> new PlanePoint(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        return Optional.of(new VrScopeDivisionProjectionSample(
                frameIndex,
                gunId,
                scopeId,
                viewIndex,
                ocularPlaneFallback,
                ringBetween,
                authorEye.distanceTo(ringPlane.center()),
                ringPlane.center().distanceTo(division.center()),
                authorEye.distanceTo(division.center()),
                center.ratio(),
                first.point(),
                second.point(),
                third.point(),
                fourth.point(),
                currentFirst.point(),
                currentSecond.point(),
                currentThird.point(),
                currentFourth.point(),
                pupil,
                center.point(),
                currentDivision == null
                        ? new PlanePoint(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
                        : currentDivision.point(),
                authorDivisionPupilRadius,
                currentDivisionPupilRadius,
                ringBetweenCurrentEyeAndDivision,
                insideDivisionSightPupil,
                projectedHalfWidth / ocular.radiusX(),
                projectedHalfHeight / ocular.radiusY(),
                currentSafeOcularRadiusScale,
                currentSafeTaczRadiusModifier,
                currentTaczCircleCenter,
                forwardEyeIntersection,
                inside
        ));
    }

    /**
     * Returns the largest ocular-centered ellipse scale that is completely contained by the current-eye projection
     * of DIVISION. Coordinates are normalized by the physical ocular radii, turning that ellipse into a circle;
     * the minimum origin-to-edge distance of the convex polygon is therefore the safe radius scale.
     */
    private static double centeredNormalizedInradius(
            List<PlanePoint> polygon,
            PlanePoint ocularCenter,
            VrScopeModelAperture.Transformed ocular
    ) {
        if (polygon == null || polygon.size() < 3 || ocularCenter == null || !ocularCenter.isFinite()
                || !positiveFinite(ocular.radiusX()) || !positiveFinite(ocular.radiusY())) {
            return 0.0D;
        }
        List<PlanePoint> normalized = polygon.stream()
                .map(point -> new PlanePoint(
                        (point.u - ocularCenter.u) / ocular.radiusX(),
                        (point.v - ocularCenter.v) / ocular.radiusY()
                ))
                .toList();
        PlanePoint origin = new PlanePoint(0.0D, 0.0D);
        if (!containsConvex(normalized, origin)) {
            return 0.0D;
        }

        double inradius = Double.POSITIVE_INFINITY;
        for (int index = 0; index < normalized.size(); index++) {
            PlanePoint first = normalized.get(index);
            PlanePoint second = normalized.get((index + 1) % normalized.size());
            double edgeU = second.u - first.u;
            double edgeV = second.v - first.v;
            double edgeLength = Math.hypot(edgeU, edgeV);
            if (!positiveFinite(edgeLength)) {
                return 0.0D;
            }
            double distance = Math.abs(edgeU * first.v - edgeV * first.u) / edgeLength;
            inradius = Math.min(inradius, distance);
        }
        return Double.isFinite(inradius) ? Math.max(0.0D, inradius) : 0.0D;
    }

    private static double normalizedRadius(
            PlanePoint point,
            PlanePoint center,
            VrScopeModelAperture.Transformed ocular
    ) {
        if (point == null || center == null || !point.isFinite() || !center.isFinite()
                || !positiveFinite(ocular.radiusX()) || !positiveFinite(ocular.radiusY())) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.hypot(
                (point.u - center.u) / ocular.radiusX(),
                (point.v - center.v) / ocular.radiusY()
        );
    }

    double projectedWidth() {
        return maxU() - minU();
    }

    double projectedHeight() {
        return maxV() - minV();
    }

    double projectedCenterU() {
        return (minU() + maxU()) * 0.5D;
    }

    double projectedCenterV() {
        return (minV() + maxV()) * 0.5D;
    }

    double currentProjectedWidth() {
        return currentMaxU() - currentMinU();
    }

    double currentProjectedHeight() {
        return currentMaxV() - currentMinV();
    }

    double currentProjectedCenterU() {
        return (currentMinU() + currentMaxU()) * 0.5D;
    }

    double currentProjectedCenterV() {
        return (currentMinV() + currentMaxV()) * 0.5D;
    }

    private double minU() {
        return Math.min(Math.min(this.corner0.u, this.corner1.u), Math.min(this.corner2.u, this.corner3.u));
    }

    private double maxU() {
        return Math.max(Math.max(this.corner0.u, this.corner1.u), Math.max(this.corner2.u, this.corner3.u));
    }

    private double minV() {
        return Math.min(Math.min(this.corner0.v, this.corner1.v), Math.min(this.corner2.v, this.corner3.v));
    }

    private double maxV() {
        return Math.max(Math.max(this.corner0.v, this.corner1.v), Math.max(this.corner2.v, this.corner3.v));
    }

    private double currentMinU() {
        return Math.min(
                Math.min(this.currentCorner0.u, this.currentCorner1.u),
                Math.min(this.currentCorner2.u, this.currentCorner3.u)
        );
    }

    private double currentMaxU() {
        return Math.max(
                Math.max(this.currentCorner0.u, this.currentCorner1.u),
                Math.max(this.currentCorner2.u, this.currentCorner3.u)
        );
    }

    private double currentMinV() {
        return Math.min(
                Math.min(this.currentCorner0.v, this.currentCorner1.v),
                Math.min(this.currentCorner2.v, this.currentCorner3.v)
        );
    }

    private double currentMaxV() {
        return Math.max(
                Math.max(this.currentCorner0.v, this.currentCorner1.v),
                Math.max(this.currentCorner2.v, this.currentCorner3.v)
        );
    }

    private static PlaneIntersection project(
            Vec3 origin,
            Vec3 target,
            Vec3 planeCenter,
            Vec3 planeNormal,
            Vec3 planeRight,
            Vec3 planeUp
    ) {
        return intersectRay(origin, target.subtract(origin), planeCenter, planeNormal, planeRight, planeUp);
    }

    private static PlaneIntersection intersectRay(
            Vec3 origin,
            Vec3 direction,
            Vec3 planeCenter,
            Vec3 planeNormal,
            Vec3 planeRight,
            Vec3 planeUp
    ) {
        double denominator = direction.dot(planeNormal);
        if (!Double.isFinite(denominator) || Math.abs(denominator) < EPSILON) {
            return null;
        }
        double ratio = planeCenter.subtract(origin).dot(planeNormal) / denominator;
        if (!Double.isFinite(ratio)) {
            return null;
        }
        Vec3 delta = origin.add(direction.scale(ratio)).subtract(planeCenter);
        PlanePoint point = new PlanePoint(delta.dot(planeRight), delta.dot(planeUp));
        return point.isFinite() ? new PlaneIntersection(point, ratio) : null;
    }

    private static boolean containsConvex(List<PlanePoint> polygon, PlanePoint point) {
        double sign = 0.0D;
        for (int index = 0; index < polygon.size(); index++) {
            PlanePoint first = polygon.get(index);
            PlanePoint second = polygon.get((index + 1) % polygon.size());
            double cross = (second.u - first.u) * (point.v - first.v)
                    - (second.v - first.v) * (point.u - first.u);
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

    private static boolean between(double ratio) {
        return ratio > EPSILON && ratio < 1.0D - EPSILON;
    }

    private static Vec3 orthogonalize(Vec3 vector, Vec3... bases) {
        if (!isFinite(vector)) {
            return null;
        }
        Vec3 result = vector;
        for (Vec3 basis : bases) {
            if (basis == null) {
                return null;
            }
            result = result.subtract(basis.scale(result.dot(basis)));
        }
        return normalize(result);
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

    private static boolean isFinite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    record PlanePoint(double u, double v) {
        private boolean isFinite() {
            return Double.isFinite(this.u) && Double.isFinite(this.v);
        }
    }

    private record PlaneIntersection(PlanePoint point, double ratio) {
    }
}
