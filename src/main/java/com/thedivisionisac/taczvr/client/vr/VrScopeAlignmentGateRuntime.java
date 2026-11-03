package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Render-thread owner that associates one smoothed usable-eye-box state and one small current-frame acquisition
 * opening with the exact TACZ scope-model instance captured for one render-pass-local stream. Reads fail closed on a
 * model, gun, or frame mismatch so stale progress cannot leak across guns, scopes, resource reloads, or missing
 * model nodes. {@link #progressFor(Object, ResourceLocation, long)} retains the physical acquisition preview, while
 * {@link #renderActivationProgressFor(Object, ResourceLocation, long)} intentionally exposes only fully usable
 * aperture progress for future off-screen rendering.
 * Eye isolation is owned by
 * {@link VrScopePerEyeGateRuntime}; this class does not read configuration or mutate TACZ.
 */
final class VrScopeAlignmentGateRuntime {
    private final VrScopeAlignmentGateState state = new VrScopeAlignmentGateState();

    @Nullable
    private Object scopeModel;
    @Nullable
    private ResourceLocation gunId;
    private long frameIndex = Long.MIN_VALUE;
    private double authorEyeBoxProgress;
    private double acquisitionProgress;
    private double divisionLineProgress = 1.0D;
    private double divisionLineAcquisitionProgress = 1.0D;
    private VrScopePerEyeGateRuntime.OptionalCenter taczCircleCenter =
            VrScopePerEyeGateRuntime.OptionalCenter.empty();
    private double nearEyeAngleProgress = 1.0D;
    private boolean nearEyePermitted;

    void record(
            VrScopeAlignmentSample sample,
            @Nullable VrScopeAuthorEyeBoxSample authorEyeBox,
            @Nullable VrScopeDivisionProjectionSample divisionProjection,
            Object scopeModel,
            VrScopeAlignmentGateProfile profile,
            long nowNanos
    ) {
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(scopeModel, "scopeModel");
        Objects.requireNonNull(profile, "profile");
        this.state.update(sample, profile, nowNanos);
        this.authorEyeBoxProgress = VrScopeAuthorEyeBoxGate.progressFor(sample, authorEyeBox);
        this.acquisitionProgress = VrScopeAuthorEyeBoxGate.acquisitionProgressFor(
                sample,
                authorEyeBox,
                profile.score(sample)
        );
        this.divisionLineProgress = divisionProjection == null
                ? 1.0D
                : VrScopeDivisionLineGate.progressFor(sample, divisionProjection);
        this.divisionLineAcquisitionProgress = divisionProjection == null
                ? 1.0D
                : VrScopeDivisionLineGate.acquisitionProgressFor(sample, divisionProjection);
        this.taczCircleCenter = !matchesSource(sample, divisionProjection)
                ? VrScopePerEyeGateRuntime.OptionalCenter.empty()
                : VrScopePerEyeGateRuntime.OptionalCenter.of(
                        divisionProjection.currentTaczCircleCenter().u(),
                        divisionProjection.currentTaczCircleCenter().v()
                );
        this.nearEyeAngleProgress = authorEyeBox == null
                ? 1.0D
                : VrScopeNearEyeAngleGate.progressFor(sample, authorEyeBox);
        this.nearEyePermitted = VrScopeNearEyeGate.permits(sample, authorEyeBox, divisionProjection);
        this.scopeModel = scopeModel;
        this.gunId = sample.gunId();
        this.frameIndex = sample.frameIndex();
    }

    private static boolean matchesSource(
            VrScopeAlignmentSample sample,
            @Nullable VrScopeDivisionProjectionSample projection
    ) {
        return projection != null
                && sample.frameIndex() == projection.frameIndex()
                && sample.viewIndex() == projection.viewIndex()
                && Objects.equals(sample.gunId(), projection.gunId())
                && Objects.equals(sample.scopeId(), projection.scopeId());
    }

    double progressFor(Object scopeModel, ResourceLocation gunId, long frameIndex) {
        Objects.requireNonNull(scopeModel, "scopeModel");
        Objects.requireNonNull(gunId, "gunId");
        if (!this.matchesQuery(scopeModel, gunId, frameIndex)) {
            this.reset();
            return 0.0D;
        }
        if (!this.nearEyePermitted) {
            return 0.0D;
        }
        double usableProgress = this.usableProgress();
        double acquisitionRadius = Math.min(
                Math.min(this.acquisitionProgress, this.divisionLineAcquisitionProgress),
                this.nearEyeAngleProgress
        )
                * VrScopeAuthorEyeBoxGate.MAX_ACQUISITION_RADIUS_SCALE;
        return Math.max(usableProgress, acquisitionRadius);
    }

    /**
     * Returns only the fully usable physical aperture progress. Acquisition preview is intentionally excluded so a
     * future off-screen scope renderer cannot spend a world pass while the eye is merely entering the aperture.
     */
    double renderActivationProgressFor(Object scopeModel, ResourceLocation gunId, long frameIndex) {
        Objects.requireNonNull(scopeModel, "scopeModel");
        Objects.requireNonNull(gunId, "gunId");
        if (!this.matchesQuery(scopeModel, gunId, frameIndex)) {
            this.reset();
            return 0.0D;
        }
        return this.nearEyePermitted ? this.usableProgress() : 0.0D;
    }

    VrScopePerEyeGateRuntime.OptionalCenter circleCenterFor(
            Object scopeModel,
            ResourceLocation gunId,
            long frameIndex
    ) {
        Objects.requireNonNull(scopeModel, "scopeModel");
        Objects.requireNonNull(gunId, "gunId");
        if (this.scopeModel != scopeModel
                || !gunId.equals(this.gunId)
                || frameIndex != this.frameIndex
                || !this.nearEyePermitted) {
            return VrScopePerEyeGateRuntime.OptionalCenter.empty();
        }
        return this.taczCircleCenter;
    }

    void reset() {
        this.state.reset();
        this.scopeModel = null;
        this.gunId = null;
        this.frameIndex = Long.MIN_VALUE;
        this.authorEyeBoxProgress = 0.0D;
        this.acquisitionProgress = 0.0D;
        this.divisionLineProgress = 1.0D;
        this.divisionLineAcquisitionProgress = 1.0D;
        this.taczCircleCenter = VrScopePerEyeGateRuntime.OptionalCenter.empty();
        this.nearEyeAngleProgress = 1.0D;
        this.nearEyePermitted = false;
    }

    private boolean matchesQuery(Object scopeModel, ResourceLocation gunId, long frameIndex) {
        return this.scopeModel == scopeModel
                && gunId.equals(this.gunId)
                && frameIndex == this.frameIndex;
    }

    private double usableProgress() {
        double combined = Math.min(
                Math.min(Math.min(this.state.progress(), this.authorEyeBoxProgress), this.divisionLineProgress),
                this.nearEyeAngleProgress
        );
        return Double.isFinite(combined)
                ? Math.max(0.0D, Math.min(1.0D, combined))
                : 0.0D;
    }
}
