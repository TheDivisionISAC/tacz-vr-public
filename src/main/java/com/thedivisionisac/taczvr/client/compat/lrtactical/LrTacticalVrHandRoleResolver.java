package com.thedivisionisac.taczvr.client.compat.lrtactical;

/**
 * Maps physical Vivecraft controllers to the left/right hand roles authored into an LRTactical model.
 */
public final class LrTacticalVrHandRoleResolver {
    private LrTacticalVrHandRoleResolver() {
    }

    public static LrTacticalVrItemHandResolver.AuthoredHand effectivePrimaryHand(
            LrTacticalVrItemHandResolver.AuthoredHand inferredPrimaryHand) {
        return inferredPrimaryHand == LrTacticalVrItemHandResolver.AuthoredHand.UNKNOWN
                ? LrTacticalVrItemHandResolver.AuthoredHand.RIGHT
                : inferredPrimaryHand;
    }

    public static LrTacticalVrItemHandResolver.AuthoredHand physicalMainAuthoredHand(boolean leftHanded) {
        return leftHanded
                ? LrTacticalVrItemHandResolver.AuthoredHand.LEFT
                : LrTacticalVrItemHandResolver.AuthoredHand.RIGHT;
    }

    public static LrTacticalVrItemHandResolver.AuthoredHand physicalSupportAuthoredHand(boolean leftHanded) {
        return leftHanded
                ? LrTacticalVrItemHandResolver.AuthoredHand.RIGHT
                : LrTacticalVrItemHandResolver.AuthoredHand.LEFT;
    }

    /**
     * The model's item-holding hand follows its authored palm continuously. The other hand stays controller-driven
     * until a supported use animation needs the model's authored two-hand motion.
     */
    public static boolean shouldFollowAuthoredAnchor(
            LrTacticalVrItemHandResolver.AuthoredHand inferredPrimaryHand,
            LrTacticalVrItemHandResolver.AuthoredHand renderedHand,
            boolean supportedUseAnimationActive) {
        if (renderedHand == LrTacticalVrItemHandResolver.AuthoredHand.UNKNOWN) {
            return false;
        }
        return renderedHand == effectivePrimaryHand(inferredPrimaryHand)
                || supportedUseAnimationActive;
    }
}
