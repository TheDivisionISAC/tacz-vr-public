package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Render-thread cache with an exact one-frame lifetime. The first valid stereo capture wins its source frame, and a
 * request can be claimed only once by the immediately following frame. Watermarks survive consumption and discard;
 * only an explicit lifecycle reset or a different live world object starts a new stream. Opaque world/held-stack
 * references are compared by identity on the render thread and are never read as mutable game objects here.
 */
final class VrScopeRenderRequestCache {
    private VrScopeRenderRequest pending;
    private Object worldSession;
    private Object pendingHeldItem;
    private long lastOfferedFrame = Long.MIN_VALUE;
    private long lastClaimedFrame = Long.MIN_VALUE;

    boolean offer(VrScopeRenderRequest candidate, Object liveWorld, Object heldItem) {
        if (candidate == null || candidate.sourceFrameIndex() < 0L || liveWorld == null || heldItem == null) {
            return false;
        }
        if (this.worldSession != liveWorld) {
            this.reset();
            this.worldSession = liveWorld;
        }
        long candidateFrame = candidate.sourceFrameIndex();
        if (candidateFrame == this.lastOfferedFrame) {
            return false;
        }
        if (candidateFrame < this.lastOfferedFrame || candidateFrame < this.lastClaimedFrame) {
            this.clear();
            return false;
        }
        this.lastOfferedFrame = candidateFrame;
        this.pending = candidate;
        this.pendingHeldItem = heldItem;
        return true;
    }

    Optional<VrScopeRenderRequest> claim(
            long renderFrameIndex,
            Object liveWorld,
            Object heldItem,
            ResourceLocation dimensionId,
            ResourceLocation gunId,
            ResourceLocation scopeId,
            int viewIndex,
            double magnification
    ) {
        // A duplicate scheduler callback cannot steal next frame's pending capture or spend a second world pass.
        if (renderFrameIndex < this.lastClaimedFrame) {
            this.clear();
            return Optional.empty();
        }
        if (renderFrameIndex == this.lastClaimedFrame) {
            return Optional.empty();
        }
        this.lastClaimedFrame = renderFrameIndex;
        VrScopeRenderRequest candidate = this.pending;
        Object capturedItem = this.pendingHeldItem;
        this.clear();
        if (candidate == null || renderFrameIndex <= 0L
                || liveWorld == null || liveWorld != this.worldSession || heldItem == null || heldItem != capturedItem
                || dimensionId == null || gunId == null || scopeId == null || viewIndex < 0
                || !Double.isFinite(magnification) || magnification <= 0.0D
                || candidate.sourceFrameIndex() != renderFrameIndex - 1L
                || !candidate.dimensionId().equals(dimensionId)
                || !candidate.gunId().equals(gunId)
                || !candidate.scopeId().equals(scopeId)
                || candidate.viewIndex() != viewIndex
                || Double.compare(candidate.magnification(), magnification) != 0) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    void clear() {
        this.pending = null;
        this.pendingHeldItem = null;
    }

    /** Called for disconnect/resource replacement; ordinary frame discards must use clear instead. */
    void reset() {
        this.clear();
        this.worldSession = null;
        this.lastOfferedFrame = Long.MIN_VALUE;
        this.lastClaimedFrame = Long.MIN_VALUE;
    }
}
