package com.thedivisionisac.taczvr.client.vr;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Owns the HMD-relative, render-only full-pose filters. One Vivecraft frame advances the state at most once so the
 * second stereo eye receives the exact same correction even if an API implementation exposes a divergent duplicate.
 */
final class VrRelativeRenderStabilizationState {
    private final AdaptiveRotationFilter rotationFilter = new AdaptiveRotationFilter();
    private final AdaptivePositionFilter positionFilter = new AdaptivePositionFilter();

    @Nullable
    private Object cachedSourceKey;
    @Nullable
    private VrRelativeRenderPose cachedPose;
    private long cachedFrameIndex = Long.MIN_VALUE;

    VrRelativeRenderPose update(Object sourceKey, long frameIndex, VrRelativeRenderPose rawPose,
                                double strength, double maximumRotationOffsetDegrees,
                                double maximumPositionOffset, double rotationReleaseSpeed,
                                double positionReleaseSpeed, long nowNanos) {
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(rawPose, "rawPose");
        if (this.cachedPose != null
                && frameIndex != Long.MIN_VALUE
                && this.cachedFrameIndex == frameIndex
                && Objects.equals(this.cachedSourceKey, sourceKey)) {
            return this.cachedPose;
        }

        VrRelativeRenderPose stabilizedPose = new VrRelativeRenderPose(
                this.positionFilter.update(
                        sourceKey,
                        rawPose.position(),
                        strength,
                        maximumPositionOffset,
                        positionReleaseSpeed,
                        nowNanos
                ),
                this.rotationFilter.update(
                        sourceKey,
                        rawPose.rotation(),
                        strength,
                        maximumRotationOffsetDegrees,
                        rotationReleaseSpeed,
                        nowNanos
                )
        );
        this.cachedSourceKey = sourceKey;
        this.cachedFrameIndex = frameIndex;
        this.cachedPose = stabilizedPose;
        return stabilizedPose;
    }

    void reset() {
        this.rotationFilter.reset();
        this.positionFilter.reset();
        this.cachedSourceKey = null;
        this.cachedFrameIndex = Long.MIN_VALUE;
        this.cachedPose = null;
    }
}
