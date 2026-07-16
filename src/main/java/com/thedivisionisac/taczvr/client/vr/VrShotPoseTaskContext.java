package com.thedivisionisac.taczvr.client.vr;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * Binds a main-thread shot snapshot to the exact TACZ scheduler task that later sends the shot packet.
 * The nested restore logic prevents pooled executor threads from leaking one task's pose into another.
 */
public final class VrShotPoseTaskContext {
    private static final ThreadLocal<VrShotPose> CURRENT = new ThreadLocal<>();

    private VrShotPoseTaskContext() {
    }

    public static Runnable bind(@Nullable VrShotPose pose, Runnable action) {
        Objects.requireNonNull(action, "action");
        return () -> runWith(pose, action);
    }

    public static Optional<VrShotPose> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    private static void runWith(@Nullable VrShotPose pose, Runnable action) {
        VrShotPose previous = CURRENT.get();
        if (pose == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(pose);
        }
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
