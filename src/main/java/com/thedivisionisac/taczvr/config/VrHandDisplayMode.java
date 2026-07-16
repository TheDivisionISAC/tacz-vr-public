package com.thedivisionisac.taczvr.config;

/**
 * Selects the VR hand presentation policy while a TACZ gun is held. The enum is persisted in Forge's client config
 * and can be cycled at runtime from the gun radial menu.
 */
public enum VrHandDisplayMode {
    VIVECRAFT_SNAP("menu.taczvr.hand_mode.vivecraft_snap"),
    HIDDEN("menu.taczvr.hand_mode.hidden"),
    TACZ_DURING_MANIPULATION("menu.taczvr.hand_mode.tacz_during_manipulation");

    private static final VrHandDisplayMode[] VALUES = values();

    private final String translationKey;

    VrHandDisplayMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return this.translationKey;
    }

    public VrHandDisplayMode next() {
        return VALUES[(this.ordinal() + 1) % VALUES.length];
    }
}
