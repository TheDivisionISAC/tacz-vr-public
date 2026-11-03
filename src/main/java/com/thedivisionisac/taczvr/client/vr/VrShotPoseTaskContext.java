package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Binds immutable pose/muzzle/grip facts and opaque source identities to TACZ's delayed task. VR packet pairs return
 * to the client executor before querying the live source or sending. Session replacement drops an obsolete pair;
 * held-stack replacement keeps only TACZ's original packet unless a caller can verify the same slot/item/gun key
 * and recapture a fresh snapshot on the client executor. Old muzzle/grip facts never transfer to a replacement.
 * Non-VR tasks stay unchanged.
 * No game objects are dereferenced here; the caller supplies source identities on the client thread. Nested restore
 * prevents pooled scheduler threads from leaking a snapshot into the next task.
 */
public final class VrShotPoseTaskContext {
    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private VrShotPoseTaskContext() {
    }

    public static Runnable bind(@Nullable Snapshot snapshot, Runnable action) {
        Objects.requireNonNull(action, "action");
        return () -> runWith(snapshot, action);
    }

    public static Optional<Snapshot> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Keeps the addon and original packet ordered in one client-thread operation; never sends into a new session. */
    public static void dispatch(@Nullable Snapshot snapshot, Executor clientExecutor, Supplier<Source> liveSource,
                                Consumer<Snapshot> sendPose, Runnable sendOriginal) {
        dispatch(snapshot, clientExecutor, liveSource, null, sendPose, sendOriginal);
    }

    /** Optional recapture handles inventory synchronization without accepting stale facts from the replaced stack. */
    public static void dispatch(@Nullable Snapshot snapshot, Executor clientExecutor, Supplier<Source> liveSource,
                                @Nullable Function<Source, Snapshot> recapture,
                                Consumer<Snapshot> sendPose, Runnable sendOriginal) {
        if (snapshot == null) {
            sendOriginal.run();
            return;
        }
        clientExecutor.execute(() -> {
            Source current = liveSource.get();
            if (!snapshot.source().sameSession(current)) {
                return;
            }
            try {
                if (snapshot.source().heldItem() == current.heldItem()) {
                    sendPose.accept(snapshot);
                } else if (recapture != null && snapshot.source().sameWeapon(current)) {
                    Snapshot fresh = recapture.apply(current);
                    if (fresh != null && fresh.source().sameWeapon(current)
                            && fresh.source().heldItem() == current.heldItem()) {
                        sendPose.accept(fresh);
                    }
                }
            } finally {
                sendOriginal.run();
            }
        });
    }

    private static void runWith(@Nullable Snapshot snapshot, Runnable action) {
        Snapshot previous = CURRENT.get();
        if (snapshot == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(snapshot);
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

    public record Snapshot(VrShotPose pose, @Nullable Vec3 muzzlePosition, boolean supportHandSnapped, Source source) {
        public Snapshot {
            Objects.requireNonNull(pose, "pose");
            Objects.requireNonNull(source, "source");
        }
    }

    /** Immutable logical weapon key captured on the client thread; mutable ammo/heat data is intentionally absent. */
    public record WeaponKey(int selectedSlot, Object itemType, Object gunId) {
        public WeaponKey {
            Objects.requireNonNull(itemType, "itemType");
            Objects.requireNonNull(gunId, "gunId");
        }
    }

    /** Session and stack references remain identity-based. A matching optional key only permits fresh recapture. */
    public record Source(Object connection, Object world, Object player, Object heldItem, @Nullable WeaponKey weapon) {
        public Source(Object connection, Object world, Object player, Object heldItem) {
            this(connection, world, player, heldItem, null);
        }

        public Source {
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(heldItem, "heldItem");
        }

        boolean sameSession(@Nullable Source other) {
            return other != null && this.connection == other.connection && this.world == other.world
                    && this.player == other.player;
        }

        boolean sameWeapon(@Nullable Source other) {
            return sameSession(other) && this.weapon != null && this.weapon.equals(other.weapon);
        }
    }
}
