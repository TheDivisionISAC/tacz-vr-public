package com.thedivisionisac.taczvr.client.vr;

/**
 * Restricts physical-scope ownership of TACZ's ocular opening to the exact first-person attachment render call.
 * Reads outside this render-thread scope retain TACZ's real ADS progress, so the aim input can still select the
 * stronger stabilization profile without opening or closing the optical mask.
 */
public final class VrScopeOcularRenderContext {
    private static final ThreadLocal<Integer> ACTIVE_DEPTH = new ThreadLocal<>();

    private VrScopeOcularRenderContext() {
    }

    public static Scope enterPhysicalOpening() {
        Integer depth = ACTIVE_DEPTH.get();
        ACTIVE_DEPTH.set(depth == null ? 1 : depth + 1);
        return new Scope();
    }

    /**
     * Returns a fully open TACZ ADS factor only while the physical gate owns the current ocular render.
     */
    public static float resolveTaczAimingProgress(float taczAimingProgress) {
        Integer depth = ACTIVE_DEPTH.get();
        return depth != null && depth > 0 ? 1.0F : taczAimingProgress;
    }

    /**
     * Returns whether the current render call is owned by the physical VR scope opening.
     */
    public static boolean isPhysicalOpeningActive() {
        Integer depth = ACTIVE_DEPTH.get();
        return depth != null && depth > 0;
    }

    static void resetForCurrentThread() {
        ACTIVE_DEPTH.remove();
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            Integer depth = ACTIVE_DEPTH.get();
            if (depth == null || depth <= 1) {
                ACTIVE_DEPTH.remove();
            } else {
                ACTIVE_DEPTH.set(depth - 1);
            }
        }
    }
}
