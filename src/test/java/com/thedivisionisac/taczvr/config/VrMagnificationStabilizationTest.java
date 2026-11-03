package com.thedivisionisac.taczvr.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Checks increasing optical damping without overriding disabled/zero settings or accepting invalid zoom data. */
class VrMagnificationStabilizationTest {
    @Test
    void increasesWithZoomAndCapsBeyondTwentyFive() {
        double previous = 0.55D;
        for (double zoom : new double[]{1.25D, 4.25D, 10.0D, 25.0D}) {
            double strength = VrMagnificationStabilization.effectiveStrength(0.55D, zoom, true);
            assertTrue(strength > previous && strength < 1.0D);
            previous = strength;
        }
        assertEquals(0.91D, previous, 1.0E-12D);
        assertEquals(previous, VrMagnificationStabilization.effectiveStrength(0.55D, 1000.0D, true));
    }

    @Test
    void disabledOnePowerAndInvalidZoomPreserveBaseline() {
        assertEquals(0.58D, VrMagnificationStabilization.effectiveStrength(0.58D, 25, false));
        for (double zoom : new double[]{1, 0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(0.58D, VrMagnificationStabilization.effectiveStrength(0.58D, zoom, true));
        }
    }

    @Test
    void zeroSliderStillDisablesAndStrengthStaysBounded() {
        double disabled = VrStabilizationLevel.effectiveStrength(0.58D, 0);
        assertEquals(0.0D, VrMagnificationStabilization.effectiveStrength(disabled, 25, true));
        assertEquals(1.0D, VrMagnificationStabilization.effectiveStrength(1.0D, 25, true));
        assertEquals(0.0D, VrMagnificationStabilization.effectiveStrength(Double.NaN, 25, true));
    }
}
