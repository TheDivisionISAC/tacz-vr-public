package com.thedivisionisac.taczvr.config;

/**
 * Pure mapping from selected optical magnification to bounded filter strength. It consumes the already scaled
 * hip/ADS baseline, never changes motion-release thresholds or configured pose-offset limits, and preserves zero.
 */
public final class VrMagnificationStabilization {
    public static final double MAXIMUM_MAGNIFICATION = 25.0D;

    private VrMagnificationStabilization() {
    }

    public static double effectiveStrength(double baseline, double magnification, boolean enabled) {
        if (!Double.isFinite(baseline)) return 0.0D;
        double strength = Math.max(0.0D, Math.min(1.0D, baseline));
        if (!enabled || strength == 0.0D || !Double.isFinite(magnification) || magnification <= 1.0D) {
            return strength;
        }
        double zoom = Math.min(magnification, MAXIMUM_MAGNIFICATION);
        return 1.0D - (1.0D - strength) / Math.sqrt(zoom);
    }
}
