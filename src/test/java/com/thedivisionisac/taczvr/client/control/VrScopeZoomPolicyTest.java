package com.thedivisionisac.taczvr.client.control;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VrScopeZoomPolicyTest {
    @Test
    void fixedAcogAndAbsentSettingsDoNotSendRedundantCycles() {
        assertFalse(VrScopeZoomPolicy.hasAlternatives(new float[]{2.5F}, new int[]{1}));
        assertFalse(VrScopeZoomPolicy.hasAlternatives(new float[]{4}, null));
        assertFalse(VrScopeZoomPolicy.hasAlternatives(null, null));
        assertFalse(VrScopeZoomPolicy.hasAlternatives(new float[0], new int[0]));
    }

    @Test
    void variableZoomRetainsCyclingEvenWithASinglePhysicalView() {
        assertTrue(VrScopeZoomPolicy.hasAlternatives(new float[]{1, 3, 6}, new int[]{1}));
    }

    @Test
    void equalZoomButDifferentSightViewsStillCycles() {
        assertTrue(VrScopeZoomPolicy.hasAlternatives(new float[]{4, 4}, new int[]{1, 2}));
    }

    @Test
    void repeatedValuesStayFixedButLaterDistinctEntriesRemainReachable() {
        assertFalse(VrScopeZoomPolicy.hasAlternatives(new float[]{4, 4}, new int[]{1, 1}));
        assertTrue(VrScopeZoomPolicy.hasAlternatives(new float[]{4, 4, 8}, new int[]{1}));
    }
}
