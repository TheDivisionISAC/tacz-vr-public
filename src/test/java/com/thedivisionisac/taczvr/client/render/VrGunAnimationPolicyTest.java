package com.thedivisionisac.taczvr.client.render;

import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies that only the run state is downgraded and that the policy is inert when disabled. */
class VrGunAnimationPolicyTest {
    @Test
    void replacesOnlyEnabledRunInput() {
        assertEquals(GunAnimationConstant.INPUT_WALK,
                VrGunAnimationPolicy.replaceSprintInput(GunAnimationConstant.INPUT_RUN, true));
        assertEquals(GunAnimationConstant.INPUT_RUN,
                VrGunAnimationPolicy.replaceSprintInput(GunAnimationConstant.INPUT_RUN, false));
        assertEquals(GunAnimationConstant.INPUT_IDLE,
                VrGunAnimationPolicy.replaceSprintInput(GunAnimationConstant.INPUT_IDLE, true));
    }
}
