package com.thedivisionisac.taczvr.client.vr;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Couples angular tolerance to the authored eye relief. The ordinary pupil test becomes increasingly permissive as
 * an eye approaches the ocular plane, because the same lens radius subtends a wider angle. Below the authored eye
 * distance this gate instead retains the author's optical cone, then smoothly releases the additional restriction
 * before the eye returns to the ordinary authored region.
 */
final class VrScopeNearEyeAngleGate {
    static final double FULL_STRICT_RELIEF_RATIO = 0.60D;
    static final double RELEASED_RELIEF_RATIO = 0.85D;
    static final double FULL_ANGLE_CONE_MULTIPLIER = 0.55D;
    static final double CLOSED_ANGLE_CONE_MULTIPLIER = 0.90D;

    private VrScopeNearEyeAngleGate() {
    }

    static double progressFor(
            @Nullable VrScopeAlignmentSample alignment,
            @Nullable VrScopeAuthorEyeBoxSample eyeBox
    ) {
        if (!matchesSource(alignment, eyeBox)) {
            return 0.0D;
        }
        return progress(alignment, eyeBox);
    }

    static double progress(VrScopeAlignmentSample alignment, VrScopeAuthorEyeBoxSample eyeBox) {
        double strictWeight = strictWeight(eyeBox);
        double strictAngleProgress = strictAngleProgress(eyeBox);
        if (!Double.isFinite(strictWeight) || !Double.isFinite(strictAngleProgress)) {
            return 0.0D;
        }
        return 1.0D - strictWeight * (1.0D - strictAngleProgress);
    }

    static double eyeReliefRatio(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        if (eyeBox == null
                || !Double.isFinite(eyeBox.currentEyeRelief())
                || !Double.isFinite(eyeBox.authorEyeRelief())
                || eyeBox.currentEyeRelief() <= 0.0D
                || eyeBox.authorEyeRelief() <= 0.0D) {
            return Double.NaN;
        }
        return eyeBox.currentEyeRelief() / eyeBox.authorEyeRelief();
    }

    static double strictWeight(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        double ratio = eyeReliefRatio(eyeBox);
        if (!Double.isFinite(ratio) || ratio <= 0.0D) {
            return Double.NaN;
        }
        if (ratio <= FULL_STRICT_RELIEF_RATIO) {
            return 1.0D;
        }
        if (ratio >= RELEASED_RELIEF_RATIO) {
            return 0.0D;
        }
        return descendingFalloff(ratio, FULL_STRICT_RELIEF_RATIO, RELEASED_RELIEF_RATIO);
    }

    static double ocularEccentricAngleDegrees(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        if (eyeBox == null
                || !Double.isFinite(eyeBox.currentEyeRelief())
                || eyeBox.currentEyeRelief() <= 0.0D
                || !Double.isFinite(eyeBox.pupilU())
                || !Double.isFinite(eyeBox.pupilV())
                || !Double.isFinite(eyeBox.apertureRadiusX())
                || !Double.isFinite(eyeBox.apertureRadiusY())
                || eyeBox.apertureRadiusX() <= 0.0D
                || eyeBox.apertureRadiusY() <= 0.0D) {
            return Double.NaN;
        }
        double lateralDistance = Math.hypot(
                eyeBox.pupilU() * eyeBox.apertureRadiusX(),
                eyeBox.pupilV() * eyeBox.apertureRadiusY()
        );
        return Double.isFinite(lateralDistance)
                ? Math.toDegrees(Math.atan2(lateralDistance, eyeBox.currentEyeRelief()))
                : Double.NaN;
    }

    static double strictAngleProgress(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        double angle = ocularEccentricAngleDegrees(eyeBox);
        if (!Double.isFinite(angle)
                || eyeBox == null
                || !Double.isFinite(eyeBox.authorConeHalfAngleDegrees())
                || eyeBox.authorConeHalfAngleDegrees() <= 0.0D) {
            return Double.NaN;
        }
        double fullThrough = eyeBox.authorConeHalfAngleDegrees() * FULL_ANGLE_CONE_MULTIPLIER;
        double closedAt = eyeBox.authorConeHalfAngleDegrees() * CLOSED_ANGLE_CONE_MULTIPLIER;
        if (angle >= closedAt) {
            return 0.0D;
        }
        return descendingFalloff(angle, fullThrough, closedAt);
    }

    private static boolean matchesSource(
            @Nullable VrScopeAlignmentSample alignment,
            @Nullable VrScopeAuthorEyeBoxSample eyeBox
    ) {
        return alignment != null && eyeBox != null
                && alignment.frameIndex() == eyeBox.frameIndex()
                && alignment.viewIndex() == eyeBox.viewIndex()
                && Objects.equals(alignment.gunId(), eyeBox.gunId())
                && Objects.equals(alignment.scopeId(), eyeBox.scopeId());
    }

    private static double descendingFalloff(double value, double fullThrough, double closedAt) {
        if (value <= fullThrough) {
            return 1.0D;
        }
        double normalized = Math.max(0.0D, Math.min(1.0D, (closedAt - value) / (closedAt - fullThrough)));
        return normalized * normalized * (3.0D - 2.0D * normalized);
    }
}
