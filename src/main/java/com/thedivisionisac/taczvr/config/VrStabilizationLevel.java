package com.thedivisionisac.taczvr.config;

/**
 * Maps one safe user-facing stabilization level onto the separately tuned hip-fire and ADS filter strengths. The
 * upper half deliberately has a smaller range so the radial slider cannot create extreme hand lag.
 */
public final class VrStabilizationLevel {
    public static final int MINIMUM = 0;
    public static final int DEFAULT = 50;
    public static final int MAXIMUM = 100;

    private static final double MAXIMUM_UPPER_MULTIPLIER = 1.25D;

    private VrStabilizationLevel() {
    }

    public static int clamp(int level) {
        return Math.max(MINIMUM, Math.min(MAXIMUM, level));
    }

    public static double toSliderValue(int level) {
        return clamp(level) / (double) MAXIMUM;
    }

    public static int fromSliderValue(double value) {
        double finiteValue = Double.isFinite(value) ? value : toSliderValue(DEFAULT);
        return clamp((int) Math.round(Math.max(0.0D, Math.min(1.0D, finiteValue)) * MAXIMUM));
    }

    public static double effectiveStrength(double validatedStrength, int level) {
        double baseline = Math.max(0.0D, Math.min(1.0D, validatedStrength));
        int boundedLevel = clamp(level);
        double multiplier;
        if (boundedLevel <= DEFAULT) {
            multiplier = boundedLevel / (double) DEFAULT;
        } else {
            double upperProgress = (boundedLevel - DEFAULT) / (double) (MAXIMUM - DEFAULT);
            multiplier = 1.0D + upperProgress * (MAXIMUM_UPPER_MULTIPLIER - 1.0D);
        }
        return Math.max(0.0D, Math.min(1.0D, baseline * multiplier));
    }
}
