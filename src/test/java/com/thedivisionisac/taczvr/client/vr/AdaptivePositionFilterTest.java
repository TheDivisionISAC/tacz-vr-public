package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies tremor damping, deliberate-motion release, source resets, and strict controller-offset bounds. */
class AdaptivePositionFilterTest {
    private static final long FRAME_NANOS = 11_111_111L;
    private static final double TOLERANCE = 1.0E-8D;

    @Test
    void zeroStrengthTracksRawPositionExactly() {
        AdaptivePositionFilter filter = new AdaptivePositionFilter();
        filter.update("gun", Vec3.ZERO, 0.0D, 0.04D, 0.80D, 1L);

        Vec3 output = filter.update("gun", new Vec3(0.02D, 0.0D, 0.0D),
                0.0D, 0.04D, 0.80D, 1L + FRAME_NANOS);

        assertEquals(0.0D, output.distanceTo(new Vec3(0.02D, 0.0D, 0.0D)), TOLERANCE);
    }

    @Test
    void aimingDampensMillimeterTremor() {
        AdaptivePositionFilter filter = new AdaptivePositionFilter();
        long time = 1L;
        filter.update("gun", Vec3.ZERO, 0.96D, 0.04D, 0.80D, time);

        double filteredMotion = 0.0D;
        Vec3 previous = Vec3.ZERO;
        for (int frame = 0; frame < 80; frame++) {
            time += FRAME_NANOS;
            Vec3 raw = new Vec3((frame & 1) == 0 ? 0.0005D : -0.0005D, 0.0D, 0.0D);
            Vec3 output = filter.update("gun", raw, 0.96D, 0.04D, 0.80D, time);
            filteredMotion += previous.distanceTo(output);
            previous = output;
        }

        assertTrue(filteredMotion < 0.02D, "millimeter tremor should be visibly damped");
    }

    @Test
    void deliberateTranslationFullyReleasesFilter() {
        AdaptivePositionFilter filter = new AdaptivePositionFilter();
        filter.update("gun", Vec3.ZERO, 0.96D, 0.04D, 0.80D, 1L);
        Vec3 raw = new Vec3(0.03D, 0.0D, 0.0D);

        Vec3 output = filter.update("gun", raw, 0.96D, 0.04D, 0.80D, 1L + FRAME_NANOS);

        assertEquals(0.0D, output.distanceTo(raw), TOLERANCE);
    }

    @Test
    void outputNeverExceedsConfiguredControllerOffset() {
        AdaptivePositionFilter filter = new AdaptivePositionFilter();
        long time = 1L;
        filter.update("gun", Vec3.ZERO, 1.0D, 0.015D, 0.80D, time);

        for (int frame = 1; frame <= 240; frame++) {
            time += FRAME_NANOS;
            Vec3 raw = new Vec3(frame * 0.0005D, 0.0D, 0.0D);
            Vec3 output = filter.update("gun", raw, 1.0D, 0.015D, 0.80D, time);
            assertTrue(output.distanceTo(raw) <= 0.0150001D);
        }
    }
}
