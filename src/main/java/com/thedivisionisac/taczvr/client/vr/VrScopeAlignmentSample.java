package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Immutable, render-only measurement of one TACZ scope view node relative to the HMD center. It contains facts for a
 * future optical gate but intentionally owns no thresholds and cannot hide lenses or request an extra world render.
 */
public record VrScopeAlignmentSample(
        long frameIndex,
        ResourceLocation gunId,
        ResourceLocation scopeId,
        int viewIndex,
        Vec3 lensPosition,
        Vec3 opticalAxis,
        double lensDistance,
        double viewForwardDistance,
        double viewLateralOffset,
        double opticalAxialDistance,
        double opticalLateralOffset,
        double viewCenterAngleDegrees,
        double opticalAxisAngleDegrees,
        double coaxialAngleDegrees
) {
    // Vivecraft's public pose direction is the device-local BACK axis, i.e. negative Z in the HMD-local basis.
    private static final Vec3 HEAD_FORWARD = new Vec3(0.0D, 0.0D, -1.0D);
    private static final double MINIMUM_LENGTH_SQUARED = 1.0E-12D;

    public static Optional<VrScopeAlignmentSample> measured(
            long frameIndex,
            ResourceLocation gunId,
            ResourceLocation scopeId,
            int viewIndex,
            Vec3 lensPosition,
            Vec3 opticalAxis
    ) {
        if (frameIndex < 0L || viewIndex < 0 || gunId == null || scopeId == null
                || lensPosition == null || opticalAxis == null
                || !isFinite(lensPosition) || !isFinite(opticalAxis)) {
            return Optional.empty();
        }
        double lensLengthSquared = lensPosition.lengthSqr();
        double axisLengthSquared = opticalAxis.lengthSqr();
        if (!Double.isFinite(lensLengthSquared) || !Double.isFinite(axisLengthSquared)
                || lensLengthSquared < MINIMUM_LENGTH_SQUARED
                || axisLengthSquared < MINIMUM_LENGTH_SQUARED) {
            return Optional.empty();
        }

        double lensDistance = Math.sqrt(lensLengthSquared);
        Vec3 lensDirection = lensPosition.scale(1.0D / lensDistance);
        Vec3 normalizedAxis = opticalAxis.scale(1.0D / Math.sqrt(axisLengthSquared));
        double viewForwardDistance = lensPosition.dot(HEAD_FORWARD);
        double viewLateralOffset = Math.hypot(lensPosition.x, lensPosition.y);
        double opticalAxialDistance = lensPosition.dot(normalizedAxis);
        double opticalLateralSquared = Math.max(
                0.0D,
                lensLengthSquared - opticalAxialDistance * opticalAxialDistance
        );

        return Optional.of(new VrScopeAlignmentSample(
                frameIndex,
                gunId,
                scopeId,
                viewIndex,
                lensPosition,
                normalizedAxis,
                lensDistance,
                viewForwardDistance,
                viewLateralOffset,
                opticalAxialDistance,
                Math.sqrt(opticalLateralSquared),
                angleDegrees(lensDirection, HEAD_FORWARD),
                angleDegrees(normalizedAxis, HEAD_FORWARD),
                angleDegrees(lensDirection, normalizedAxis)
        ));
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static double angleDegrees(Vec3 first, Vec3 second) {
        double dot = Math.max(-1.0D, Math.min(1.0D, first.dot(second)));
        return Math.toDegrees(Math.acos(dot));
    }
}
