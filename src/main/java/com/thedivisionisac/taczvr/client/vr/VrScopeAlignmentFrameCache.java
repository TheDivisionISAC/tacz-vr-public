package com.thedivisionisac.taczvr.client.vr;

import java.util.Optional;

/**
 * Render-thread-owned single-frame cache. The first valid eye capture wins so LEFT, RIGHT, and CENTER passes share one
 * HMD-centered diagnostic sample. A lower frame ID starts a new stream, while a repeated ID remains first-eye-wins.
 */
final class VrScopeAlignmentFrameCache {
    private VrScopeAlignmentSample latest;

    boolean contains(long frameIndex) {
        return this.latest != null && this.latest.frameIndex() == frameIndex;
    }

    boolean accept(VrScopeAlignmentSample sample) {
        if (sample == null) {
            return false;
        }
        if (this.latest != null && sample.frameIndex() == this.latest.frameIndex()) {
            // The second stereo eye must not replace the first eye's immutable diagnostic facts.
            return false;
        }
        if (this.latest != null && sample.frameIndex() < this.latest.frameIndex()) {
            // Vivecraft can restart its frame counter when a session is recreated. Drop the old stream before
            // accepting the first sample from the new one; the request cache has a stricter drop-on-regression rule.
            this.latest = null;
        }
        this.latest = sample;
        return true;
    }

    Optional<VrScopeAlignmentSample> latest() {
        return Optional.ofNullable(this.latest);
    }

    Optional<VrScopeAlignmentSample> forFrame(long frameIndex) {
        return this.contains(frameIndex) ? Optional.of(this.latest) : Optional.empty();
    }

    void reset() {
        this.latest = null;
    }
}
