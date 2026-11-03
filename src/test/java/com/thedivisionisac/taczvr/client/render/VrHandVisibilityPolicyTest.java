package com.thedivisionisac.taczvr.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VrHandVisibilityPolicyTest {
    @Test void firstPersonRespectsHiddenHandsInvisibleAndFullBodyArms() {
        assertTrue(VrHandVisibilityPolicy.allows(true,false,false,true,true,false,false,false));
        assertFalse(VrHandVisibilityPolicy.allows(true,false,false,false,true,false,false,false));
        assertFalse(VrHandVisibilityPolicy.allows(true,false,false,true,true,true,false,false));
        assertFalse(VrHandVisibilityPolicy.allows(true,false,false,true,true,false,true,false));
    }
    @Test void passAndItemSpecificGuardsRemainEffective() {
        assertFalse(VrHandVisibilityPolicy.allows(false,false,true,true,true,false,false,false));
        assertFalse(VrHandVisibilityPolicy.allows(false,true,false,true,false,false,false,false));
        assertTrue(VrHandVisibilityPolicy.allows(false,true,false,false,true,true,false,false));
        assertFalse(VrHandVisibilityPolicy.allows(true,false,false,true,true,false,false,true));
    }
}
