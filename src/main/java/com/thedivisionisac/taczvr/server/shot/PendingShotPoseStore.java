package com.thedivisionisac.taczvr.server.shot;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded timestamp store that keeps the first pose for a shot and consumes it at most once.
 * The caller owns thread confinement and supplies the authoritative clock and limits.
 */
final class PendingShotPoseStore<K, V> {
    private final Map<K, LinkedHashMap<Long, PendingValue<V>>> pendingByOwner = new HashMap<>();

    void put(K owner, long timestamp, V value, long gameTime, int maximumPerOwner, long ttlTicks) {
        LinkedHashMap<Long, PendingValue<V>> pending = pendingByOwner.computeIfAbsent(
                owner, ignored -> new LinkedHashMap<>()
        );
        purgeExpired(pending, gameTime, ttlTicks);
        if (pending.containsKey(timestamp)) {
            return;
        }
        while (pending.size() >= maximumPerOwner) {
            Iterator<Long> iterator = pending.keySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }
        pending.put(timestamp, new PendingValue<>(value, gameTime));
    }

    Optional<V> consume(K owner, long timestamp, long gameTime, long ttlTicks) {
        LinkedHashMap<Long, PendingValue<V>> pending = pendingByOwner.get(owner);
        if (pending == null) {
            return Optional.empty();
        }
        purgeExpired(pending, gameTime, ttlTicks);
        PendingValue<V> consumed = pending.remove(timestamp);
        if (pending.isEmpty()) {
            pendingByOwner.remove(owner);
        }
        return consumed == null ? Optional.empty() : Optional.of(consumed.value());
    }

    void clear(K owner) {
        pendingByOwner.remove(owner);
    }

    int pendingCount(K owner) {
        LinkedHashMap<Long, PendingValue<V>> pending = pendingByOwner.get(owner);
        return pending == null ? 0 : pending.size();
    }

    private static <V> void purgeExpired(
            LinkedHashMap<Long, PendingValue<V>> pending,
            long gameTime,
            long ttlTicks
    ) {
        pending.values().removeIf(value -> gameTime - value.createdGameTime() > ttlTicks);
    }

    private record PendingValue<V>(V value, long createdGameTime) {
    }
}
