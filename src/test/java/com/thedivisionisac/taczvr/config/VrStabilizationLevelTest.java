package com.thedivisionisac.taczvr.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VrStabilizationLevelTest {
    @Test
    void defaultLevelPreservesValidatedStrengthsExactly() {
        assertEquals(0.55D, VrStabilizationLevel.effectiveStrength(0.55D, 50), 1.0E-12D);
        assertEquals(0.96D, VrStabilizationLevel.effectiveStrength(0.96D, 50), 1.0E-12D);
    }

    @Test
    void lowerHalfCanDisableOrReduceFiltering() {
        assertEquals(0.0D, VrStabilizationLevel.effectiveStrength(0.96D, 0), 1.0E-12D);
        assertEquals(0.275D, VrStabilizationLevel.effectiveStrength(0.55D, 25), 1.0E-12D);
    }

    @Test
    void upperHalfIsLimitedAndCannotExceedFilterBounds() {
        assertEquals(0.6875D, VrStabilizationLevel.effectiveStrength(0.55D, 100), 1.0E-12D);
        assertEquals(1.0D, VrStabilizationLevel.effectiveStrength(0.96D, 100), 1.0E-12D);
    }

    @Test
    void sliderConversionsClampAndRoundPredictably() {
        assertEquals(0.5D, VrStabilizationLevel.toSliderValue(50), 1.0E-12D);
        assertEquals(33, VrStabilizationLevel.fromSliderValue(0.333D));
        assertEquals(100, VrStabilizationLevel.fromSliderValue(2.0D));
        assertEquals(50, VrStabilizationLevel.fromSliderValue(Double.NaN));
    }
}
