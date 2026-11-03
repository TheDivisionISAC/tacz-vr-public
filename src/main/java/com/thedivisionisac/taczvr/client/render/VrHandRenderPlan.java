package com.thedivisionisac.taczvr.client.render;

/**
 * Per-invocation ownership of a Vivecraft hand draw. Hiding wins over replacement; a post draw is allowed only
 * after the original draw was actually suppressed. Contains no shared render state or optional runtime types.
 */
public enum VrHandRenderPlan {
    KEEP, HIDE, REPLACE;

    public static VrHandRenderPlan select(boolean upstreamVisible, boolean hide, boolean replace) {
        if (hide) return HIDE;
        return upstreamVisible && replace ? REPLACE : KEEP;
    }

    public boolean suppressesOriginal() {
        return this != KEEP;
    }

    public boolean allowsPostRender(boolean originalSuppressed) {
        return this == REPLACE && originalSuppressed;
    }
}
