package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Frame-rate-independent low-pass filter that releases as angular speed rises and bounds aim lag relative to tracking.
 * The caller owns thread confinement; render and shot capture both run on Minecraft's client thread.
 */
final class AdaptiveDirectionFilter {
    private static final double MIN_CUTOFF_HZ = 0.75D;
    private static final double MAX_CUTOFF_HZ = 20.0D;
    private static final double MIN_UPDATE_SECONDS = 1.0D / 240.0D;
    private static final double MAX_UPDATE_SECONDS = 0.25D;
    private static final double TRACKING_RESET_DEGREES = 60.0D;
    private static final double RELEASE_STEP_START_DEGREES = 1.5D;
    private static final double RELEASE_STEP_FULL_DEGREES = 6.0D;
    private static final double MIN_MAX_LAG_DEGREES = 0.75D;
    private static final double MAX_MAX_LAG_DEGREES = 4.75D;

    @Nullable
    private Object sourceKey;
    @Nullable
    private Vec3 filteredDirection;
    @Nullable
    private Vec3 previousRawDirection;
    private long lastUpdateNanos;

    Vec3 update(Object sourceKey, Vec3 rawDirection, double strength,
                double releaseSpeedDegreesPerSecond, long nowNanos) {
        Vec3 raw = normalizeOrFallback(rawDirection);
        double boundedStrength = Mth.clamp(strength, 0.0D, 1.0D);
        if (boundedStrength <= 0.0D) {
            this.seed(sourceKey, raw, nowNanos);
            return raw;
        }

        double elapsedSeconds = (nowNanos - this.lastUpdateNanos) / 1_000_000_000.0D;
        if (this.filteredDirection == null
                || this.previousRawDirection == null
                || !Objects.equals(this.sourceKey, sourceKey)
                || !Double.isFinite(elapsedSeconds)
                || elapsedSeconds <= 0.0D
                || elapsedSeconds > MAX_UPDATE_SECONDS) {
            this.seed(sourceKey, raw, nowNanos);
            return raw;
        }

        double rawStepDegrees = angleDegrees(this.previousRawDirection, raw);
        if (rawStepDegrees >= TRACKING_RESET_DEGREES) {
            this.seed(sourceKey, raw, nowNanos);
            return raw;
        }

        double maximumLag = Mth.lerp(boundedStrength, MIN_MAX_LAG_DEGREES, MAX_MAX_LAG_DEGREES);
        if (elapsedSeconds < MIN_UPDATE_SECONDS) {
            this.filteredDirection = limitLag(this.filteredDirection, raw, maximumLag);
            return this.filteredDirection;
        }

        double cutoffHz = MAX_CUTOFF_HZ * Math.pow(MIN_CUTOFF_HZ / MAX_CUTOFF_HZ, boundedStrength);
        double baseAlpha = 1.0D - Math.exp(-2.0D * Math.PI * cutoffHz * elapsedSeconds);
        double releaseSpeed = Math.max(1.0D, releaseSpeedDegreesPerSecond);
        double speedRelease = smoothStep(Mth.clamp(
                rawStepDegrees / elapsedSeconds / releaseSpeed, 0.0D, 1.0D
        ));
        double stepRelease = smoothStep(Mth.clamp(
                (rawStepDegrees - RELEASE_STEP_START_DEGREES)
                        / (RELEASE_STEP_FULL_DEGREES - RELEASE_STEP_START_DEGREES),
                0.0D,
                1.0D
        ));
        double release = speedRelease * stepRelease;
        double alpha = Mth.lerp(release, baseAlpha, 1.0D);

        this.filteredDirection = limitLag(nlerp(this.filteredDirection, raw, alpha), raw, maximumLag);
        this.previousRawDirection = raw;
        this.lastUpdateNanos = nowNanos;
        return this.filteredDirection;
    }

    void reset() {
        this.sourceKey = null;
        this.filteredDirection = null;
        this.previousRawDirection = null;
        this.lastUpdateNanos = 0L;
    }

    private void seed(Object sourceKey, Vec3 raw, long nowNanos) {
        this.sourceKey = sourceKey;
        this.filteredDirection = raw;
        this.previousRawDirection = raw;
        this.lastUpdateNanos = nowNanos;
    }

    private static Vec3 limitLag(Vec3 filtered, Vec3 raw, double maximumLagDegrees) {
        double lag = angleDegrees(raw, filtered);
        if (lag <= maximumLagDegrees || lag < 1.0E-8D) {
            return filtered;
        }
        return nlerp(raw, filtered, maximumLagDegrees / lag);
    }

    private static Vec3 nlerp(Vec3 from, Vec3 to, double alpha) {
        Vec3 blended = from.scale(1.0D - alpha).add(to.scale(alpha));
        return normalizeOrFallback(blended.lengthSqr() < 1.0E-12D ? to : blended);
    }

    private static double smoothStep(double value) {
        return value * value * (3.0D - 2.0D * value);
    }

    private static double angleDegrees(Vec3 first, Vec3 second) {
        double dot = Mth.clamp(first.dot(second), -1.0D, 1.0D);
        return Math.toDegrees(Math.acos(dot));
    }

    private static Vec3 normalizeOrFallback(Vec3 direction) {
        if (!Double.isFinite(direction.x)
                || !Double.isFinite(direction.y)
                || !Double.isFinite(direction.z)
                || direction.lengthSqr() < 1.0E-12D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return direction.normalize();
    }
}
