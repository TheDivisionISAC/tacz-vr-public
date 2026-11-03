package com.thedivisionisac.taczvr.client.vr;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Converts TACZ's authored {@code scope_view} and ocular geometry into one conservative, render-only eye-box
 * multiplier. The pupil component rejects oblique rays near the modeled lens rim; the scope-view component keeps a
 * tight lateral envelope around the authored eye line while allowing a wider axial band. TACZ authors place
 * {@code scope_view} as a desktop camera reference, so treating its offset as an equal-axis sphere can reject a
 * physically centered VR eye solely because it is closer to the rear lens than the authored camera. A wider outer
 * acquisition envelope may expose only a small TACZ stencil-clipped opening, giving the player a direction cue
 * without allowing a full division outside the usable pupil.
 */
final class VrScopeAuthorEyeBoxGate {
    static final double FULL_PUPIL_RADIUS = 0.60D;
    static final double CLOSED_PUPIL_RADIUS = 0.90D;
    static final double FULL_SCOPE_VIEW_AXIAL_RATIO = 0.75D;
    static final double CLOSED_SCOPE_VIEW_AXIAL_RATIO = 0.90D;
    static final double FULL_SCOPE_VIEW_LATERAL_RATIO = 0.20D;
    static final double CLOSED_SCOPE_VIEW_LATERAL_RATIO = 0.30D;
    static final double ACQUISITION_FULL_PUPIL_RADIUS = CLOSED_PUPIL_RADIUS;
    static final double ACQUISITION_CLOSED_PUPIL_RADIUS = 1.50D;
    static final double ACQUISITION_FULL_AXIAL_RATIO = CLOSED_SCOPE_VIEW_AXIAL_RATIO;
    static final double ACQUISITION_CLOSED_AXIAL_RATIO = 1.20D;
    static final double ACQUISITION_FULL_LATERAL_RATIO = CLOSED_SCOPE_VIEW_LATERAL_RATIO;
    static final double ACQUISITION_CLOSED_LATERAL_RATIO = 0.45D;
    static final double ACQUISITION_ZERO_ALIGNMENT_SCORE = 0.10D;
    static final double ACQUISITION_FULL_ALIGNMENT_SCORE = 0.35D;
    static final double MAX_ACQUISITION_RADIUS_SCALE = 0.15D;

    private VrScopeAuthorEyeBoxGate() {
    }

    static double progressFor(
            @Nullable VrScopeAlignmentSample alignment,
            @Nullable VrScopeAuthorEyeBoxSample eyeBox
    ) {
        if (!matchesSource(alignment, eyeBox)) {
            return 0.0D;
        }
        return progress(eyeBox);
    }

    static double acquisitionProgressFor(
            @Nullable VrScopeAlignmentSample alignment,
            @Nullable VrScopeAuthorEyeBoxSample eyeBox,
            double alignmentScore
    ) {
        if (!matchesSource(alignment, eyeBox)) {
            return 0.0D;
        }
        return Math.min(
                acquisitionAlignmentProgress(alignmentScore),
                acquisitionGeometryProgress(eyeBox)
        );
    }

    static double progress(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        return Math.min(pupilProgress(eyeBox), scopeViewProgress(eyeBox));
    }

    static double pupilProgress(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        if (eyeBox == null
                || !eyeBox.forwardIntersection()
                || !eyeBox.insideAperture()
                || !Double.isFinite(eyeBox.pupilRadius())
                || eyeBox.pupilRadius() < 0.0D
                || eyeBox.pupilRadius() >= CLOSED_PUPIL_RADIUS) {
            return 0.0D;
        }
        return descendingFalloff(
                eyeBox.pupilRadius(),
                FULL_PUPIL_RADIUS,
                CLOSED_PUPIL_RADIUS
        );
    }

    static double scopeViewProgress(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        return Math.min(scopeViewAxialProgress(eyeBox), scopeViewLateralProgress(eyeBox));
    }

    static double acquisitionGeometryProgress(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        return Math.min(
                acquisitionPupilProgress(eyeBox),
                Math.min(
                        acquisitionAxialProgress(eyeBox),
                        acquisitionLateralProgress(eyeBox)
                )
        );
    }

    static double acquisitionPupilProgress(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        if (eyeBox == null
                || !eyeBox.forwardIntersection()
                || !Double.isFinite(eyeBox.pupilRadius())
                || eyeBox.pupilRadius() < 0.0D
                || eyeBox.pupilRadius() >= ACQUISITION_CLOSED_PUPIL_RADIUS) {
            return 0.0D;
        }
        return descendingFalloff(
                eyeBox.pupilRadius(),
                ACQUISITION_FULL_PUPIL_RADIUS,
                ACQUISITION_CLOSED_PUPIL_RADIUS
        );
    }

    static double acquisitionAxialProgress(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        double ratio = scopeViewAxialDistanceRatio(eyeBox);
        if (!Double.isFinite(ratio) || ratio < 0.0D || ratio >= ACQUISITION_CLOSED_AXIAL_RATIO) {
            return 0.0D;
        }
        return descendingFalloff(
                ratio,
                ACQUISITION_FULL_AXIAL_RATIO,
                ACQUISITION_CLOSED_AXIAL_RATIO
        );
    }

