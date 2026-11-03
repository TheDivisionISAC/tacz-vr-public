package com.thedivisionisac.taczvr.client.vr;

import com.thedivisionisac.taczvr.TaczVr;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Opt-in, render-thread-only telemetry for measuring raw and stabilized controller motion without changing filter
 * behavior. Enable it only for a diagnostic run with {@code -Dtaczvr.debugAimStabilization=true}.
 */
final class VrAimStabilizationDiagnostics {
    static final String ENABLE_PROPERTY = "taczvr.debugAimStabilization";
    private static final long DEFAULT_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1L);
    private static final int MAXIMUM_WINDOW_SAMPLES = 512;

    private final boolean enabled;
    private final long windowNanos;
    private final Consumer<String> sink;
    private final double[] frameIntervalMilliseconds = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] rawAngularStepDegrees = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] stabilizedAngularStepDegrees = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] rawAngularSpeedDegreesPerSecond = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] rawPositionStepMillimeters = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] stabilizedPositionStepMillimeters = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] rawPositionSpeedMetersPerSecond = new double[MAXIMUM_WINDOW_SAMPLES];

    @Nullable
    private ResourceLocation gunId;
    @Nullable
    private VrShotPose previousRawPose;
    @Nullable
    private VrShotPose previousStabilizedPose;
    @Nullable
    private VrShotPose lastFrameRawPose;
    private boolean aiming;
    private long previousSampleNanos;
    private long lastFrameIndex = Long.MIN_VALUE;
    private long windowStartedNanos;
    private int uniqueFrames;
    private int deltaSamples;
    private int duplicateCalls;
    private int divergentDuplicateCalls;
    private long missingFrames;
    private int backwardFrameIndices;
    private double maximumCorrectionAngleDegrees;
    private double maximumCorrectionPositionMillimeters;

    VrAimStabilizationDiagnostics() {
        this(
                Boolean.getBoolean(ENABLE_PROPERTY),
                DEFAULT_WINDOW_NANOS,
                message -> TaczVr.LOGGER.info(message)
        );
    }

    VrAimStabilizationDiagnostics(boolean enabled, long windowNanos, Consumer<String> sink) {
        this.enabled = enabled;
        this.windowNanos = Math.max(1L, windowNanos);
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    void record(long frameIndex, ResourceLocation gunId, boolean aiming, VrShotPose rawPose,
                VrShotPose stabilizedPose, long nowNanos) {
        if (!this.enabled) {
            return;
        }
        Objects.requireNonNull(gunId, "gunId");
        Objects.requireNonNull(rawPose, "rawPose");
        Objects.requireNonNull(stabilizedPose, "stabilizedPose");

        if (!gunId.equals(this.gunId) || aiming != this.aiming) {
            this.emitWindow("source_change");
            this.resetStream(gunId, aiming, nowNanos);
        } else if (this.windowStartedNanos == 0L) {
            this.windowStartedNanos = nowNanos;
        }

        if (frameIndex == this.lastFrameIndex) {
            this.duplicateCalls++;
            if (!rawPose.equals(this.lastFrameRawPose)) {
                this.divergentDuplicateCalls++;
            }
            this.emitIfWindowElapsed(nowNanos);
            return;
        }

        if (this.lastFrameIndex != Long.MIN_VALUE) {
            if (frameIndex > this.lastFrameIndex + 1L) {
                this.missingFrames += frameIndex - this.lastFrameIndex - 1L;
            } else if (frameIndex < this.lastFrameIndex) {
                this.backwardFrameIndices++;
            }
        }

        this.uniqueFrames++;
        if (this.previousRawPose != null
                && this.previousStabilizedPose != null
                && nowNanos > this.previousSampleNanos) {
            if (this.deltaSamples == MAXIMUM_WINDOW_SAMPLES) {
                this.emitWindow("capacity");
                this.resetWindow(nowNanos);
            }
            double elapsedSeconds = (nowNanos - this.previousSampleNanos) / 1_000_000_000.0D;
            double rawAngularStep = angleDegrees(this.previousRawPose.direction(), rawPose.direction());
            double rawPositionStepMeters = this.previousRawPose.position().distanceTo(rawPose.position());
            int index = this.deltaSamples++;
            this.frameIntervalMilliseconds[index] = elapsedSeconds * 1_000.0D;
            this.rawAngularStepDegrees[index] = rawAngularStep;
            this.stabilizedAngularStepDegrees[index] = angleDegrees(
                    this.previousStabilizedPose.direction(), stabilizedPose.direction()
            );
            this.rawAngularSpeedDegreesPerSecond[index] = rawAngularStep / elapsedSeconds;
            this.rawPositionStepMillimeters[index] = rawPositionStepMeters * 1_000.0D;
            this.stabilizedPositionStepMillimeters[index] = this.previousStabilizedPose.position()
                    .distanceTo(stabilizedPose.position()) * 1_000.0D;
            this.rawPositionSpeedMetersPerSecond[index] = rawPositionStepMeters / elapsedSeconds;
        }

        this.maximumCorrectionAngleDegrees = Math.max(
                this.maximumCorrectionAngleDegrees,
                angleDegrees(rawPose.direction(), stabilizedPose.direction())
        );
        this.maximumCorrectionPositionMillimeters = Math.max(
                this.maximumCorrectionPositionMillimeters,
                rawPose.position().distanceTo(stabilizedPose.position()) * 1_000.0D
        );
        this.previousRawPose = rawPose;
        this.previousStabilizedPose = stabilizedPose;
        this.previousSampleNanos = nowNanos;
        this.lastFrameIndex = frameIndex;
        this.lastFrameRawPose = rawPose;
        this.emitIfWindowElapsed(nowNanos);
    }

    void reset() {
        if (!this.enabled) {
            return;
        }
        this.gunId = null;
        this.previousRawPose = null;
        this.previousStabilizedPose = null;
        this.lastFrameRawPose = null;
        this.aiming = false;
        this.previousSampleNanos = 0L;
        this.lastFrameIndex = Long.MIN_VALUE;
        this.windowStartedNanos = 0L;
        this.resetWindowCounters();
    }

    private void emitIfWindowElapsed(long nowNanos) {
        if (nowNanos - this.windowStartedNanos >= this.windowNanos) {
            this.emitWindow("interval");
            this.resetWindow(nowNanos);
        }
    }

    private void emitWindow(String reason) {
        if (this.gunId == null || this.uniqueFrames == 0) {
            return;
        }
        this.sink.accept(String.format(
                Locale.ROOT,
                "TACZVR_AIM_DIAG reason=%s gun=%s mode=%s unique_frames=%d delta_samples=%d "
                        + "duplicate_calls=%d divergent_duplicate_calls=%d missing_frames=%d backward_frames=%d "
                        + "dt_ms_p50=%.3f dt_ms_p95=%.3f dt_ms_max=%.3f "
                        + "raw_angle_deg_p50=%.4f raw_angle_deg_p95=%.4f raw_angle_deg_max=%.4f "
                        + "stabilized_angle_deg_p50=%.4f stabilized_angle_deg_p95=%.4f "
                        + "raw_angle_speed_deg_s_p95=%.3f raw_angle_speed_deg_s_max=%.3f "
                        + "raw_position_mm_p50=%.4f raw_position_mm_p95=%.4f raw_position_mm_max=%.4f "
                        + "stabilized_position_mm_p50=%.4f stabilized_position_mm_p95=%.4f "
                        + "raw_position_speed_m_s_p95=%.4f raw_position_speed_m_s_max=%.4f "
                        + "correction_angle_deg_max=%.4f correction_position_mm_max=%.4f",
                reason,
                this.gunId,
                this.aiming ? "ads" : "hip",
                this.uniqueFrames,
                this.deltaSamples,
                this.duplicateCalls,
                this.divergentDuplicateCalls,
                this.missingFrames,
                this.backwardFrameIndices,
                percentile(this.frameIntervalMilliseconds, this.deltaSamples, 0.50D),
                percentile(this.frameIntervalMilliseconds, this.deltaSamples, 0.95D),
                maximum(this.frameIntervalMilliseconds, this.deltaSamples),
                percentile(this.rawAngularStepDegrees, this.deltaSamples, 0.50D),
                percentile(this.rawAngularStepDegrees, this.deltaSamples, 0.95D),
                maximum(this.rawAngularStepDegrees, this.deltaSamples),
                percentile(this.stabilizedAngularStepDegrees, this.deltaSamples, 0.50D),
                percentile(this.stabilizedAngularStepDegrees, this.deltaSamples, 0.95D),
                percentile(this.rawAngularSpeedDegreesPerSecond, this.deltaSamples, 0.95D),
                maximum(this.rawAngularSpeedDegreesPerSecond, this.deltaSamples),
                percentile(this.rawPositionStepMillimeters, this.deltaSamples, 0.50D),
                percentile(this.rawPositionStepMillimeters, this.deltaSamples, 0.95D),
                maximum(this.rawPositionStepMillimeters, this.deltaSamples),
                percentile(this.stabilizedPositionStepMillimeters, this.deltaSamples, 0.50D),
                percentile(this.stabilizedPositionStepMillimeters, this.deltaSamples, 0.95D),
                percentile(this.rawPositionSpeedMetersPerSecond, this.deltaSamples, 0.95D),
                maximum(this.rawPositionSpeedMetersPerSecond, this.deltaSamples),
                this.maximumCorrectionAngleDegrees,
                this.maximumCorrectionPositionMillimeters
        ));
    }

    private void resetStream(ResourceLocation gunId, boolean aiming, long nowNanos) {
        this.gunId = gunId;
        this.aiming = aiming;
        this.previousRawPose = null;
        this.previousStabilizedPose = null;
        this.lastFrameRawPose = null;
        this.previousSampleNanos = 0L;
        this.lastFrameIndex = Long.MIN_VALUE;
        this.resetWindow(nowNanos);
    }

    private void resetWindow(long nowNanos) {
        this.windowStartedNanos = nowNanos;
        this.resetWindowCounters();
    }

    private void resetWindowCounters() {
        this.uniqueFrames = 0;
        this.deltaSamples = 0;
        this.duplicateCalls = 0;
        this.divergentDuplicateCalls = 0;
        this.missingFrames = 0L;
        this.backwardFrameIndices = 0;
        this.maximumCorrectionAngleDegrees = 0.0D;
        this.maximumCorrectionPositionMillimeters = 0.0D;
    }

    private static double percentile(double[] values, int count, double fraction) {
        if (count <= 0) {
            return 0.0D;
        }
        double[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        int index = (int) Math.ceil(fraction * count) - 1;
        return sorted[Math.max(0, Math.min(count - 1, index))];
    }

    private static double maximum(double[] values, int count) {
        double maximum = 0.0D;
        for (int index = 0; index < count; index++) {
            maximum = Math.max(maximum, values[index]);
        }
        return maximum;
    }

    private static double angleDegrees(Vec3 first, Vec3 second) {
        double dot = Math.max(-1.0D, Math.min(1.0D, first.normalize().dot(second.normalize())));
        return Math.toDegrees(Math.acos(dot));
    }
}
