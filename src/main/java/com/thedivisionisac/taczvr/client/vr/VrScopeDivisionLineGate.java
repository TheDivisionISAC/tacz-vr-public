package com.thedivisionisac.taczvr.client.vr;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Limits the TACZ circle/ocular-stencil intersection to DIVISION's projection on TACZ 1.1.8's fixed circle plane.
 * DIVISION geometry that covers the complete ocular retains full field of view; an edge entering the ocular contracts
 * the opening continuously without item-specific thresholds.
 */
final class VrScopeDivisionLineGate {
    static final double SAFE_RADIUS_MARGIN = 0.95D;

    private VrScopeDivisionLineGate() {
    }

    static double progressFor(
            @Nullable VrScopeAlignmentSample alignment,
            @Nullable VrScopeDivisionProjectionSample projection
    ) {
        if (!matchesSource(alignment, projection)) {
            return 0.0D;
        }
        return progress(projection);
    }

    static double acquisitionProgressFor(
            @Nullable VrScopeAlignmentSample alignment,
            @Nullable VrScopeDivisionProjectionSample projection
    ) {
        if (!matchesSource(alignment, projection)) {
            return 0.0D;
        }
        return acquisitionProgress(projection);
    }

    static double progress(@Nullable VrScopeDivisionProjectionSample projection) {
        if (!isUsable(projection)) {
            return 0.0D;
        }
        double safeModifier = projection.currentSafeTaczRadiusModifier();
        return safeModifier >= 1.0D ? 1.0D : clamp01(safeModifier * SAFE_RADIUS_MARGIN);
    }

    static double acquisitionProgress(@Nullable VrScopeDivisionProjectionSample projection) {
        if (!isUsable(projection)) {
            return 0.0D;
        }
        double safeAcquisitionProgress = projection.currentSafeTaczRadiusModifier() * SAFE_RADIUS_MARGIN
                / VrScopeAuthorEyeBoxGate.MAX_ACQUISITION_RADIUS_SCALE;
        return Math.min(
                clamp01(safeAcquisitionProgress),
                legacyAcquisitionDirectionProgress(projection.currentDivisionPupilRadius())
        );
    }

    private static boolean isUsable(@Nullable VrScopeDivisionProjectionSample projection) {
        return projection != null
                && projection.ringBetweenAuthorAndDivision()
                && projection.ringBetweenCurrentEyeAndDivision()
                && Double.isFinite(projection.authorDivisionPupilRadius())
                && projection.authorDivisionPupilRadius() <= 1.0D
                && Double.isFinite(projection.currentSafeTaczRadiusModifier())
                && projection.currentSafeTaczRadiusModifier() >= 0.0D;
    }

    private static boolean matchesSource(
            @Nullable VrScopeAlignmentSample alignment,
            @Nullable VrScopeDivisionProjectionSample projection
    ) {
        return alignment != null && projection != null
                && alignment.frameIndex() == projection.frameIndex()
                && Objects.equals(alignment.gunId(), projection.gunId())
                && Objects.equals(alignment.scopeId(), projection.scopeId())
                && alignment.viewIndex() == projection.viewIndex();
    }

    private static double legacyAcquisitionDirectionProgress(double pupilRadius) {
        if (!Double.isFinite(pupilRadius) || pupilRadius < 0.0D || pupilRadius >= 1.30D) {
            return 0.0D;
        }
        if (pupilRadius <= 0.90D) {
            return 1.0D;
        }
        double normalized = (1.30D - pupilRadius) / 0.40D;
        return normalized * normalized * (3.0D - 2.0D * normalized);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
