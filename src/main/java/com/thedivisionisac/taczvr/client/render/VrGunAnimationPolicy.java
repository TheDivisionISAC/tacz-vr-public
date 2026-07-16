package com.thedivisionisac.taczvr.client.render;

import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;

/**
 * Small, side-effect-free animation input policy used by the TACZ tick hook. Keeping the substitution here makes the
 * VR-only behavior testable without starting a client.
 */
public final class VrGunAnimationPolicy {
    private VrGunAnimationPolicy() {
    }

    public static String replaceSprintInput(String input, boolean suppressSprintAnimation) {
        if (suppressSprintAnimation && GunAnimationConstant.INPUT_RUN.equals(input)) {
            return GunAnimationConstant.INPUT_WALK;
        }
        return input;
    }
}