    static double acquisitionLateralProgress(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        double ratio = scopeViewLateralDistanceRatio(eyeBox);
        if (!Double.isFinite(ratio) || ratio < 0.0D || ratio >= ACQUISITION_CLOSED_LATERAL_RATIO) {
            return 0.0D;
        }
        return descendingFalloff(
                ratio,
                ACQUISITION_FULL_LATERAL_RATIO,
                ACQUISITION_CLOSED_LATERAL_RATIO
        );
    }

    static double acquisitionAlignmentProgress(double alignmentScore) {
        if (!Double.isFinite(alignmentScore)
                || alignmentScore <= ACQUISITION_ZERO_ALIGNMENT_SCORE) {
            return 0.0D;
        }
        return ascendingFalloff(
                alignmentScore,
                ACQUISITION_ZERO_ALIGNMENT_SCORE,
                ACQUISITION_FULL_ALIGNMENT_SCORE
        );
    }

    static double scopeViewAxialProgress(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        double ratio = scopeViewAxialDistanceRatio(eyeBox);
        if (!Double.isFinite(ratio) || ratio < 0.0D || ratio >= CLOSED_SCOPE_VIEW_AXIAL_RATIO) {
            return 0.0D;
        }
        return descendingFalloff(
                ratio,
                FULL_SCOPE_VIEW_AXIAL_RATIO,
                CLOSED_SCOPE_VIEW_AXIAL_RATIO
        );
    }

    static double scopeViewLateralProgress(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        double ratio = scopeViewLateralDistanceRatio(eyeBox);
        if (!Double.isFinite(ratio) || ratio < 0.0D || ratio >= CLOSED_SCOPE_VIEW_LATERAL_RATIO) {
            return 0.0D;
        }
        return descendingFalloff(
                ratio,
                FULL_SCOPE_VIEW_LATERAL_RATIO,
                CLOSED_SCOPE_VIEW_LATERAL_RATIO
        );
    }

    static double scopeViewAxialDistanceRatio(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        if (!hasUsableAuthorRelief(eyeBox) || !Double.isFinite(eyeBox.authorEyeAxialError())) {
            return Double.NaN;
        }
        return Math.abs(eyeBox.authorEyeAxialError()) / eyeBox.authorEyeRelief();
    }

    static double scopeViewLateralDistanceRatio(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        if (!hasUsableAuthorRelief(eyeBox)
                || !Double.isFinite(eyeBox.authorEyeLateralError())
                || eyeBox.authorEyeLateralError() < 0.0D) {
            return Double.NaN;
        }
        return eyeBox.authorEyeLateralError() / eyeBox.authorEyeRelief();
    }

    static double scopeViewDistance(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        if (eyeBox == null
                || !Double.isFinite(eyeBox.authorEyeAxialError())
                || !Double.isFinite(eyeBox.authorEyeLateralError())
                || eyeBox.authorEyeLateralError() < 0.0D) {
            return Double.NaN;
        }
        return Math.hypot(eyeBox.authorEyeAxialError(), eyeBox.authorEyeLateralError());
    }

    static double scopeViewDistanceRatio(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        if (!hasUsableAuthorRelief(eyeBox)) {
            return Double.NaN;
        }
        double distance = scopeViewDistance(eyeBox);
        return Double.isFinite(distance) ? distance / eyeBox.authorEyeRelief() : Double.NaN;
    }

    private static boolean hasUsableAuthorRelief(@Nullable VrScopeAuthorEyeBoxSample eyeBox) {
        return eyeBox != null
                && Double.isFinite(eyeBox.authorEyeRelief())
                && eyeBox.authorEyeRelief() > 0.0D;
    }

    private static boolean matchesSource(
            @Nullable VrScopeAlignmentSample alignment,
            @Nullable VrScopeAuthorEyeBoxSample eyeBox
    ) {
        return alignment != null && eyeBox != null
                && alignment.gunId() != null
                && alignment.scopeId() != null
                && eyeBox.gunId() != null
                && eyeBox.scopeId() != null
                && alignment.frameIndex() == eyeBox.frameIndex()
                && Objects.equals(alignment.gunId(), eyeBox.gunId())
                && Objects.equals(alignment.scopeId(), eyeBox.scopeId())
                && alignment.viewIndex() == eyeBox.viewIndex();
    }

    private static double descendingFalloff(double value, double fullThrough, double closedAt) {
        if (value <= fullThrough) {
            return 1.0D;
        }
        double normalized = (closedAt - value) / (closedAt - fullThrough);
        return smoothStep(normalized);
    }

    private static double ascendingFalloff(double value, double zeroThrough, double fullAt) {
        if (value >= fullAt) {
            return 1.0D;
        }
        return smoothStep((value - zeroThrough) / (fullAt - zeroThrough));
    }

    private static double smoothStep(double value) {
        double bounded = Math.max(0.0D, Math.min(1.0D, value));
        return bounded * bounded * (3.0D - 2.0D * bounded);
    }
}
