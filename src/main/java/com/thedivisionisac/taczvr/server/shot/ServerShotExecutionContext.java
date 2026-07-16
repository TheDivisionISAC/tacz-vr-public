package com.thedivisionisac.taczvr.server.shot;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Call-scoped bridge from TACZ packet handling to the script API object created during the same shoot invocation.
 * Burst tasks copy the validated muzzle into their API instance before this thread-local is cleared.
 */
public final class ServerShotExecutionContext {
    private static final ThreadLocal<ValidatedShotPose> CURRENT = new ThreadLocal<>();

    private ServerShotExecutionContext() {
    }

    public static <T> T runWith(ValidatedShotPose pose, Supplier<T> action) {
        ValidatedShotPose previous = CURRENT.get();
        CURRENT.set(pose);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static Optional<ValidatedShotPose> current() {
        return Optional.ofNullable(CURRENT.get());
    }
}
