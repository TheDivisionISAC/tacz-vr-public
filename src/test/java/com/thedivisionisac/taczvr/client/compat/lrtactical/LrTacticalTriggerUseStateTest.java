package com.thedivisionisac.taczvr.client.compat.lrtactical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrTacticalTriggerUseStateTest {
    @Test
    void ownsAndReleasesUseKeyForAnOrdinaryTriggerHold() {
        LrTacticalTriggerUseState state = new LrTacticalTriggerUseState();

        LrTacticalTriggerUseState.Step start = state.update(true, true, false);
        assertEquals(LrTacticalTriggerUseState.KeyTransition.PRESS, start.keyTransition());
        assertTrue(start.ensureUseStarted());
        assertFalse(start.releaseUsingItem());

        assertEquals(LrTacticalTriggerUseState.Step.NONE, state.update(true, true, true));

        LrTacticalTriggerUseState.Step release = state.update(true, false, true);
        assertEquals(LrTacticalTriggerUseState.KeyTransition.RELEASE, release.keyTransition());
        assertFalse(release.ensureUseStarted());
        assertTrue(release.releaseUsingItem());
    }

    @Test
    void preservesNativeUseThatWasAlreadyHeld() {
        LrTacticalTriggerUseState state = new LrTacticalTriggerUseState();

        LrTacticalTriggerUseState.Step start = state.update(true, true, true);
        assertEquals(LrTacticalTriggerUseState.KeyTransition.NONE, start.keyTransition());
        assertTrue(start.ensureUseStarted());

        LrTacticalTriggerUseState.Step release = state.update(true, false, true);
        assertEquals(LrTacticalTriggerUseState.KeyTransition.NONE, release.keyTransition());
        assertFalse(release.releaseUsingItem());
    }

    @Test
    void takesOwnershipIfNativeUseIsReleasedDuringTheTriggerHold() {
        LrTacticalTriggerUseState state = new LrTacticalTriggerUseState();
        state.update(true, true, true);

        LrTacticalTriggerUseState.Step takeover = state.update(true, true, false);
        assertEquals(LrTacticalTriggerUseState.KeyTransition.PRESS, takeover.keyTransition());
        assertFalse(takeover.ensureUseStarted());

        LrTacticalTriggerUseState.Step release = state.update(true, false, true);
        assertEquals(LrTacticalTriggerUseState.KeyTransition.RELEASE, release.keyTransition());
        assertTrue(release.releaseUsingItem());
    }

    @Test
    void releasesOwnedUseWhenEligibilityIsLost() {
        LrTacticalTriggerUseState state = new LrTacticalTriggerUseState();
        state.update(true, true, false);

        LrTacticalTriggerUseState.Step release = state.update(false, true, true);
        assertEquals(LrTacticalTriggerUseState.KeyTransition.RELEASE, release.keyTransition());
        assertTrue(release.releaseUsingItem());
        assertEquals(LrTacticalTriggerUseState.Step.NONE, state.update(false, false, false));
    }
}
