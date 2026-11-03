package com.thedivisionisac.taczvr.client.render;

/** Pure form of Vivecraft's upstream arm eligibility, used when planning ownership of each native hand draw. */
public final class VrHandVisibilityPolicy {
    private VrHandVisibilityPolicy() {
    }

    public static boolean allows(boolean firstPerson, boolean thirdPerson, boolean camera,
                                 boolean showHands, boolean mixedRealityHands, boolean fullBodyArms,
                                 boolean invisible, boolean itemSuppressesHand) {
        return !camera && !invisible && !itemSuppressesHand
                && (!thirdPerson || mixedRealityHands)
                && (!firstPerson || (showHands && !fullBodyArms));
    }
}
