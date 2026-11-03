package com.thedivisionisac.taczvr.client.compat.lrtactical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrTacticalVrHandRoleResolverTest {
    @Test
    void rightHandedControllersMapToMatchingAuthoredHands() {
        assertSame(
                LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                LrTacticalVrHandRoleResolver.physicalMainAuthoredHand(false)
        );
        assertSame(
                LrTacticalVrItemHandResolver.AuthoredHand.LEFT,
                LrTacticalVrHandRoleResolver.physicalSupportAuthoredHand(false)
        );
    }

    @Test
    void leftHandedControllersInvertAuthoredHandRoles() {
        assertSame(
                LrTacticalVrItemHandResolver.AuthoredHand.LEFT,
                LrTacticalVrHandRoleResolver.physicalMainAuthoredHand(true)
        );
        assertSame(
                LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                LrTacticalVrHandRoleResolver.physicalSupportAuthoredHand(true)
        );
    }

    @Test
    void leftHeldItemKeepsPrimaryAttachedAndAddsSecondaryDuringUse() {
        assertTrue(LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                LrTacticalVrItemHandResolver.AuthoredHand.LEFT,
                LrTacticalVrItemHandResolver.AuthoredHand.LEFT,
                false
        ));
        assertFalse(LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                LrTacticalVrItemHandResolver.AuthoredHand.LEFT,
                LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                false
        ));
        assertTrue(LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                LrTacticalVrItemHandResolver.AuthoredHand.LEFT,
                LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                true
        ));
        assertTrue(LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                LrTacticalVrItemHandResolver.AuthoredHand.LEFT,
                LrTacticalVrItemHandResolver.AuthoredHand.LEFT,
                true
        ));
    }

    @Test
    void rightHeldItemKeepsPrimaryAttachedAndAddsSecondaryDuringUse() {
        assertTrue(LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                false
        ));
        assertFalse(LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                LrTacticalVrItemHandResolver.AuthoredHand.LEFT,
                false
        ));
        assertTrue(LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                LrTacticalVrItemHandResolver.AuthoredHand.LEFT,
                true
        ));
        assertTrue(LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                true
        ));
    }

    @Test
    void unknownPrimaryPreservesRightHandFallback() {
        assertSame(
                LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                LrTacticalVrHandRoleResolver.effectivePrimaryHand(
                        LrTacticalVrItemHandResolver.AuthoredHand.UNKNOWN
                )
        );
        assertTrue(LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                LrTacticalVrItemHandResolver.AuthoredHand.UNKNOWN,
                LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                false
        ));
        assertFalse(LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                LrTacticalVrItemHandResolver.AuthoredHand.UNKNOWN,
                LrTacticalVrItemHandResolver.AuthoredHand.LEFT,
                false
        ));
        assertTrue(LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                LrTacticalVrItemHandResolver.AuthoredHand.UNKNOWN,
                LrTacticalVrItemHandResolver.AuthoredHand.LEFT,
                true
        ));
    }

    @Test
    void unknownRenderedHandNeverFollowsAnAnchor() {
        assertFalse(LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                LrTacticalVrItemHandResolver.AuthoredHand.LEFT,
                LrTacticalVrItemHandResolver.AuthoredHand.UNKNOWN,
                false
        ));
        assertFalse(LrTacticalVrHandRoleResolver.shouldFollowAuthoredAnchor(
                LrTacticalVrItemHandResolver.AuthoredHand.RIGHT,
                LrTacticalVrItemHandResolver.AuthoredHand.UNKNOWN,
                true
        ));
    }
}
