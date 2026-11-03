package com.thedivisionisac.taczvr.client.compat.lrtactical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrTacticalVrAnimationStateTest {
    @Test
    void recognizesLrTacticalAuthoredActions() {
        assertTrue(LrTacticalVrAnimationState.isManipulationAnimationName("draw"));
        assertTrue(LrTacticalVrAnimationState.isManipulationAnimationName("re_draw"));
        assertTrue(LrTacticalVrAnimationState.isManipulationAnimationName("put_away"));
        assertTrue(LrTacticalVrAnimationState.isManipulationAnimationName("inspect"));
        assertTrue(LrTacticalVrAnimationState.isManipulationAnimationName("use"));
        assertTrue(LrTacticalVrAnimationState.isManipulationAnimationName("unlock_safe_loop"));
        assertTrue(LrTacticalVrAnimationState.isManipulationAnimationName("throw"));
        assertTrue(LrTacticalVrAnimationState.isManipulationAnimationName("melee_3"));
        assertTrue(LrTacticalVrAnimationState.isManipulationAnimationName("attack_left"));
    }

    @Test
    void excludesStableAndLocomotionTracks() {
        assertFalse(LrTacticalVrAnimationState.isManipulationAnimationName("static_idle"));
        assertFalse(LrTacticalVrAnimationState.isManipulationAnimationName("idle"));
        assertFalse(LrTacticalVrAnimationState.isManipulationAnimationName("run_start"));
        assertFalse(LrTacticalVrAnimationState.isManipulationAnimationName("walk_forward"));
        assertFalse(LrTacticalVrAnimationState.isManipulationAnimationName("sprint"));
        assertFalse(LrTacticalVrAnimationState.isManipulationAnimationName("unused"));
    }
}
