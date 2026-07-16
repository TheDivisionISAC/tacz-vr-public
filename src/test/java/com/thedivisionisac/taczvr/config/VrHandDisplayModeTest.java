package com.thedivisionisac.taczvr.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies that the radial-menu cycle is deterministic and wraps to the default mode. */
class VrHandDisplayModeTest {
    @Test
    void cyclesThroughAllModesAndWraps() {
        assertEquals(VrHandDisplayMode.HIDDEN, VrHandDisplayMode.VIVECRAFT_SNAP.next());
        assertEquals(VrHandDisplayMode.TACZ_DURING_MANIPULATION, VrHandDisplayMode.HIDDEN.next());
        assertEquals(VrHandDisplayMode.VIVECRAFT_SNAP, VrHandDisplayMode.TACZ_DURING_MANIPULATION.next());
    }
}
