package com.thedivisionisac.taczvr.client.vr;

import com.thedivisionisac.taczvr.TaczVr;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Opt-in C2B render-thread consumer for observing {@link VrScopeAlignmentGateState} without changing rendering.
 * Its shared prototype profile only brackets the Forge C1/C2B HMD observations and is not a production preset. When
 * disabled it does not advance state; when enabled it emits bounded-rate score/progress/latch telemetry. Resource
 * reload and disconnect owners must call {@link #reset()} through
 * {@link VrScopeAlignmentCapture#resetTransientState()}.
 */
final class VrScopeAlignmentGateDiagnostics {
    static final String ENABLE_PROPERTY = "taczvr.debugScopeAlignmentGate";
    static final String PROFILE_NAME = VrScopeAlignmentGateTuning.PROFILE_NAME;
    private static final long DEFAULT_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(100L);

    private final boolean enabled;
    private final long intervalNanos;
    private final VrScopeAlignmentGateProfile profile;
    private final Consumer<String> sink;
    private final VrScopeAlignmentGateState state = new VrScopeAlignmentGateState();
    private final Map<EyeSourceKey, Long> lastEyeEmissionNanos = new HashMap<>();
    private final Map<EyeSourceKey, Long> lastAuthorEyeBoxEmissionNanos = new HashMap<>();
    private final Map<EyeSourceKey, Long> lastDivisionProjectionEmissionNanos = new HashMap<>();
    private final Map<EyeSourceKey, Long> lastNearEyeAngleEmissionNanos = new HashMap<>();
    private final Map<EyeSourceKey, Long> lastAppliedApertureEmissionNanos = new HashMap<>();

    @Nullable
    private SourceKey source;
    private long lastEmissionNanos = Long.MIN_VALUE;

    VrScopeAlignmentGateDiagnostics() {
        this(
                Boolean.getBoolean(ENABLE_PROPERTY),
                DEFAULT_INTERVAL_NANOS,
                VrScopeAlignmentGateTuning.observationProfile(),
                message -> TaczVr.LOGGER.info(message)
        );
        if (this.enabled) {
            TaczVr.LOGGER.info(
                    "TACZVR_SCOPE_GATE_DIAG enabled profile={} interval_ms=100 "
                            + "eye_relief_m=[0.03,0.07,0.18,0.30] optical_lateral_m=[0.05,0.12] "
                            + "near_eye_hard_close_m=0.055_or_authored_cone_fallback "
                            + "division_envelope=active_current_eye_convex_inradius_x0.95 "
                            + "near_angle_relief_ratio=[0.60,0.85] ocular_eccentric_cone_multiplier=[0.55,0.90] "
                            + "view_center_deg=[25,45] optical_axis_deg=[12,30] coaxial_deg=[25,45] "
                            + "enter=0.75 exit=0.35 attack_s=0.08 release_s=0.12 reset_gap_s=0.25",
                    PROFILE_NAME
            );
        }
    }

    VrScopeAlignmentGateDiagnostics(
            boolean enabled,
            long intervalNanos,
            VrScopeAlignmentGateProfile profile,
            Consumer<String> sink
    ) {
        this.enabled = enabled;
        this.intervalNanos = Math.max(1L, intervalNanos);
        this.profile = Objects.requireNonNull(profile, "profile");
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
        if (!this.profile.isUsable(sample)) {
            this.reset();
            return;
        }

        SourceKey nextSource = new SourceKey(sample.gunId(), sample.scopeId(), sample.viewIndex());
        boolean sourceChanged = !nextSource.equals(this.source);
        boolean wasLatched = this.state.isLatched();
        double score = this.profile.score(sample);
        double progress = this.state.update(sample, this.profile, nowNanos);
        boolean latchChanged = wasLatched != this.state.isLatched();
        boolean clockRegressed = this.lastEmissionNanos != Long.MIN_VALUE
                && nowNanos < this.lastEmissionNanos;
        boolean intervalElapsed = this.lastEmissionNanos == Long.MIN_VALUE
                || nowNanos - this.lastEmissionNanos >= this.intervalNanos;
        if (!sourceChanged && !latchChanged && !clockRegressed && !intervalElapsed) {
            return;
        }

        this.source = nextSource;
        this.lastEmissionNanos = nowNanos;
        this.sink.accept(String.format(
                Locale.ROOT,
                "TACZVR_SCOPE_GATE_DIAG frame=%d pass=%s profile=%s gun=%s scope=%s view=%d "
                        + "score=%.6f progress=%.6f latched=%s "
                        + "optical_axial_m=%.6f optical_lateral_m=%.6f "
                        + "view_center_angle_deg=%.4f optical_axis_angle_deg=%.4f coaxial_angle_deg=%.4f",
                sample.frameIndex(),
                renderPass,
                PROFILE_NAME,
                sample.gunId(),
                sample.scopeId(),
                sample.viewIndex(),
                score,
                progress,
                this.state.isLatched(),
                sample.opticalAxialDistance(),
                sample.opticalLateralOffset(),
                sample.viewCenterAngleDegrees(),
                sample.opticalAxisAngleDegrees(),
                sample.coaxialAngleDegrees()
        ));
    }

    /**
     * Reports the current render eye's uncentered geometry without advancing the shared HMD-centered diagnostic
     * state. This lets HMD tests distinguish an eye that is genuinely aligned from a shared-center false negative.
     */
    void recordEyeCandidate(VrScopeAlignmentSample sample, String renderPass, long nowNanos) {
        if (!this.enabled) {
            return;
        }
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(renderPass, "renderPass");
        if (!this.profile.isUsable(sample)) {
            return;
        }

        EyeSourceKey key = new EyeSourceKey(
                sample.gunId(),
                sample.scopeId(),
                sample.viewIndex(),
                renderPass
        );
        Long lastEmission = this.lastEyeEmissionNanos.get(key);
        if (lastEmission != null
                && nowNanos >= lastEmission
                && nowNanos - lastEmission < this.intervalNanos) {
            return;
        }
        this.lastEyeEmissionNanos.put(key, nowNanos);
        this.sink.accept(String.format(
                Locale.ROOT,
                "TACZVR_SCOPE_EYE_GATE_DIAG frame=%d pass=%s profile=%s gun=%s scope=%s view=%d "
                        + "eye_score=%.6f eye_optical_axial_m=%.6f eye_optical_lateral_m=%.6f "
                        + "eye_view_center_angle_deg=%.4f eye_optical_axis_angle_deg=%.4f "
                        + "eye_coaxial_angle_deg=%.4f",
                sample.frameIndex(),
                renderPass,
                PROFILE_NAME,
                sample.gunId(),
                sample.scopeId(),
                sample.viewIndex(),
                this.profile.score(sample),
                sample.opticalAxialDistance(),
                sample.opticalLateralOffset(),
                sample.viewCenterAngleDegrees(),
                sample.opticalAxisAngleDegrees(),
                sample.coaxialAngleDegrees()
        ));
    }

    /**
     * Reports model-derived eye-box geometry without changing either the shared diagnostic state or the per-eye
     * aperture runtimes.
     */
    void recordAuthorEyeBox(VrScopeAuthorEyeBoxSample sample, String renderPass, long nowNanos) {
        if (!this.enabled) {
            return;
        }
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(renderPass, "renderPass");
        EyeSourceKey key = new EyeSourceKey(
                sample.gunId(),
                sample.scopeId(),
                sample.viewIndex(),
                renderPass
        );
        Long lastEmission = this.lastAuthorEyeBoxEmissionNanos.get(key);
        if (lastEmission != null
                && nowNanos >= lastEmission
                && nowNanos - lastEmission < this.intervalNanos) {
            return;
        }
        this.lastAuthorEyeBoxEmissionNanos.put(key, nowNanos);
        this.sink.accept(String.format(
                Locale.ROOT,
                "TACZVR_SCOPE_AUTHOR_EYEBOX_DIAG frame=%d pass=%s gun=%s scope=%s view=%d "
                        + "author_relief_m=%.6f current_relief_m=%.6f "
                        + "author_eye_axial_error_m=%.6f author_eye_lateral_error_m=%.6f "
                        + "aperture_rx_m=%.6f aperture_ry_m=%.6f author_cone_half_deg=%.4f "
                        + "author_ray_axis_deg=%.4f author_view_axis_deg=%.4f "
                        + "forward_intersection=%s pupil_u=%.6f pupil_v=%.6f pupil_radius=%.6f "
                        + "pupil_margin=%.6f inside=%s scope_view_distance_m=%.6f "
                        + "scope_view_distance_ratio=%.6f scope_view_axial_ratio=%.6f "
                        + "scope_view_lateral_ratio=%.6f pupil_progress=%.6f "
                        + "scope_view_axial_progress=%.6f scope_view_lateral_progress=%.6f "
                        + "scope_view_progress=%.6f combined_progress=%.6f "
                        + "acquisition_pupil_progress=%.6f acquisition_axial_progress=%.6f "
                        + "acquisition_lateral_progress=%.6f acquisition_geometry_progress=%.6f",
                sample.frameIndex(),
                renderPass,
                sample.gunId(),
                sample.scopeId(),
                sample.viewIndex(),
                sample.authorEyeRelief(),
                sample.currentEyeRelief(),
                sample.authorEyeAxialError(),
                sample.authorEyeLateralError(),
                sample.apertureRadiusX(),
                sample.apertureRadiusY(),
                sample.authorConeHalfAngleDegrees(),
                sample.authorRayAxisAngleDegrees(),
                sample.authorViewAxisAngleDegrees(),
                sample.forwardIntersection(),
                sample.pupilU(),
                sample.pupilV(),
                sample.pupilRadius(),
                sample.pupilMargin(),
                sample.insideAperture(),
                VrScopeAuthorEyeBoxGate.scopeViewDistance(sample),
                VrScopeAuthorEyeBoxGate.scopeViewDistanceRatio(sample),
                VrScopeAuthorEyeBoxGate.scopeViewAxialDistanceRatio(sample),
                VrScopeAuthorEyeBoxGate.scopeViewLateralDistanceRatio(sample),
                VrScopeAuthorEyeBoxGate.pupilProgress(sample),
                VrScopeAuthorEyeBoxGate.scopeViewAxialProgress(sample),
                VrScopeAuthorEyeBoxGate.scopeViewLateralProgress(sample),
                VrScopeAuthorEyeBoxGate.scopeViewProgress(sample),
                VrScopeAuthorEyeBoxGate.progress(sample),
                VrScopeAuthorEyeBoxGate.acquisitionPupilProgress(sample),
                VrScopeAuthorEyeBoxGate.acquisitionAxialProgress(sample),
                VrScopeAuthorEyeBoxGate.acquisitionLateralProgress(sample),
                VrScopeAuthorEyeBoxGate.acquisitionGeometryProgress(sample)
        ));
    }

    /**
     * Reports both the authored ring-plane envelope and the final TACZ fixed-circle-plane envelope without changing
     * aperture ownership.
     */
    void recordDivisionProjection(
            VrScopeDivisionProjectionSample sample,
            String renderPass,
            long nowNanos
    ) {
        if (!this.enabled) {
            return;
        }
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(renderPass, "renderPass");
        EyeSourceKey key = new EyeSourceKey(
                sample.gunId(),
                sample.scopeId(),
                sample.viewIndex(),
                renderPass
        );
        Long lastEmission = this.lastDivisionProjectionEmissionNanos.get(key);
        if (lastEmission != null
                && nowNanos >= lastEmission
                && nowNanos - lastEmission < this.intervalNanos) {
            return;
        }
        this.lastDivisionProjectionEmissionNanos.put(key, nowNanos);
        this.sink.accept(String.format(
                Locale.ROOT,
                "TACZVR_SCOPE_DIVISION_PROJECTION_DIAG frame=%d pass=%s gun=%s scope=%s view=%d "
                        + "ocular_fallback=%s ring_between=%s author_ring_m=%.6f ring_division_m=%.6f "
                        + "author_division_m=%.6f center_t=%.6f projected_w_m=%.6f projected_h_m=%.6f "
                        + "projected_center_u_m=%.6f projected_center_v_m=%.6f "
                        + "projected_rx_to_ocular=%.6f projected_ry_to_ocular=%.6f "
                        + "current_projected_w_m=%.6f current_projected_h_m=%.6f "
                        + "current_projected_center_u_m=%.6f current_projected_center_v_m=%.6f "
                        + "current_safe_ocular_radius_scale=%.6f current_safe_tacz_radius_modifier=%.6f "
                        + "current_tacz_center_x=%.6f current_tacz_center_y=%.6f "
                        + "author_division_pupil_radius=%.6f current_division_pupil_radius=%.6f "
                        + "current_ring_between=%s inside_division_sight_pupil=%s "
                        + "eye_forward_intersection=%s pupil_u_m=%.6f pupil_v_m=%.6f inside_projection=%s",
                sample.frameIndex(),
                renderPass,
                sample.gunId(),
                sample.scopeId(),
                sample.viewIndex(),
                sample.ocularPlaneFallback(),
                sample.ringBetweenAuthorAndDivision(),
                sample.authorToRingDistance(),
                sample.ringToDivisionDistance(),
                sample.authorToDivisionDistance(),
                sample.centerProjectionRatio(),
                sample.projectedWidth(),
                sample.projectedHeight(),
                sample.projectedCenterU(),
                sample.projectedCenterV(),
                sample.projectedHalfWidthToOcularRatio(),
                sample.projectedHalfHeightToOcularRatio(),
                sample.currentProjectedWidth(),
                sample.currentProjectedHeight(),
                sample.currentProjectedCenterU(),
                sample.currentProjectedCenterV(),
                sample.currentSafeOcularRadiusScale(),
                sample.currentSafeTaczRadiusModifier(),
                sample.currentTaczCircleCenter().u(),
                sample.currentTaczCircleCenter().v(),
                sample.authorDivisionPupilRadius(),
                sample.currentDivisionPupilRadius(),
                sample.ringBetweenCurrentEyeAndDivision(),
                sample.insideDivisionSightPupil(),
                sample.forwardEyeIntersection(),
                sample.currentPupil().u(),
                sample.currentPupil().v(),
                sample.insideProjectedDivision()
        ));
    }

    void recordNearEyeAngle(
            VrScopeAlignmentSample alignment,
            VrScopeAuthorEyeBoxSample eyeBox,
            String renderPass,
            long nowNanos
    ) {
        if (!this.enabled) {
            return;
        }
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(eyeBox, "eyeBox");
        Objects.requireNonNull(renderPass, "renderPass");
        EyeSourceKey key = new EyeSourceKey(
                alignment.gunId(),
                alignment.scopeId(),
                alignment.viewIndex(),
                renderPass
        );
        Long lastEmission = this.lastNearEyeAngleEmissionNanos.get(key);
        if (lastEmission != null
                && nowNanos >= lastEmission
                && nowNanos - lastEmission < this.intervalNanos) {
            return;
        }
        this.lastNearEyeAngleEmissionNanos.put(key, nowNanos);
        this.sink.accept(String.format(
                Locale.ROOT,
                "TACZVR_SCOPE_NEAR_ANGLE_DIAG frame=%d pass=%s gun=%s scope=%s view=%d "
                        + "current_relief_m=%.6f author_relief_m=%.6f relief_ratio=%.6f "
                        + "view_angle_deg=%.4f optical_axis_angle_deg=%.4f ocular_eccentric_angle_deg=%.4f "
                        + "author_cone_deg=%.4f strict_weight=%.6f strict_angle_progress=%.6f progress=%.6f",
                alignment.frameIndex(),
                renderPass,
                alignment.gunId(),
                alignment.scopeId(),
                alignment.viewIndex(),
                eyeBox.currentEyeRelief(),
                eyeBox.authorEyeRelief(),
                VrScopeNearEyeAngleGate.eyeReliefRatio(eyeBox),
                alignment.viewCenterAngleDegrees(),
                alignment.opticalAxisAngleDegrees(),
                VrScopeNearEyeAngleGate.ocularEccentricAngleDegrees(eyeBox),
                eyeBox.authorConeHalfAngleDegrees(),
                VrScopeNearEyeAngleGate.strictWeight(eyeBox),
                VrScopeNearEyeAngleGate.strictAngleProgress(eyeBox),
                VrScopeNearEyeAngleGate.progressFor(alignment, eyeBox)
        ));
    }

    /**
     * Reports the final scope radius multiplier written immediately before TACZ renders the current eye.
     */
    void recordAppliedAperture(
            long frameIndex,
            ResourceLocation gunId,
            ResourceLocation scopeId,
            int viewIndex,
            String renderPass,
            float physicalProgress,
            float authorRadiusModifier,
            float appliedRadiusModifier,
            long nowNanos
    ) {
        if (!this.enabled) {
            return;
        }
        Objects.requireNonNull(gunId, "gunId");
        Objects.requireNonNull(scopeId, "scopeId");
        Objects.requireNonNull(renderPass, "renderPass");
        EyeSourceKey key = new EyeSourceKey(gunId, scopeId, viewIndex, renderPass);
        Long lastEmission = this.lastAppliedApertureEmissionNanos.get(key);
        if (lastEmission != null
                && nowNanos >= lastEmission
                && nowNanos - lastEmission < this.intervalNanos) {
            return;
        }
        this.lastAppliedApertureEmissionNanos.put(key, nowNanos);
        this.sink.accept(String.format(
                Locale.ROOT,
                "TACZVR_SCOPE_APPLIED_APERTURE_DIAG frame=%d pass=%s gun=%s scope=%s view=%d "
                        + "physical_progress=%.6f author_radius_modifier=%.6f applied_radius_modifier=%.6f",
                frameIndex,
                renderPass,
                gunId,
                scopeId,
                viewIndex,
                physicalProgress,
                authorRadiusModifier,
                appliedRadiusModifier
        ));
    }

    double progress() {
        return this.state.progress();
    }

    boolean isLatched() {
        return this.state.isLatched();
    }

    void reset() {
        this.state.reset();
        this.source = null;
        this.lastEmissionNanos = Long.MIN_VALUE;
        this.lastEyeEmissionNanos.clear();
        this.lastAuthorEyeBoxEmissionNanos.clear();
        this.lastDivisionProjectionEmissionNanos.clear();
        this.lastNearEyeAngleEmissionNanos.clear();
        this.lastAppliedApertureEmissionNanos.clear();
    }

    static VrScopeAlignmentGateProfile provisionalProfile() {
        return VrScopeAlignmentGateTuning.observationProfile();
    }

    private record SourceKey(ResourceLocation gunId, ResourceLocation scopeId, int viewIndex) {
    }

    private record EyeSourceKey(ResourceLocation gunId, ResourceLocation scopeId, int viewIndex, String renderPass) {
    }
}
