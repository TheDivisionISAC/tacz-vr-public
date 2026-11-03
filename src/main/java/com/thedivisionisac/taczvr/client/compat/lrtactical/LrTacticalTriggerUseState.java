package com.thedivisionisac.taczvr.client.compat.lrtactical;

/**
 * Tracks whether the VR attack trigger temporarily owns Minecraft's use-key state. Native use input that was already
 * held when the bridge starts is left untouched when the attack trigger is released.
 */
final class LrTacticalTriggerUseState {
    private boolean active;
    private boolean ownsUseKey;

    Step update(boolean eligible, boolean triggerDown, boolean useKeyDown) {
        if (!this.active) {
            if (!eligible || !triggerDown) {
                return Step.NONE;
            }
            this.active = true;
            this.ownsUseKey = !useKeyDown;
            return new Step(this.ownsUseKey ? KeyTransition.PRESS : KeyTransition.NONE, true, false);
        }

        if (!eligible || !triggerDown) {
            boolean releaseOwnedUse = this.ownsUseKey;
            this.active = false;
            this.ownsUseKey = false;
            return new Step(releaseOwnedUse ? KeyTransition.RELEASE : KeyTransition.NONE, false, releaseOwnedUse);
        }

        if (!useKeyDown) {
            this.ownsUseKey = true;
            return new Step(KeyTransition.PRESS, false, false);
        }
        return Step.NONE;
    }

    enum KeyTransition {
        NONE,
        PRESS,
        RELEASE
    }

    record Step(KeyTransition keyTransition, boolean ensureUseStarted, boolean releaseUsingItem) {
        static final Step NONE = new Step(KeyTransition.NONE, false, false);
    }
}
