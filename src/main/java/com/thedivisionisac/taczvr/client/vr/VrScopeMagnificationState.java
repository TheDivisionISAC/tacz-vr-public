package com.thedivisionisac.taczvr.client.vr;

/**
 * Render-thread transaction for one optional world pass. Only a complete camera/projection execution can publish an
 * image. Failure invalidates the image but retains the obligation to restore Vivecraft's first ordinary eye flag.
 * Opaque identities have reference semantics and are released at the next frame or explicit lifecycle reset.
 */
public final class VrScopeMagnificationState {
    private long frame = Long.MIN_VALUE;
    private VrScopeRenderRequest request;
    private Object world;
    private Object item;
    private Object target;
    private boolean attempted;
    private boolean executing;
    private boolean camera;
    private boolean projection;
    private boolean complete;
    private boolean firstEyeOwed;

    public boolean prepare(long frame, VrScopeRenderRequest request, Object world, Object item, Object target) {
        this.reset();
        if (frame <= 0 || request == null || request.sourceFrameIndex() != frame - 1
                || world == null || item == null || target == null) return false;
        this.frame = frame;
        this.request = request;
        this.world = world;
        this.item = item;
        this.target = target;
        this.firstEyeOwed = true;
        return true;
    }

    public boolean begin(long frame, Object world, Object item, Object target) {
        if (!this.matches(frame, world, item, target) || this.attempted) return false;
        this.attempted = true;
        this.executing = true;
        return true;
    }

    public void cameraApplied() { if (this.executing) this.camera = true; }
    public void projectionApplied() { if (this.executing) this.projection = true; }

    public boolean finish(boolean succeeded) {
        this.complete = this.executing && succeeded && this.camera && this.projection;
        this.executing = false;
        return this.complete;
    }

    public boolean imageReady(long frame, Object world, Object item, Object target) {
        return this.complete && this.matches(frame, world, item, target);
    }

    public boolean scheduled(long frame) { return this.request != null && this.frame == frame; }
    public boolean executing() { return this.executing; }
    public VrScopeRenderRequest request() { return this.request; }

    public boolean takeFirstEye(long frame) {
        if (this.frame != frame || !this.firstEyeOwed) return false;
        this.firstEyeOwed = false;
        return true;
    }

    public void invalidateImage() { this.executing = false; this.complete = false; }

    private boolean matches(long frame, Object world, Object item, Object target) {
        return this.request != null && frame == this.frame && world == this.world && item == this.item
                && target == this.target;
    }

    public void reset() {
        this.frame = Long.MIN_VALUE;
        this.request = null;
        this.world = null;
        this.item = null;
        this.target = null;
        this.attempted = this.executing = this.camera = this.projection = this.complete = this.firstEyeOwed = false;
    }
}
