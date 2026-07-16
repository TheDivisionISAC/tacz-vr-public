package com.thedivisionisac.taczvr.server.shot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies timing, ordering, replay protection, and bounds without requiring a running server. */
class PendingShotPoseStoreTest {
    private static final String PLAYER = "player";
    private static final long TTL = 10L;

    @Test
    void keepsFirstPoseWhenTimestampIsDuplicated() {
        PendingShotPoseStore<String, String> store = new PendingShotPoseStore<>();

        store.put(PLAYER, 40L, "first", 100L, 16, TTL);
        store.put(PLAYER, 40L, "duplicate", 101L, 16, TTL);

        assertEquals("first", store.consume(PLAYER, 40L, 101L, TTL).orElseThrow());
        assertTrue(store.consume(PLAYER, 40L, 101L, TTL).isEmpty());
    }

    @Test
    void consumesOutOfOrderTimestampsByExactKey() {
        PendingShotPoseStore<String, String> store = new PendingShotPoseStore<>();

        store.put(PLAYER, 30L, "late", 100L, 16, TTL);
        store.put(PLAYER, 10L, "early", 100L, 16, TTL);
        store.put(PLAYER, 20L, "middle", 100L, 16, TTL);

        assertEquals("middle", store.consume(PLAYER, 20L, 101L, TTL).orElseThrow());
        assertEquals("early", store.consume(PLAYER, 10L, 101L, TTL).orElseThrow());
        assertEquals("late", store.consume(PLAYER, 30L, 101L, TTL).orElseThrow());
    }

    @Test
    void expiresOnlyAfterTheConfiguredBoundary() {
        PendingShotPoseStore<String, String> store = new PendingShotPoseStore<>();

        store.put(PLAYER, 1L, "boundary", 100L, 16, TTL);
        assertEquals("boundary", store.consume(PLAYER, 1L, 110L, TTL).orElseThrow());

        store.put(PLAYER, 2L, "expired", 100L, 16, TTL);
        assertTrue(store.consume(PLAYER, 2L, 111L, TTL).isEmpty());
    }

    @Test
    void evictsOldestInsertionAtCapacity() {
        PendingShotPoseStore<String, String> store = new PendingShotPoseStore<>();

        store.put(PLAYER, 30L, "oldest", 100L, 2, TTL);
        store.put(PLAYER, 10L, "second", 100L, 2, TTL);
        store.put(PLAYER, 20L, "newest", 100L, 2, TTL);

        assertTrue(store.consume(PLAYER, 30L, 100L, TTL).isEmpty());
        assertEquals("second", store.consume(PLAYER, 10L, 100L, TTL).orElseThrow());
        assertEquals("newest", store.consume(PLAYER, 20L, 100L, TTL).orElseThrow());
    }

    @Test
    void isolatesOwnersAndClearsOnlyTheRequestedOwner() {
        PendingShotPoseStore<String, String> store = new PendingShotPoseStore<>();

        store.put("one", 1L, "one-pose", 100L, 16, TTL);
        store.put("two", 1L, "two-pose", 100L, 16, TTL);
        store.clear("one");

        assertEquals(0, store.pendingCount("one"));
        assertEquals(1, store.pendingCount("two"));
        assertEquals("two-pose", store.consume("two", 1L, 100L, TTL).orElseThrow());
    }
}
