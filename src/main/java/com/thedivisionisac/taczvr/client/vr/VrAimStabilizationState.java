package com.thedivisionisac.taczvr.client.vr;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Owns the render-thread stabilization lifecycle. Each Vivecraft frame can advance the filters once, while shot
 * capture can only read a recent immutable render snapshot and therefore cannot feed an older tick pose back in.
 */
final class VrAimStabilizationState {
    private final AdaptiveDirectionFilter directionFilter = new AdaptiveDirectionFilter();
    private final AdaptivePositionFilter positionFilter = new AdaptivePositionFilter();

    @Nullable
    private Object cachedSourceKey;
    @Nullable
    private VrShotPose cachedRawPose;
    @Nullable
    private VrShotPose cachedStabilizedPose;
    private long cachedFrameIndex = Long.MIN_VALUE;
    private long cachedAtNanos;

    VrShotPose updateRenderPose(Object sourceKey, long frameIndex, VrShotPose rawPose, double strength,
                                double maximumPositionOffset, double directionReleaseSpeed,
                                double positionReleaseSpeed, long nowNanos) {
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(rawPose, "rawPose");
        if (this.cachedStabilizedPose != null
                && Objects.equals(this.cachedSourceKey, sourceKey)
                && frameIndex != Long.MIN_VALUE
                && this.cachedFrameIndex == frameIndex) {
            return this.cachedStabilizedPose;
        }

        VrShotPose stabilized = new VrShotPose(
                this.positionFilter.update(
                        sourceKey,
                        rawPose.position(),
                        strength,
                        maximumPositionOffset,
                        positionReleaseSpeed,
                        nowNanos
                ),
                this.directionFilter.update(
                        sourceKey,
                        rawPose.direction(),
                        strength,
                        directionReleaseSpeed,
                        nowNanos
                )
        );
        this.cachedSourceKey = sourceKey;
        this.cachedFrameIndex = frameIndex;
        this.cachedRawPose = rawPose;
        this.cachedStabilizedPose = stabilized;
        this.cachedAtNanos = nowNanos;
        return stabilized;
    }

    VrShotPose resolveShotPose(Object sourceKey, VrShotPose rawShotPose, long nowNanos, long maximumAgeNanos) {
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(rawShotPose, "rawShotPose");
        long ageNanos = nowNanos - this.cachedAtNanos;
        if (this.cachedStabilizedPose == null
                || !Objects.equals(this.cachedSourceKey, sourceKey)
                || ageNanos < 0L
                || ageNanos > Math.max(0L, maximumAgeNanos)) {
            return rawShotPose;
        }
        return this.cachedStabilizedPose;
    }

    void reset() {
        this.directionFilter.reset();
        this.positionFilter.reset();
        this.cachedSourceKey = null;
        this.cachedFrameIndex = Long.MIN_VALUE;
        this.cachedRawPose = null;
        this.cachedStabilizedPose = null;
        this.cachedAtNanos = 0L;
    }
}
