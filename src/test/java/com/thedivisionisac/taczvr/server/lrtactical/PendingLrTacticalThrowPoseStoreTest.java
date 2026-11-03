package com.thedivisionisac.taczvr.server.lrtactical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingLrTacticalThrowPoseStoreTest {
    @Test
    void heldCookingRefreshesRemainBoundedAndAutomaticThrowConsumesLatestOnce() {
        var store = new PendingLrTacticalThrowPoseStore<String, Integer>();
        for (int tick = 0; tick <= 83; tick++) {
            store.put("player", tick, tick);
            assertEquals(1, store.pendingCount());
        }
        assertEquals(83, store.consume("player", 83, 4).orElseThrow());
        assertTrue(store.consume("player", 83, 4).isEmpty());
    }

    @Test
    void stoppedUpdatesCannotSupplyAStaleAutomaticThrow() {
        var store = new PendingLrTacticalThrowPoseStore<String, Integer>();
        for (int tick = 0; tick < 10; tick++) store.put("player", tick, tick);
        assertTrue(store.consume("player", 83, 4).isEmpty());
    }

    @Test
    void keepsOnlyTheLatestPoseAndConsumesItOnce() {
        PendingLrTacticalThrowPoseStore<String, String> store = new PendingLrTacticalThrowPoseStore<>();

        store.put("player", "old", 100L);
        store.put("player", "latest", 101L);

        assertEquals("latest", store.consume("player", 102L, 4L).orElseThrow());
        assertTrue(store.consume("player", 102L, 4L).isEmpty());
    }

    @Test
    void rejectsExpiredAndFutureDatedEntries() {
        PendingLrTacticalThrowPoseStore<String, String> store = new PendingLrTacticalThrowPoseStore<>();

        store.put("player", "expired", 100L);
        assertTrue(store.consume("player", 105L, 4L).isEmpty());

        store.put("player", "future", 110L);
        assertTrue(store.consume("player", 109L, 4L).isEmpty());
    }

    @Test
    void clearRemovesOnlyTheRequestedOwner() {
        PendingLrTacticalThrowPoseStore<String, String> store = new PendingLrTacticalThrowPoseStore<>();

        store.put("one", "one", 100L);
        store.put("two", "two", 100L);
        store.clear("one");

        assertEquals(1, store.pendingCount());
        assertTrue(store.consume("one", 100L, 4L).isEmpty());
        assertEquals("two", store.consume("two", 100L, 4L).orElseThrow());
    }
}
