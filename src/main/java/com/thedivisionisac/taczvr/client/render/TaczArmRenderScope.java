package com.thedivisionisac.taczvr.client.render;

/**
 * Marks the short render-thread interval in which TACZ draws a player-skin arm inside a gun animation group.
 * Vivecraft compatibility mixins use this scope to avoid applying tracked whole-player poses a second time.
 */
public final class TaczArmRenderScope {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private TaczArmRenderScope() {
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    public static void run(Runnable renderCall) {
        int previousDepth = DEPTH.get();
        DEPTH.set(previousDepth + 1);
        try {
            renderCall.run();
        } finally {
            if (previousDepth == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(previousDepth);
            }
        }
    }
}
