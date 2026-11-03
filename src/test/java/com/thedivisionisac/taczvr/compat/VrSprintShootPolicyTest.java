package com.thedivisionisac.taczvr.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies that sprint-fire compatibility changes only the timer presented for an active VR player. */
class VrSprintShootPolicyTest {
    @Test
    void vrPlayerBypassesTaczSprintTimer() {
        assertEquals(0.0F, VrSprintShootPolicy.effectiveSprintTime(0.35F, true));
    }

    @Test
    void nonVrPlayerKeepsUpstreamSprintTimer() {
        assertEquals(0.35F, VrSprintShootPolicy.effectiveSprintTime(0.35F, false));
    }
}
