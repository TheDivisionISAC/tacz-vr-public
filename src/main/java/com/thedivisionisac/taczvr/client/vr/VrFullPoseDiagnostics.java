package com.thedivisionisac.taczvr.client.vr;

import com.thedivisionisac.taczvr.TaczVr;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Opt-in render telemetry for the rotational degree of freedom that {@link VrShotPose} does not carry, HMD-relative
 * controller motion, and the actual view-space controller basis before and after the existing correction.
 */
final class VrFullPoseDiagnostics {
    private static final long DEFAULT_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1L);
    private static final int MAXIMUM_WINDOW_SAMPLES = 512;

    private final boolean enabled;
    private final long windowNanos;
    private final Consumer<String> sink;
    private final double[] handRotationStepDegrees = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] handTwistStepDegrees = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] headRotationStepDegrees = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] headPositionStepMillimeters = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] headRelativeRotationStepDegrees = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] headRelativeTwistStepDegrees = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] headRelativePositionStepMillimeters = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] rawViewRotationStepDegrees = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] stabilizedViewRotationStepDegrees = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] rawViewPositionStepMillimeters = new double[MAXIMUM_WINDOW_SAMPLES];
    private final double[] stabilizedViewPositionStepMillimeters = new double[MAXIMUM_WINDOW_SAMPLES];

    @Nullable
    private ResourceLocation gunId;
    @Nullable
    private VrRenderTrackingSnapshot previousTracking;
    @Nullable
    private RelativePose previousRelativePose;
    @Nullable
    private MatrixPose previousRawViewPose;
    @Nullable
    private MatrixPose previousStabilizedViewPose;
    @Nullable
    private VrRenderTrackingSnapshot lastFrameTracking;
    @Nullable
    private MatrixPose lastFrameRawViewPose;
    @Nullable
    private MatrixPose lastFrameStabilizedViewPose;
    @Nullable
    private String primaryPass;
    @Nullable
    private String duplicatePass;
    private boolean aiming;
    private long lastFrameIndex = Long.MIN_VALUE;
    private long windowStartedNanos;
    private int uniqueFrames;
    private int deltaSamples;
    private int duplicateCalls;
    private int divergentTrackingDuplicates;
    private int primaryPassChanges;
    private double maximumDuplicateRawViewRotationDegrees;
    private double maximumDuplicateStabilizedViewRotationDegrees;
    private double maximumDuplicateRawViewPositionMillimeters;
    private double maximumDuplicateStabilizedViewPositionMillimeters;

    VrFullPoseDiagnostics() {
        this(
                Boolean.getBoolean(VrAimStabilizationDiagnostics.ENABLE_PROPERTY),
                DEFAULT_WINDOW_NANOS,
                message -> TaczVr.LOGGER.info(message)
        );
    }

    VrFullPoseDiagnostics(boolean enabled, long windowNanos, Consumer<String> sink) {
        this.enabled = enabled;
        this.windowNanos = Math.max(1L, windowNanos);
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    boolean isEnabled() {
        return this.enabled;
    }

    void record(long frameIndex, ResourceLocation gunId, boolean aiming, String renderPass,
                VrRenderTrackingSnapshot tracking, Matrix4fc rawViewMatrix, Matrix4fc stabilizedViewMatrix,
                long nowNanos) {
        if (!this.enabled) {
            return;
        }
        Objects.requireNonNull(gunId, "gunId");
        Objects.requireNonNull(renderPass, "renderPass");
        Objects.requireNonNull(tracking, "tracking");
        Objects.requireNonNull(rawViewMatrix, "rawViewMatrix");
        Objects.requireNonNull(stabilizedViewMatrix, "stabilizedViewMatrix");

        if (!gunId.equals(this.gunId) || aiming != this.aiming) {
            this.emitWindow("source_change");
            this.resetStream(gunId, aiming, nowNanos);
        } else if (this.windowStartedNanos == 0L) {
            this.windowStartedNanos = nowNanos;
        }

        MatrixPose rawViewPose = MatrixPose.from(rawViewMatrix);
        MatrixPose stabilizedViewPose = MatrixPose.from(stabilizedViewMatrix);
        if (frameIndex == this.lastFrameIndex) {
            this.recordDuplicate(renderPass, tracking, rawViewPose, stabilizedViewPose);
            this.emitIfWindowElapsed(nowNanos);
            return;
        }

        if (this.primaryPass == null) {
            this.primaryPass = renderPass;
        } else if (!this.primaryPass.equals(renderPass)) {
            this.primaryPassChanges++;
            this.primaryPass = renderPass;
        }

        RelativePose relativePose = RelativePose.from(tracking);
        this.uniqueFrames++;
        if (this.previousTracking != null
                && this.previousRelativePose != null
                && this.previousRawViewPose != null
                && this.previousStabilizedViewPose != null) {
            if (this.deltaSamples == MAXIMUM_WINDOW_SAMPLES) {
                this.emitWindow("capacity");
                this.resetWindow(nowNanos);
            }
            int index = this.deltaSamples++;
            this.handRotationStepDegrees[index] = rotationAngleDegrees(
                    this.previousTracking.handRotation(), tracking.handRotation()
            );
            this.handTwistStepDegrees[index] = twistAngleDegrees(
                    this.previousTracking.handRotation(),
                    tracking.handRotation(),
                    this.previousTracking.handPose().direction()
            );
            this.headRotationStepDegrees[index] = rotationAngleDegrees(
                    this.previousTracking.headRotation(), tracking.headRotation()
            );
            this.headPositionStepMillimeters[index] = this.previousTracking.headPose().position()
                    .distanceTo(tracking.headPose().position()) * 1_000.0D;
            this.headRelativeRotationStepDegrees[index] = rotationAngleDegrees(
                    this.previousRelativePose.rotation(), relativePose.rotation()
            );
            this.headRelativeTwistStepDegrees[index] = twistAngleDegrees(
                    this.previousRelativePose.rotation(),
                    relativePose.rotation(),
                    this.previousRelativePose.direction()
            );
            this.headRelativePositionStepMillimeters[index] = this.previousRelativePose.position()
                    .distance(relativePose.position()) * 1_000.0D;
            this.rawViewRotationStepDegrees[index] = rotationAngleDegrees(
                    this.previousRawViewPose.rotation(), rawViewPose.rotation()
            );
            this.stabilizedViewRotationStepDegrees[index] = rotationAngleDegrees(
                    this.previousStabilizedViewPose.rotation(), stabilizedViewPose.rotation()
            );
            this.rawViewPositionStepMillimeters[index] = this.previousRawViewPose.position()
                    .distance(rawViewPose.position()) * 1_000.0D;
            this.stabilizedViewPositionStepMillimeters[index] = this.previousStabilizedViewPose.position()
                    .distance(stabilizedViewPose.position()) * 1_000.0D;
        }

        this.previousTracking = tracking;
        this.previousRelativePose = relativePose;
        this.previousRawViewPose = rawViewPose;
        this.previousStabilizedViewPose = stabilizedViewPose;
        this.lastFrameIndex = frameIndex;
        this.lastFrameTracking = tracking;
        this.lastFrameRawViewPose = rawViewPose;
        this.lastFrameStabilizedViewPose = stabilizedViewPose;
        this.emitIfWindowElapsed(nowNanos);
    }

    void reset() {
        if (!this.enabled) {
            return;
        }
        this.gunId = null;
        this.previousTracking = null;
        this.previousRelativePose = null;
        this.previousRawViewPose = null;
        this.previousStabilizedViewPose = null;
        this.lastFrameTracking = null;
        this.lastFrameRawViewPose = null;
        this.lastFrameStabilizedViewPose = null;
        this.primaryPass = null;
        this.duplicatePass = null;
        this.aiming = false;
        this.lastFrameIndex = Long.MIN_VALUE;
        this.windowStartedNanos = 0L;
        this.resetWindowCounters();
    }

    private void recordDuplicate(String renderPass, VrRenderTrackingSnapshot tracking,
                                 MatrixPose rawViewPose, MatrixPose stabilizedViewPose) {
        this.duplicateCalls++;
        this.duplicatePass = renderPass;
        if (!tracking.equals(this.lastFrameTracking)) {
            this.divergentTrackingDuplicates++;
        }
        if (this.lastFrameRawViewPose != null && this.lastFrameStabilizedViewPose != null) {
            this.maximumDuplicateRawViewRotationDegrees = Math.max(
                    this.maximumDuplicateRawViewRotationDegrees,
                    rotationAngleDegrees(this.lastFrameRawViewPose.rotation(), rawViewPose.rotation())
            );
            this.maximumDuplicateStabilizedViewRotationDegrees = Math.max(
                    this.maximumDuplicateStabilizedViewRotationDegrees,
                    rotationAngleDegrees(this.lastFrameStabilizedViewPose.rotation(), stabilizedViewPose.rotation())
            );
            this.maximumDuplicateRawViewPositionMillimeters = Math.max(
                    this.maximumDuplicateRawViewPositionMillimeters,
                    this.lastFrameRawViewPose.position().distance(rawViewPose.position()) * 1_000.0D
            );
            this.maximumDuplicateStabilizedViewPositionMillimeters = Math.max(
                    this.maximumDuplicateStabilizedViewPositionMillimeters,
                    this.lastFrameStabilizedViewPose.position().distance(stabilizedViewPose.position()) * 1_000.0D
            );
        }
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
                "TACZVR_FULL_POSE_DIAG reason=%s gun=%s mode=%s primary_pass=%s duplicate_pass=%s "
                        + "unique_frames=%d delta_samples=%d duplicate_calls=%d "
                        + "divergent_tracking_duplicates=%d primary_pass_changes=%d "
                        + "hand_rotation_deg_p50=%.4f hand_rotation_deg_p95=%.4f "
                        + "hand_twist_deg_p50=%.4f hand_twist_deg_p95=%.4f "
                        + "head_rotation_deg_p95=%.4f head_position_mm_p95=%.4f "
                        + "head_relative_rotation_deg_p50=%.4f head_relative_rotation_deg_p95=%.4f "
                        + "head_relative_twist_deg_p50=%.4f head_relative_twist_deg_p95=%.4f "
                        + "head_relative_position_mm_p50=%.4f head_relative_position_mm_p95=%.4f "
                        + "raw_view_rotation_deg_p95=%.4f stabilized_view_rotation_deg_p95=%.4f "
                        + "raw_view_position_mm_p95=%.4f stabilized_view_position_mm_p95=%.4f "
                        + "duplicate_raw_view_rotation_deg_max=%.4f "
                        + "duplicate_stabilized_view_rotation_deg_max=%.4f "
                        + "duplicate_raw_view_position_mm_max=%.4f "
                        + "duplicate_stabilized_view_position_mm_max=%.4f",
                reason,
                this.gunId,
                this.aiming ? "ads" : "hip",
                this.primaryPass == null ? "unknown" : this.primaryPass,
                this.duplicatePass == null ? "none" : this.duplicatePass,
                this.uniqueFrames,
                this.deltaSamples,
                this.duplicateCalls,
                this.divergentTrackingDuplicates,
                this.primaryPassChanges,
                percentile(this.handRotationStepDegrees, this.deltaSamples, 0.50D),
                percentile(this.handRotationStepDegrees, this.deltaSamples, 0.95D),
                percentile(this.handTwistStepDegrees, this.deltaSamples, 0.50D),
                percentile(this.handTwistStepDegrees, this.deltaSamples, 0.95D),
                percentile(this.headRotationStepDegrees, this.deltaSamples, 0.95D),
                percentile(this.headPositionStepMillimeters, this.deltaSamples, 0.95D),
                percentile(this.headRelativeRotationStepDegrees, this.deltaSamples, 0.50D),
                percentile(this.headRelativeRotationStepDegrees, this.deltaSamples, 0.95D),
                percentile(this.headRelativeTwistStepDegrees, this.deltaSamples, 0.50D),
                percentile(this.headRelativeTwistStepDegrees, this.deltaSamples, 0.95D),
                percentile(this.headRelativePositionStepMillimeters, this.deltaSamples, 0.50D),
                percentile(this.headRelativePositionStepMillimeters, this.deltaSamples, 0.95D),
                percentile(this.rawViewRotationStepDegrees, this.deltaSamples, 0.95D),
                percentile(this.stabilizedViewRotationStepDegrees, this.deltaSamples, 0.95D),
                percentile(this.rawViewPositionStepMillimeters, this.deltaSamples, 0.95D),
                percentile(this.stabilizedViewPositionStepMillimeters, this.deltaSamples, 0.95D),
                this.maximumDuplicateRawViewRotationDegrees,
                this.maximumDuplicateStabilizedViewRotationDegrees,
                this.maximumDuplicateRawViewPositionMillimeters,
                this.maximumDuplicateStabilizedViewPositionMillimeters
        ));
    }

    private void resetStream(ResourceLocation gunId, boolean aiming, long nowNanos) {
        this.gunId = gunId;
        this.aiming = aiming;
        this.previousTracking = null;
        this.previousRelativePose = null;
        this.previousRawViewPose = null;
        this.previousStabilizedViewPose = null;
        this.lastFrameTracking = null;
        this.lastFrameRawViewPose = null;
        this.lastFrameStabilizedViewPose = null;
        this.primaryPass = null;
        this.duplicatePass = null;
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
        this.duplicatePass = null;
        this.divergentTrackingDuplicates = 0;
        this.primaryPassChanges = 0;
        this.maximumDuplicateRawViewRotationDegrees = 0.0D;
        this.maximumDuplicateStabilizedViewRotationDegrees = 0.0D;
        this.maximumDuplicateRawViewPositionMillimeters = 0.0D;
        this.maximumDuplicateStabilizedViewPositionMillimeters = 0.0D;
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

    private static double rotationAngleDegrees(VrRotation first, VrRotation second) {
        return rotationAngleDegrees(first.toQuaternion(), second.toQuaternion());
    }

    private static double rotationAngleDegrees(Quaternionf first, Quaternionf second) {
        double dot = Math.abs(first.dot(second));
        return Math.toDegrees(2.0D * Math.acos(clamp(dot, 0.0D, 1.0D)));
    }

    private static double twistAngleDegrees(VrRotation first, VrRotation second,
                                            net.minecraft.world.phys.Vec3 axis) {
        return twistAngleDegrees(first.toQuaternion(), second.toQuaternion(), toVector(axis));
    }

    private static double twistAngleDegrees(Quaternionf first, Quaternionf second, Vector3f axis) {
        Vector3f normalizedAxis = new Vector3f(axis);
        if (!normalizedAxis.isFinite() || normalizedAxis.lengthSquared() < 1.0E-12F) {
            return 0.0D;
        }
        normalizedAxis.normalize();
        Quaternionf delta = new Quaternionf(second).mul(new Quaternionf(first).conjugate()).normalize();
        double projection = delta.x * normalizedAxis.x + delta.y * normalizedAxis.y + delta.z * normalizedAxis.z;
        double projectedLength = Math.abs(projection);
        return Math.toDegrees(2.0D * Math.atan2(projectedLength, Math.abs(delta.w)));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static Vector3f toVector(net.minecraft.world.phys.Vec3 vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }

    private record RelativePose(Vector3f position, Vector3f direction, Quaternionf rotation) {
        private static RelativePose from(VrRenderTrackingSnapshot tracking) {
            Quaternionf inverseHead = tracking.headRotation().toQuaternion().conjugate();
            Vector3f relativePosition = inverseHead.transform(toVector(
                    tracking.handPose().position().subtract(tracking.headPose().position())
            ));
            Vector3f relativeDirection = inverseHead.transform(toVector(tracking.handPose().direction())).normalize();
            Quaternionf relativeRotation = inverseHead.mul(tracking.handRotation().toQuaternion()).normalize();
            return new RelativePose(relativePosition, relativeDirection, relativeRotation);
        }
    }

    private record MatrixPose(Vector3f position, Quaternionf rotation) {
        private static MatrixPose from(Matrix4fc matrix) {
            Matrix4f copy = new Matrix4f(matrix);
            return new MatrixPose(
                    copy.getTranslation(new Vector3f()),
                    copy.getNormalizedRotation(new Quaternionf()).normalize()
            );
        }
    }
}
