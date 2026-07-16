package com.thedivisionisac.taczvr.client.control;

/**
 * Tracks whether one radial-menu session consumed an action so an empty release can mean reload.
 */
public final class VrGunRadialState {
    private static boolean gunMenuActive;
    private static boolean actionSelected;

    private VrGunRadialState() {
    }

    public static boolean isGunMenuActive() {
        return gunMenuActive;
    }

    public static void beginGunMenu(boolean active) {
        gunMenuActive = active;
        actionSelected = false;
    }

    public static void markActionSelected() {
        if (gunMenuActive) {
            actionSelected = true;
        }
    }

    public static boolean closeGunMenuWithoutSelection() {
        boolean emptyClose = gunMenuActive && !actionSelected;
        gunMenuActive = false;
        actionSelected = false;
        return emptyClose;
    }
}
