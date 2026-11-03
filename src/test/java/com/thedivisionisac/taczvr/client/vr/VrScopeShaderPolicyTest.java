package com.thedivisionisac.taczvr.client.vr;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.thedivisionisac.taczvr.client.vr.VrScopeShaderPolicy.Decision.*;

class VrScopeShaderPolicyTest {
    @Test
    void installedLoaderWithoutShadersAlwaysUsesOrdinaryRendering() {
        for (boolean enabled : new boolean[]{false, true}) {
            for (boolean supported : new boolean[]{false, true}) {
                assertEquals(ORDINARY, VrScopeShaderPolicy.decide(false, enabled, supported));
            }
        }
    }

    @Test
    void activeShadersRetainOptOutAndVersionGuards() {
        assertEquals(COMPAT_DISABLED, VrScopeShaderPolicy.decide(true, false, false));
        assertEquals(COMPAT_DISABLED, VrScopeShaderPolicy.decide(true, false, true));
        assertEquals(VERSION_UNSUPPORTED, VrScopeShaderPolicy.decide(true, true, false));
        assertEquals(SHADER, VrScopeShaderPolicy.decide(true, true, true));
    }
}
