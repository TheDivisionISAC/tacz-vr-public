package com.thedivisionisac.taczvr.client.control;

/** Tracks which optional TACZ VR menu owns the current Vivecraft radial session. */
public final class VrGunRadialState {
    public enum MenuKind {
        NONE,
        TACZ_GUN,
        LRTACTICAL_ITEM
    }

    private static MenuKind activeMenu = MenuKind.NONE;
    private VrGunRadialState() {
    }

    public static boolean isGunMenuActive() {
        return activeMenu != MenuKind.NONE;
    }

    public static boolean isLrTacticalMenuActive() {
        return activeMenu == MenuKind.LRTACTICAL_ITEM;
    }

    public static void beginMenu(MenuKind menuKind) {
        activeMenu = menuKind;
    }

    public static void closeMenu() {
        activeMenu = MenuKind.NONE;
    }
}
