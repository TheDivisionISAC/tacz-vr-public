package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Speed-adaptive full-orientation filter with a strict angular lag bound. Quaternion interpolation retains controller
 * roll, while deliberate rotation releases toward tracking before the bounded lag becomes perceptible.
 */
final class AdaptiveRotationFilter {
    private static final double MIN_CUTOFF_HZ = 1.25D;
    private static final double MAX_CUTOFF_HZ = 24.0D;
    private static final double MIN_UPDATE_SECONDS = 1.0D / 240.0D;
    private static final double MAX_UPDATE_SECONDS = 0.25D;
    private static final double TRACKING_RESET_DEGREES = 60.0D;
    private static final double RELEASE_START_SPEED_DEGREES_PER_SECOND = 18.0D;

    @Nullable
    private Object sourceKey;
    @Nullable
    private VrRotation filteredRotation;
    @Nullable
    private VrRotation previousRawRotation;
    private long lastUpdateNanos;

    VrRotation update(Object sourceKey, VrRotation rawRotation, double strength, double maximumLagDegrees,
                      double releaseSpeedDegreesPerSecond, long nowNanos) {
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(rawRotation, "rawRotation");
        double boundedStrength = Mth.clamp(strength, 0.0D, 1.0D);
        double boundedMaximumLag = Math.max(0.0D, maximumLagDegrees);
        if (boundedStrength <= 0.0D || boundedMaximumLag <= 0.0D) {
            this.seed(sourceKey, rawRotation, nowNanos);
            return rawRotation;
        }

        double elapsedSeconds = (nowNanos - this.lastUpdateNanos) / 1_000_000_000.0D;
        if (this.filteredRotation == null
                || this.previousRawRotation == null
                || !Objects.equals(this.sourceKey, sourceKey)
                || !Double.isFinite(elapsedSeconds)
                || elapsedSeconds <= 0.0D
                || elapsedSeconds > MAX_UPDATE_SECONDS) {
            this.seed(sourceKey, rawRotation, nowNanos);
            return rawRotation;
        }

        double rawStepDegrees = angleDegrees(this.previousRawRotation, rawRotation);
        if (rawStepDegrees >= TRACKING_RESET_DEGREES) {
            this.seed(sourceKey, rawRotation, nowNanos);
            return rawRotation;
        }
        if (elapsedSeconds < MIN_UPDATE_SECONDS) {
            this.filteredRotation = limitLag(this.filteredRotation, rawRotation, boundedMaximumLag);
            return this.filteredRotation;
        }

        double cutoffHz = MAX_CUTOFF_HZ * Math.pow(MIN_CUTOFF_HZ / MAX_CUTOFF_HZ, boundedStrength);
        double baseAlpha = 1.0D - Math.exp(-2.0D * Math.PI * cutoffHz * elapsedSeconds);
        double fullReleaseSpeed = Math.max(
                RELEASE_START_SPEED_DEGREES_PER_SECOND + 1.0D,
                releaseSpeedDegreesPerSecond
        );
        double angularSpeed = rawStepDegrees / elapsedSeconds;
        double release = smoothStep(Mth.clamp(
                (angularSpeed - RELEASE_START_SPEED_DEGREES_PER_SECOND)
                        / (fullReleaseSpeed - RELEASE_START_SPEED_DEGREES_PER_SECOND),
                0.0D,
                1.0D
        ));
        double alpha = Mth.lerp(release, baseAlpha, 1.0D);

        this.filteredRotation = limitLag(
                interpolate(this.filteredRotation, rawRotation, alpha),
                rawRotation,
                boundedMaximumLag
        );
        this.previousRawRotation = rawRotation;
        this.lastUpdateNanos = nowNanos;
        return this.filteredRotation;
    }

    void reset() {
        this.sourceKey = null;
        this.filteredRotation = null;
        this.previousRawRotation = null;
        this.lastUpdateNanos = 0L;
    }

    private void seed(Object sourceKey, VrRotation rawRotation, long nowNanos) {
        this.sourceKey = sourceKey;
        this.filteredRotation = rawRotation;
        this.previousRawRotation = rawRotation;
        this.lastUpdateNanos = nowNanos;
    }

    private static VrRotation limitLag(VrRotation filtered, VrRotation raw, double maximumLagDegrees) {
        double lagDegrees = angleDegrees(raw, filtered);
        if (lagDegrees <= maximumLagDegrees || lagDegrees < 1.0E-8D) {
            return filtered;
        }
        return interpolate(raw, filtered, maximumLagDegrees / lagDegrees);
    }

    private static VrRotation interpolate(VrRotation from, VrRotation to, double alpha) {
        Quaternionf interpolated = from.toQuaternion().slerp(to.toQuaternion(), (float) Mth.clamp(alpha, 0.0D, 1.0D));
        return VrRotation.validated(interpolated).orElse(to);
    }

    private static double angleDegrees(VrRotation first, VrRotation second) {
        double dot = Math.abs(
                first.x() * second.x()
                        + first.y() * second.y()
                        + first.z() * second.z()
                        + first.w() * second.w()
        );
        return Math.toDegrees(2.0D * Math.acos(Mth.clamp(dot, 0.0D, 1.0D)));
    }

    private static double smoothStep(double value) {
        return value * value * (3.0D - 2.0D * value);
    }
}
