package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Bounded low-pass filter for tracked hand position. Small tremor is damped, deliberate translation releases the
 * filter, and the output can never drift farther from the physical controller than the caller's configured limit.
 */
final class AdaptivePositionFilter {
    private static final double MIN_CUTOFF_HZ = 0.75D;
    private static final double MAX_CUTOFF_HZ = 16.0D;
    private static final double MIN_UPDATE_SECONDS = 1.0D / 240.0D;
    private static final double MAX_UPDATE_SECONDS = 0.25D;
    private static final double TRACKING_RESET_DISTANCE = 0.35D;
    private static final double RELEASE_START_SPEED = 0.15D;

    @Nullable
    private Object sourceKey;
    @Nullable
    private Vec3 filteredPosition;
    @Nullable
    private Vec3 previousRawPosition;
    private long lastUpdateNanos;

    Vec3 update(Object sourceKey, Vec3 rawPosition, double strength, double maximumOffset,
                double releaseSpeed, long nowNanos) {
        if (!isFinite(rawPosition)) {
            this.reset();
            return Vec3.ZERO;
        }
        double boundedStrength = Mth.clamp(strength, 0.0D, 1.0D);
        double boundedOffset = Math.max(0.0D, maximumOffset);
        if (boundedStrength <= 0.0D || boundedOffset <= 0.0D) {
            this.seed(sourceKey, rawPosition, nowNanos);
            return rawPosition;
        }

        double elapsedSeconds = (nowNanos - this.lastUpdateNanos) / 1_000_000_000.0D;
        if (this.filteredPosition == null
                || this.previousRawPosition == null
                || !Objects.equals(this.sourceKey, sourceKey)
                || !Double.isFinite(elapsedSeconds)
                || elapsedSeconds <= 0.0D
                || elapsedSeconds > MAX_UPDATE_SECONDS) {
            this.seed(sourceKey, rawPosition, nowNanos);
            return rawPosition;
        }

        double rawStep = this.previousRawPosition.distanceTo(rawPosition);
        if (rawStep >= TRACKING_RESET_DISTANCE) {
            this.seed(sourceKey, rawPosition, nowNanos);
            return rawPosition;
        }
        if (elapsedSeconds < MIN_UPDATE_SECONDS) {
            this.filteredPosition = limitOffset(this.filteredPosition, rawPosition, boundedOffset);
            return this.filteredPosition;
        }

        double cutoffHz = MAX_CUTOFF_HZ * Math.pow(MIN_CUTOFF_HZ / MAX_CUTOFF_HZ, boundedStrength);
        double baseAlpha = 1.0D - Math.exp(-2.0D * Math.PI * cutoffHz * elapsedSeconds);
        double fullReleaseSpeed = Math.max(RELEASE_START_SPEED + 0.01D, releaseSpeed);
        double movementSpeed = rawStep / elapsedSeconds;
        double release = smoothStep(Mth.clamp(
                (movementSpeed - RELEASE_START_SPEED) / (fullReleaseSpeed - RELEASE_START_SPEED),
                0.0D,
                1.0D
        ));
        double alpha = Mth.lerp(release, baseAlpha, 1.0D);

        this.filteredPosition = limitOffset(lerp(this.filteredPosition, rawPosition, alpha), rawPosition, boundedOffset);
        this.previousRawPosition = rawPosition;
        this.lastUpdateNanos = nowNanos;
        return this.filteredPosition;
    }

    void reset() {
        this.sourceKey = null;
        this.filteredPosition = null;
        this.previousRawPosition = null;
        this.lastUpdateNanos = 0L;
    }

    private void seed(Object sourceKey, Vec3 rawPosition, long nowNanos) {
        this.sourceKey = sourceKey;
        this.filteredPosition = rawPosition;
        this.previousRawPosition = rawPosition;
        this.lastUpdateNanos = nowNanos;
    }

    private static Vec3 limitOffset(Vec3 filtered, Vec3 raw, double maximumOffset) {
        Vec3 offset = filtered.subtract(raw);
        double length = offset.length();
        if (length <= maximumOffset || length < 1.0E-9D) {
            return filtered;
        }
        return raw.add(offset.scale(maximumOffset / length));
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double alpha) {
        return from.scale(1.0D - alpha).add(to.scale(alpha));
    }

    private static double smoothStep(double value) {
        return value * value * (3.0D - 2.0D * value);
    }

    private static boolean isFinite(Vec3 position) {
        return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
    }
}
