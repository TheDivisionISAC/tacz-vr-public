package com.thedivisionisac.taczvr.mixin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaczVrMixinPluginTest {
    private static final String COMMON = "com.thedivisionisac.taczvr.mixin.common.ExampleMixin";

    @Test
    void oculusLifecycleRequiresBothOculusAndVivecraft() {
        String mixin = "com.thedivisionisac.taczvr.mixin.client.OculusScopePipelineMixin";
        assertFalse(TaczVrMixinPlugin.shouldApplyOptionalMixin(mixin, false, false, true));
        assertFalse(TaczVrMixinPlugin.shouldApplyOptionalMixin(mixin, true, false, false));
        assertTrue(TaczVrMixinPlugin.shouldApplyOptionalMixin(mixin, true, false, true));
    }
    private static final String CLIENT = "com.thedivisionisac.taczvr.mixin.client.ExampleMixin";
    private static final String LRTACTICAL =
            "com.thedivisionisac.taczvr.mixin.client.lrtactical.LrTacticalThrowableRendererMixin";
    private static final String COMMON_LRTACTICAL =
            "com.thedivisionisac.taczvr.mixin.common.lrtactical.LrTacticalThrowableItemMixin";

    @Test
    void commonMixinsRemainAvailableWithoutOptionalClientMods() {
        assertTrue(TaczVrMixinPlugin.shouldApplyOptionalMixin(COMMON, false, false));
    }

    @Test
    void ordinaryClientMixinsRequireVivecraftOnly() {
        assertFalse(TaczVrMixinPlugin.shouldApplyOptionalMixin(CLIENT, false, true));
        assertTrue(TaczVrMixinPlugin.shouldApplyOptionalMixin(CLIENT, true, false));
    }

    @Test
    void lrTacticalMixinsRequireBothOptionalMods() {
        assertFalse(TaczVrMixinPlugin.shouldApplyOptionalMixin(LRTACTICAL, false, false));
        assertFalse(TaczVrMixinPlugin.shouldApplyOptionalMixin(LRTACTICAL, true, false));
        assertTrue(TaczVrMixinPlugin.shouldApplyOptionalMixin(LRTACTICAL, true, true));
    }

    @Test
    void lrTacticalCommonMixinRequiresLrTacticalButNotClientVivecraft() {
        assertFalse(TaczVrMixinPlugin.shouldApplyOptionalMixin(COMMON_LRTACTICAL, false, false));
        assertTrue(TaczVrMixinPlugin.shouldApplyOptionalMixin(COMMON_LRTACTICAL, false, true));
    }
}
