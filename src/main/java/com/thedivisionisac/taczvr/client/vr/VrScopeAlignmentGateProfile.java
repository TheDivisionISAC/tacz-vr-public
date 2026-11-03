package com.thedivisionisac.taczvr.client.vr;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Immutable, caller-supplied geometry and timing policy for a future physical scope gate. It deliberately has no
 * built-in tuning preset: C1 observations must be validated across representative scopes before a runtime owner or
 * client configuration chooses values. Scoring is side-effect free and cannot modify TACZ rendering.
 */
public record VrScopeAlignmentGateProfile(
        EyeReliefBand eyeRelief,
        UpperFalloff opticalLateralOffset,
        UpperFalloff viewCenterAngleDegrees,
        UpperFalloff opticalAxisAngleDegrees,
        UpperFalloff coaxialAngleDegrees,
        double enterScore,
        double exitScore,
        double attackSeconds,
        double releaseSeconds,
        double resetGapSeconds
) {
    public VrScopeAlignmentGateProfile {
        Objects.requireNonNull(eyeRelief, "eyeRelief");
        Objects.requireNonNull(opticalLateralOffset, "opticalLateralOffset");
        Objects.requireNonNull(viewCenterAngleDegrees, "viewCenterAngleDegrees");
        Objects.requireNonNull(opticalAxisAngleDegrees, "opticalAxisAngleDegrees");
        Objects.requireNonNull(coaxialAngleDegrees, "coaxialAngleDegrees");
        requireFiniteRange(enterScore, 0.0D, 1.0D, "enterScore");
        requireFiniteRange(exitScore, 0.0D, 1.0D, "exitScore");
        if (exitScore >= enterScore) {
            throw new IllegalArgumentException("exitScore must be lower than enterScore");
        }
        requirePositive(attackSeconds, "attackSeconds");
        requirePositive(releaseSeconds, "releaseSeconds");
        requirePositive(resetGapSeconds, "resetGapSeconds");
    }

    /**
     * Returns the weakest normalized geometry component. Zero is closed, one is fully aligned, and invalid facts are
     * fail-closed. The stateful enter/exit latch is intentionally owned by {@link VrScopeAlignmentGateState}.
     */
    public double score(@Nullable VrScopeAlignmentSample sample) {
        if (!isUsable(sample)) {
            return 0.0D;
        }
        return Math.min(
                this.eyeRelief.score(sample.opticalAxialDistance()),
                Math.min(
                        this.opticalLateralOffset.score(sample.opticalLateralOffset()),
                        Math.min(
                                this.viewCenterAngleDegrees.score(sample.viewCenterAngleDegrees()),
                                Math.min(
                                        this.opticalAxisAngleDegrees.score(sample.opticalAxisAngleDegrees()),
                                        this.coaxialAngleDegrees.score(sample.coaxialAngleDegrees())
                                )
                        )
                )
        );
    }

    boolean isUsable(@Nullable VrScopeAlignmentSample sample) {
        return sample != null
                && sample.gunId() != null
                && sample.scopeId() != null
                && Double.isFinite(sample.opticalAxialDistance())
                && Double.isFinite(sample.opticalLateralOffset())
                && Double.isFinite(sample.viewCenterAngleDegrees())
                && Double.isFinite(sample.opticalAxisAngleDegrees())
                && Double.isFinite(sample.coaxialAngleDegrees());
    }

    /**
     * Smooth band-pass for signed eye relief along the optical axis. Values at or beyond either zero boundary close
     * the gate; the interior full range scores one.
     */
    public record EyeReliefBand(
            double zeroBelow,
            double fullFrom,
            double fullTo,
            double zeroAbove
    ) {
        public EyeReliefBand {
            requireFinite(zeroBelow, "zeroBelow");
            requireFinite(fullFrom, "fullFrom");
            requireFinite(fullTo, "fullTo");
            requireFinite(zeroAbove, "zeroAbove");
            if (zeroBelow < 0.0D
                    || zeroBelow >= fullFrom
                    || fullFrom > fullTo
                    || fullTo >= zeroAbove) {
                throw new IllegalArgumentException(
                        "eye relief must satisfy 0 <= zeroBelow < fullFrom <= fullTo < zeroAbove"
                );
            }
        }

        double score(double value) {
            if (!Double.isFinite(value) || value <= this.zeroBelow || value >= this.zeroAbove) {
                return 0.0D;
            }
            if (value < this.fullFrom) {
                return smoothStep((value - this.zeroBelow) / (this.fullFrom - this.zeroBelow));
            }
            if (value <= this.fullTo) {
                return 1.0D;
            }
            return smoothStep((this.zeroAbove - value) / (this.zeroAbove - this.fullTo));
        }
    }

    /**
     * Smooth upper-bound falloff for lateral error or angular error. Values through {@code fullThrough} score one;
     * values at or above {@code zeroAt} close the gate.
     */
    public record UpperFalloff(double fullThrough, double zeroAt) {
        public UpperFalloff {
            requireFinite(fullThrough, "fullThrough");
            requireFinite(zeroAt, "zeroAt");
            if (fullThrough < 0.0D || fullThrough >= zeroAt) {
                throw new IllegalArgumentException("falloff must satisfy 0 <= fullThrough < zeroAt");
            }
        }

        double score(double value) {
            if (!Double.isFinite(value) || value >= this.zeroAt) {
                return 0.0D;
            }
            if (value <= this.fullThrough) {
                return value < 0.0D ? 0.0D : 1.0D;
            }
            double normalized = (this.zeroAt - value) / (this.zeroAt - this.fullThrough);
            return smoothStep(normalized);
        }
    }

    private static double smoothStep(double value) {
        double bounded = Math.max(0.0D, Math.min(1.0D, value));
        return bounded * bounded * (3.0D - 2.0D * bounded);
    }

    private static void requireFiniteRange(double value, double minimum, double maximum, String name) {
        requireFinite(value, name);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be in [" + minimum + ", " + maximum + "]");
        }
    }

    private static void requirePositive(double value, String name) {
        requireFinite(value, name);
        if (value <= 0.0D) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
