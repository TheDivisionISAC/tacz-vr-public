package com.thedivisionisac.taczvr.client.vr;

import com.thedivisionisac.taczvr.TaczVr;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Opt-in, render-thread-only telemetry for validating C1 scope geometry before any physical gate is allowed to change
 * rendering. It emits a bounded-rate snapshot and owns no alignment thresholds or render state.
 */
final class VrScopeAlignmentDiagnostics {
    static final String ENABLE_PROPERTY = "taczvr.debugScopeAlignment";
    private static final long DEFAULT_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);

    private final boolean enabled;
    private final long intervalNanos;
    private final Consumer<String> sink;

    @Nullable
    private SourceKey source;
    private long lastEmissionNanos = Long.MIN_VALUE;
    @Nullable
    private SourceKey stereoSource;
    private long lastStereoEmissionNanos = Long.MIN_VALUE;
    private long referenceFrameIndex = Long.MIN_VALUE;
    @Nullable
    private String referencePass;

    VrScopeAlignmentDiagnostics() {
        this(
                Boolean.getBoolean(ENABLE_PROPERTY),
                DEFAULT_INTERVAL_NANOS,
                message -> TaczVr.LOGGER.info(message)
        );
        if (this.enabled) {
            TaczVr.LOGGER.info("TACZVR_SCOPE_DIAG enabled interval_ms=250");
        }
    }

    VrScopeAlignmentDiagnostics(boolean enabled, long intervalNanos, Consumer<String> sink) {
        this.enabled = enabled;
        this.intervalNanos = Math.max(1L, intervalNanos);
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    boolean isEnabled() {
        return this.enabled;
    }

    void record(VrScopeAlignmentSample sample, String renderPass, long nowNanos) {
        if (!this.enabled) {
            return;
        }
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(renderPass, "renderPass");
        this.referenceFrameIndex = sample.frameIndex();
        this.referencePass = renderPass;

        SourceKey nextSource = new SourceKey(sample.gunId(), sample.scopeId(), sample.viewIndex());
        boolean sourceChanged = !nextSource.equals(this.source);
        boolean clockRegressed = this.lastEmissionNanos != Long.MIN_VALUE && nowNanos < this.lastEmissionNanos;
        if (!sourceChanged
                && !clockRegressed
                && this.lastEmissionNanos != Long.MIN_VALUE
                && nowNanos - this.lastEmissionNanos < this.intervalNanos) {
            return;
        }

        this.source = nextSource;
        this.lastEmissionNanos = nowNanos;
        this.sink.accept(String.format(
                Locale.ROOT,
                "TACZVR_SCOPE_DIAG frame=%d pass=%s gun=%s scope=%s view=%d "
                        + "lens_x_m=%.6f lens_y_m=%.6f lens_z_m=%.6f "
                        + "axis_x=%.6f axis_y=%.6f axis_z=%.6f "
                        + "lens_distance_m=%.6f view_forward_m=%.6f view_lateral_m=%.6f "
                        + "optical_axial_m=%.6f optical_lateral_m=%.6f "
                        + "view_center_angle_deg=%.4f optical_axis_angle_deg=%.4f coaxial_angle_deg=%.4f",
                sample.frameIndex(),
                renderPass,
                sample.gunId(),
                sample.scopeId(),
                sample.viewIndex(),
                sample.lensPosition().x,
                sample.lensPosition().y,
                sample.lensPosition().z,
                sample.opticalAxis().x,
                sample.opticalAxis().y,
                sample.opticalAxis().z,
                sample.lensDistance(),
                sample.viewForwardDistance(),
                sample.viewLateralOffset(),
                sample.opticalAxialDistance(),
                sample.opticalLateralOffset(),
                sample.viewCenterAngleDegrees(),
                sample.opticalAxisAngleDegrees(),
                sample.coaxialAngleDegrees()
        ));
    }

    void recordStereoComparison(
            VrScopeAlignmentSample reference,
            VrScopeAlignmentSample candidate,
            String candidatePass,
            long nowNanos
    ) {
        if (!this.enabled) {
            return;
        }
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(candidatePass, "candidatePass");
        if (reference.frameIndex() != candidate.frameIndex()
                || this.referenceFrameIndex != reference.frameIndex()
                || !isStereoPair(this.referencePass, candidatePass)) {
            return;
        }

        SourceKey referenceSource = new SourceKey(reference.gunId(), reference.scopeId(), reference.viewIndex());
        SourceKey candidateSource = new SourceKey(candidate.gunId(), candidate.scopeId(), candidate.viewIndex());
        if (!referenceSource.equals(candidateSource)) {
            return;
        }
        boolean sourceChanged = !candidateSource.equals(this.stereoSource);
        boolean clockRegressed = this.lastStereoEmissionNanos != Long.MIN_VALUE
                && nowNanos < this.lastStereoEmissionNanos;
        if (!sourceChanged
                && !clockRegressed
                && this.lastStereoEmissionNanos != Long.MIN_VALUE
                && nowNanos - this.lastStereoEmissionNanos < this.intervalNanos) {
            return;
        }

        this.stereoSource = candidateSource;
        this.lastStereoEmissionNanos = nowNanos;
        Vec3 lensDelta = candidate.lensPosition().subtract(reference.lensPosition());
        double axisDot = Math.max(-1.0D, Math.min(
                1.0D,
                reference.opticalAxis().dot(candidate.opticalAxis())
        ));
        this.sink.accept(String.format(
                Locale.ROOT,
                "TACZVR_SCOPE_STEREO frame=%d reference_pass=%s candidate_pass=%s "
                        + "gun=%s scope=%s view=%d "
                        + "lens_dx_m=%.6f lens_dy_m=%.6f lens_dz_m=%.6f "
                        + "lens_delta_m=%.6f axis_delta_deg=%.4f",
                reference.frameIndex(),
                this.referencePass,
                candidatePass,
                candidate.gunId(),
                candidate.scopeId(),
                candidate.viewIndex(),
                lensDelta.x,
                lensDelta.y,
                lensDelta.z,
                lensDelta.length(),
                Math.toDegrees(Math.acos(axisDot))
        ));
    }

    void reset() {
        this.source = null;
        this.lastEmissionNanos = Long.MIN_VALUE;
        this.stereoSource = null;
        this.lastStereoEmissionNanos = Long.MIN_VALUE;
        this.referenceFrameIndex = Long.MIN_VALUE;
        this.referencePass = null;
    }

    private static boolean isStereoPair(@Nullable String referencePass, String candidatePass) {
        VrScopeRenderPass reference = VrScopeRenderPass.fromName(referencePass);
        VrScopeRenderPass candidate = VrScopeRenderPass.fromName(candidatePass);
        return reference.isEye() && candidate == reference.opposite();
    }

    private record SourceKey(net.minecraft.resources.ResourceLocation gunId,
                             net.minecraft.resources.ResourceLocation scopeId,
                             int viewIndex) {
    }
}
