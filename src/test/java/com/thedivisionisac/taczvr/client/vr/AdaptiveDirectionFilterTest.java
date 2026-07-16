package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies weak hip filtering, stronger ADS filtering, fast-turn release, lag bounds, and source resets. */
class AdaptiveDirectionFilterTest {
    private static final long FRAME_NANOS = 11_111_111L;
    private static final double TOLERANCE = 1.0E-6D;

    @Test
    void zeroStrengthTracksRawDirectionExactly() {
        AdaptiveDirectionFilter filter = new AdaptiveDirectionFilter();
        filter.update("gun", direction(0.0D), 0.0D, 120.0D, 1L);

        Vec3 output = filter.update("gun", direction(12.0D), 0.0D, 120.0D, 1L + FRAME_NANOS);

        assertEquals(0.0D, angleDegrees(output, direction(12.0D)), TOLERANCE);
    }

    @Test
    void adsStrengthDampensMicroJitterMoreThanHipStrength() {
        AdaptiveDirectionFilter hip = new AdaptiveDirectionFilter();
        AdaptiveDirectionFilter ads = new AdaptiveDirectionFilter();
        long time = 1L;
        hip.update("gun", direction(0.0D), 0.55D, 120.0D, time);
        ads.update("gun", direction(0.0D), 0.96D, 120.0D, time);

        double hipMotion = 0.0D;
        double adsMotion = 0.0D;
        Vec3 previousHip = direction(0.0D);
        Vec3 previousAds = direction(0.0D);
        for (int frame = 0; frame < 80; frame++) {
            time += FRAME_NANOS;
            Vec3 raw = direction((frame & 1) == 0 ? 0.75D : -0.75D);
            Vec3 currentHip = hip.update("gun", raw, 0.55D, 120.0D, time);
            Vec3 currentAds = ads.update("gun", raw, 0.96D, 120.0D, time);
            hipMotion += angleDegrees(previousHip, currentHip);
            adsMotion += angleDegrees(previousAds, currentAds);
            previousHip = currentHip;
            previousAds = currentAds;
        }

        assertTrue(adsMotion < hipMotion * 0.8D, "ADS should suppress more alternating jitter");
    }

    @Test
    void rapidIntentionalTurnFullyReleasesFilter() {
        AdaptiveDirectionFilter filter = new AdaptiveDirectionFilter();
        filter.update("gun", direction(0.0D), 0.72D, 120.0D, 1L);

        Vec3 output = filter.update("gun", direction(20.0D), 0.72D, 120.0D, 1L + FRAME_NANOS);

        assertEquals(0.0D, angleDegrees(output, direction(20.0D)), TOLERANCE);
    }

    @Test
    void trackingJumpAndGunChangeResetImmediately() {
        AdaptiveDirectionFilter filter = new AdaptiveDirectionFilter();
        long time = 1L;
        filter.update("first", direction(0.0D), 0.72D, 120.0D, time);
        time += FRAME_NANOS;
        filter.update("first", direction(1.0D), 0.72D, 120.0D, time);

        time += FRAME_NANOS;
        Vec3 jump = filter.update("first", direction(80.0D), 0.72D, 120.0D, time);
        time += FRAME_NANOS;
        Vec3 changedGun = filter.update("second", direction(-15.0D), 0.72D, 120.0D, time);

        assertEquals(0.0D, angleDegrees(jump, direction(80.0D)), TOLERANCE);
        assertEquals(0.0D, angleDegrees(changedGun, direction(-15.0D)), TOLERANCE);
    }

    @Test
    void slowTrackingNeverExceedsConfiguredInternalLagBound() {
        AdaptiveDirectionFilter filter = new AdaptiveDirectionFilter();
        long time = 1L;
        filter.update("gun", direction(0.0D), 1.0D, 120.0D, time);

        double maximumObservedLag = 0.0D;
        for (int frame = 1; frame <= 240; frame++) {
            time += FRAME_NANOS;
            Vec3 raw = direction(frame * 0.10D);
            Vec3 output = filter.update("gun", raw, 1.0D, 120.0D, time);
            maximumObservedLag = Math.max(maximumObservedLag, angleDegrees(raw, output));
        }

        assertTrue(maximumObservedLag <= 4.76D, "bounded lag must stay close to the tracked hand");
    }

    private static Vec3 direction(double yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        return new Vec3(Math.sin(radians), 0.0D, Math.cos(radians));
    }

    private static double angleDegrees(Vec3 first, Vec3 second) {
        return Math.toDegrees(Math.acos(Mth.clamp(first.normalize().dot(second.normalize()), -1.0D, 1.0D)));
    }
}
