package com.thedivisionisac.taczvr.client.vr;

/**
 * Render-thread context for the exact TACZ attachment-model call whose ocular opening is physically owned by VR.
 * It lets a thin mixin replace TACZ 1.1.8's desktop-only circle center with the current eye projection while keeping
 * all scope identity, frame validation, and geometry outside the injection class. The caller supplies one immutable
 * per-eye center shared by the normal and late replay, nested calls restore the prior center, and reads outside the
 * bounded call preserve TACZ's original coordinates.
 */
public final class VrScopeTaczCircleRenderContext {
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private VrScopeTaczCircleRenderContext() {
    }

    public static Scope enter(VrScopeAlignmentCapture.VrScopeCircleCenter center) {
        State previous = ACTIVE.get();
        ACTIVE.set(new State(center));
        return new Scope(previous);
    }

    public static float resolveCenterX(float originalCenterX) {
        State state = ACTIVE.get();
        if (state == null) {
            return originalCenterX;
        }
        return state.center == null ? originalCenterX : (float) state.center.x();
    }

    public static float resolveCenterY(float originalCenterY) {
        State state = ACTIVE.get();
        if (state == null) {
            return originalCenterY;
        }
        return state.center == null ? originalCenterY : (float) state.center.y();
    }

    static void resetForCurrentThread() {
        ACTIVE.remove();
    }

    private record State(VrScopeAlignmentCapture.VrScopeCircleCenter center) {
    }

    public static final class Scope implements AutoCloseable {
        private final State previous;
        private boolean closed;

        private Scope(State previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            if (this.previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(this.previous);
            }
        }
    }
}
