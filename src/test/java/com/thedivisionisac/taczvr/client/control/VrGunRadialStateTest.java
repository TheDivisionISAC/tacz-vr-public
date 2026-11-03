package com.thedivisionisac.taczvr.client.control;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrGunRadialStateTest {
    @AfterEach
    void resetMenu() {
        VrGunRadialState.closeMenu();
    }

    @Test
    void closingLrTacticalMenuClearsActiveSession() {
        VrGunRadialState.beginMenu(VrGunRadialState.MenuKind.LRTACTICAL_ITEM);

        assertTrue(VrGunRadialState.isGunMenuActive());
        assertTrue(VrGunRadialState.isLrTacticalMenuActive());
        VrGunRadialState.closeMenu();
        assertFalse(VrGunRadialState.isGunMenuActive());
    }

    @Test
    void closingTaczMenuDoesNotEncodeAnImplicitAction() {
        VrGunRadialState.beginMenu(VrGunRadialState.MenuKind.TACZ_GUN);
        assertTrue(VrGunRadialState.isGunMenuActive());
        VrGunRadialState.closeMenu();
        assertFalse(VrGunRadialState.isGunMenuActive());
    }
}
