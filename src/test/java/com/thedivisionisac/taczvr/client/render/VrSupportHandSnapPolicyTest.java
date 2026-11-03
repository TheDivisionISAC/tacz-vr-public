package com.thedivisionisac.taczvr.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrSupportHandSnapPolicyTest {
    @Test
    void directLeftAnchorProximityRemainsAvailableForEveryGun() {
        assertTrue(VrSupportHandSnapPolicy.shouldAttach(true, false, false, 0.10F, 0.80F, 0.12F));
    }

    @Test
    void nonPistolCanUseMainHandProximityWhenOptionIsEnabled() {
        assertTrue(VrSupportHandSnapPolicy.shouldAttach(true, true, true, 0.40F, 0.10F, 0.12F));
    }

    @Test
    void nearMainHandDoesNotAddAlternatePathForPistol() {
        assertFalse(VrSupportHandSnapPolicy.shouldAttach(true, true, false, 0.40F, 0.10F, 0.12F));
    }

    @Test
    void nearMainHandOptionCanBeDisabledIndependently() {
        assertFalse(VrSupportHandSnapPolicy.shouldAttach(true, false, true, 0.40F, 0.10F, 0.12F));
    }

    @Test
    void masterSupportSnapStillDisablesEveryProximityPath() {
        assertFalse(VrSupportHandSnapPolicy.shouldAttach(false, true, true, 0.10F, 0.10F, 0.12F));
    }
}
