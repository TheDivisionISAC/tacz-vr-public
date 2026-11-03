package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Render-thread-owned physical scope progress state. It de-duplicates stereo reads by Vivecraft frame, resets closed
 * on source/profile changes, invalid input, frame regression, or a long update gap, and otherwise applies enter/exit
 * hysteresis plus frame-rate-independent attack/release smoothing. It owns no render hook and cannot hide an ocular
 * or request a world pass; a later runtime owner must reset it on reload and disconnect.
 */
final class VrScopeAlignmentGateState {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0D;
    private static final double CLOSED_EPSILON = 1.0E-9D;

    @Nullable
    private SourceKey source;
    @Nullable
    private VrScopeAlignmentGateProfile profile;
    private long frameIndex = Long.MIN_VALUE;
    private long lastUpdateNanos = Long.MIN_VALUE;
    private double progress;
    private boolean latched;

    double update(
            @Nullable VrScopeAlignmentSample sample,
            VrScopeAlignmentGateProfile profile,
            long nowNanos
    ) {
        Objects.requireNonNull(profile, "profile");
        if (!profile.isUsable(sample)) {
            this.reset();
            return 0.0D;
        }

        SourceKey nextSource = new SourceKey(sample.gunId(), sample.scopeId(), sample.viewIndex());
        boolean sourceChanged = !nextSource.equals(this.source) || !profile.equals(this.profile);
        boolean frameRegressed = this.frameIndex != Long.MIN_VALUE && sample.frameIndex() < this.frameIndex;
        if (sourceChanged || frameRegressed || this.lastUpdateNanos == Long.MIN_VALUE) {
            this.seedClosed(nextSource, sample.frameIndex(), profile, nowNanos);
            return 0.0D;
        }
        if (sample.frameIndex() == this.frameIndex) {
            return this.progress;
        }

        double elapsedSeconds = (nowNanos - this.lastUpdateNanos) / NANOS_PER_SECOND;
        if (!Double.isFinite(elapsedSeconds)
                || elapsedSeconds <= 0.0D
                || elapsedSeconds > profile.resetGapSeconds()) {
            this.seedClosed(nextSource, sample.frameIndex(), profile, nowNanos);
            return 0.0D;
        }

        double score = profile.score(sample);
        if (this.latched) {
            if (score <= profile.exitScore()) {
                this.latched = false;
            }
        } else if (score >= profile.enterScore()) {
            this.latched = true;
        }

        double target = this.latched ? score : 0.0D;
        double responseSeconds = target > this.progress
                ? profile.attackSeconds()
                : profile.releaseSeconds();
        double alpha = 1.0D - Math.exp(-elapsedSeconds / responseSeconds);
        this.progress += (target - this.progress) * alpha;
        this.progress = Math.max(0.0D, Math.min(1.0D, this.progress));
        if (!this.latched && this.progress < CLOSED_EPSILON) {
            this.progress = 0.0D;
        }
        this.frameIndex = sample.frameIndex();
        this.lastUpdateNanos = nowNanos;
        return this.progress;
    }

    double progress() {
        return this.progress;
    }

    boolean isLatched() {
        return this.latched;
    }

    void reset() {
        this.source = null;
        this.profile = null;
        this.frameIndex = Long.MIN_VALUE;
        this.lastUpdateNanos = Long.MIN_VALUE;
        this.progress = 0.0D;
        this.latched = false;
    }

    private void seedClosed(
            SourceKey source,
            long frameIndex,
            VrScopeAlignmentGateProfile profile,
            long nowNanos
    ) {
        this.source = source;
        this.profile = profile;
        this.frameIndex = frameIndex;
        this.lastUpdateNanos = nowNanos;
        this.progress = 0.0D;
        this.latched = false;
    }

    private record SourceKey(ResourceLocation gunId, ResourceLocation scopeId, int viewIndex) {
    }
}
