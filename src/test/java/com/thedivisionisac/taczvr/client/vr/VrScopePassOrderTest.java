package com.thedivisionisac.taczvr.client.vr;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VrScopePassOrderTest {
    @Test
    void insertsBeforeStereoWithoutChangingInputOrOtherPasses() {
        List<String> original = List.of("LEFT", "RIGHT", "CENTER", "SCOPEL");
        assertEquals(List.of("SCOPER", "LEFT", "RIGHT", "CENTER", "SCOPEL"),
                VrScopePassOrder.beforeEyes(original, "LEFT", "RIGHT", "SCOPER").orElseThrow());
        assertEquals(4, original.size());
    }

    @Test
    void rejectsReservedPassConflictsMissingEyesAndNonstandardFirstPass() {
        for (List<String> passes : List.of(List.of("LEFT", "RIGHT", "SCOPER"), List.of("LEFT"),
                List.of("RIGHT", "LEFT"), List.of("LEFT", "LEFT", "RIGHT"))) {
            assertTrue(VrScopePassOrder.beforeEyes(passes, "LEFT", "RIGHT", "SCOPER").isEmpty());
        }
    }
}
