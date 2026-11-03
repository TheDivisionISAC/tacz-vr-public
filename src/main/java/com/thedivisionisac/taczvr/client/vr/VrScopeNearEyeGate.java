package com.thedivisionisac.taczvr.client.vr;

import javax.annotation.Nullable;

import java.util.Objects;

/**
 * Hard safety boundary for a physical scope's near eye relief. The ordinary alignment state intentionally fades
 * over time, but that release tail must not leave even an acquisition-sized opening after the eye enters the rear
 * of the optic. This limit is expressed in physical view-space metres and is independent of gun-pack model scale.
 */
final class VrScopeNearEyeGate {
    static final double MINIMUM_EYE_RELIEF_METERS = 0.055D;
    static final double MAXIMUM_AUTHORED_CONE_MULTIPLIER = 3.0D;

    private VrScopeNearEyeGate() {
    }

    static boolean permits(
            @Nullable VrScopeAlignmentSample sample,
            @Nullable VrScopeAuthorEyeBoxSample eyeBox,
            @Nullable VrScopeDivisionProjectionSample divisionProjection
    ) {
        double minimum = minimumEyeRelief(sample, eyeBox, divisionProjection);
        return sample != null
                && Double.isFinite(sample.opticalAxialDistance())
                && Double.isFinite(minimum)
                && sample.opticalAxialDistance() > minimum;
    }

    static double minimumEyeRelief(
            @Nullable VrScopeAlignmentSample sample,
            @Nullable VrScopeAuthorEyeBoxSample eyeBox,
            @Nullable VrScopeDivisionProjectionSample divisionProjection
    ) {
        if (!matchesSource(sample, eyeBox)) {
            return Double.NaN;
        }
        if (hasProjectiveEnvelope(sample, divisionProjection)) {
            return MINIMUM_EYE_RELIEF_METERS;
        }
        return authoredConeFallback(eyeBox);
    }

    private static boolean hasProjectiveEnvelope(
            VrScopeAlignmentSample sample,
            @Nullable VrScopeDivisionProjectionSample projection
    ) {
        return matchesSource(sample, projection)
                && projection.ringBetweenAuthorAndDivision()
                && !projection.ocularPlaneFallback()
                && Double.isFinite(projection.currentSafeOcularRadiusScale())
                && projection.currentSafeOcularRadiusScale() >= 0.0D;
    }

    private static double authoredConeFallback(VrScopeAuthorEyeBoxSample eyeBox) {
        if (!Double.isFinite(eyeBox.authorConeHalfAngleDegrees())
                || !Double.isFinite(eyeBox.apertureRadiusX())
                || !Double.isFinite(eyeBox.apertureRadiusY())
                || eyeBox.authorConeHalfAngleDegrees() <= 0.0D
                || eyeBox.apertureRadiusX() <= 0.0D
                || eyeBox.apertureRadiusY() <= 0.0D) {
            return Double.NaN;
        }
        double maximumCurrentConeRadians = Math.toRadians(
                Math.min(89.0D, eyeBox.authorConeHalfAngleDegrees() * MAXIMUM_AUTHORED_CONE_MULTIPLIER)
        );
        double authoredConeMinimum = Math.min(eyeBox.apertureRadiusX(), eyeBox.apertureRadiusY())
                / Math.tan(maximumCurrentConeRadians);
        return Double.isFinite(authoredConeMinimum)
                ? Math.max(MINIMUM_EYE_RELIEF_METERS, authoredConeMinimum)
                : Double.NaN;
    }

    private static boolean matchesSource(
            @Nullable VrScopeAlignmentSample sample,
            @Nullable VrScopeAuthorEyeBoxSample eyeBox
    ) {
        return sample != null && eyeBox != null
                && sample.frameIndex() == eyeBox.frameIndex()
                && sample.viewIndex() == eyeBox.viewIndex()
                && Objects.equals(sample.gunId(), eyeBox.gunId())
                && Objects.equals(sample.scopeId(), eyeBox.scopeId());
    }

    private static boolean matchesSource(
            VrScopeAlignmentSample sample,
            @Nullable VrScopeDivisionProjectionSample projection
    ) {
        return projection != null
                && sample.frameIndex() == projection.frameIndex()
                && sample.viewIndex() == projection.viewIndex()
                && Objects.equals(sample.gunId(), projection.gunId())
                && Objects.equals(sample.scopeId(), projection.scopeId());
    }

}
