package com.thedivisionisac.taczvr.client.vr;

import com.thedivisionisac.taczvr.config.VrMagnificationStabilization;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real filters: stronger high-zoom tremor damping, deliberate-motion release and matching shot cache. */
class VrMagnificationFilterTest {
    @Test
    void highZoomDampsSmallTremorMoreButStillReleasesOnFastTurn() {
        AdaptiveDirectionFilter low = new AdaptiveDirectionFilter(), high = new AdaptiveDirectionFilter();
        double lowStrength = VrMagnificationStabilization.effectiveStrength(0.55, 1, true);
        double highStrength = VrMagnificationStabilization.effectiveStrength(0.55, 25, true);
        double lowEnergy = 0, highEnergy = 0;
        for (int i = 0; i < 180; i++) {
            double angle = Math.toRadians((i % 2 == 0 ? 1 : -1) * 0.1);
            Vec3 raw = new Vec3(Math.sin(angle), 0, Math.cos(angle));
            Vec3 a = low.update("low", raw, lowStrength, 120, 1L + i * 11_111_111L);
            Vec3 b = high.update("high", raw, highStrength, 120, 1L + i * 11_111_111L);
            if (i > 90) { lowEnergy += a.x * a.x; highEnergy += b.x * b.x; }
        }
        assertTrue(highEnergy < lowEnergy);
        Vec3 fastTurn = new Vec3(Math.sin(Math.toRadians(10)), 0, Math.cos(Math.toRadians(10)));
        assertTrue(high.update("high", fastTurn, highStrength, 120, 1L + 180 * 11_111_111L)
                .distanceTo(fastTurn) < 1.0E-9);
    }

    @Test
    void zoomProfileChangeCannotReadPreviousStrengthShotSnapshot() {
        VrAimStabilizationState state = new VrAimStabilizationState();
        Double oldProfile = VrMagnificationStabilization.effectiveStrength(0.55, 4.25, true);
        Double newProfile = VrMagnificationStabilization.effectiveStrength(0.55, 25, true);
        VrShotPose raw = new VrShotPose(Vec3.ZERO, new Vec3(0, 0, 1));
        state.updateRenderPose(oldProfile, 10, raw, oldProfile, 0.015, 120, 0.8, 1);
        VrShotPose nextShot = new VrShotPose(new Vec3(0.01, 0, 0), new Vec3(0, 0, 1));
        assertSame(nextShot, state.resolveShotPose(newProfile, nextShot, 2, 250_000_000L));
    }
}
