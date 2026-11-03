package com.thedivisionisac.taczvr.server.lrtactical;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Single-use, one-entry-per-player pending store for an immediately following release-use action.
 */
final class PendingLrTacticalThrowPoseStore<K, V> {
    private final Map<K, Entry<V>> pending = new HashMap<>();

    void put(K owner, V value, long gameTime) {
        this.pending.put(owner, new Entry<>(value, gameTime));
    }

    Optional<V> consume(K owner, long gameTime, long ttlTicks) {
        Entry<V> entry = this.pending.remove(owner);
        if (entry == null) {
            return Optional.empty();
        }
        long age = gameTime - entry.gameTime;
        return age >= 0L && age <= ttlTicks ? Optional.of(entry.value) : Optional.empty();
    }

    void clear(K owner) {
        this.pending.remove(owner);
    }

    int pendingCount() {
        return this.pending.size();
    }

    private record Entry<V>(V value, long gameTime) {
    }
}
