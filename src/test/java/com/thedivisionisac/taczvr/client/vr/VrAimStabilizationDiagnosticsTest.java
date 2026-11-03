package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies opt-in behavior, stereo duplicate accounting, source separation, and interval summaries. */
class VrAimStabilizationDiagnosticsTest {
    private static final ResourceLocation FIRST_GUN = new ResourceLocation("tacz", "first");
    private static final ResourceLocation SECOND_GUN = new ResourceLocation("tacz", "second");
    private static final long FRAME_NANOS = 11_111_111L;

    @Test
    void disabledDiagnosticsProduceNoOutput() {
        List<String> output = new ArrayList<>();
        VrAimStabilizationDiagnostics diagnostics = new VrAimStabilizationDiagnostics(
                false, 1L, output::add
        );

        diagnostics.record(1L, FIRST_GUN, false, pose(0.0D, 0.0D), pose(0.0D, 0.0D), 1L);
        diagnostics.record(2L, FIRST_GUN, false, pose(0.001D, 1.0D), pose(0.0D, 0.1D), 2L);

        assertTrue(output.isEmpty());
    }

    @Test
    void sourceChangeReportsStereoDuplicatesWithoutTreatingThemAsSamples() {
        List<String> output = new ArrayList<>();
        VrAimStabilizationDiagnostics diagnostics = new VrAimStabilizationDiagnostics(
                true, TimeUnit.SECONDS.toNanos(10L), output::add
        );
        VrShotPose first = pose(0.0D, 0.0D);
        VrShotPose second = pose(0.002D, 2.0D);

        diagnostics.record(10L, FIRST_GUN, false, first, first, 1L);
        diagnostics.record(10L, FIRST_GUN, false, first, first, 2L);
        diagnostics.record(10L, FIRST_GUN, false, pose(0.001D, 0.5D), first, 3L);
        diagnostics.record(11L, FIRST_GUN, false, second, pose(0.0002D, 0.2D), 1L + FRAME_NANOS);
        diagnostics.record(12L, SECOND_GUN, true, second, second, 1L + 2L * FRAME_NANOS);

        assertEquals(1, output.size());
        String summary = output.get(0);
        assertTrue(summary.contains("reason=source_change"));
        assertTrue(summary.contains("gun=tacz:first"));
        assertTrue(summary.contains("mode=hip"));
        assertTrue(summary.contains("unique_frames=2"));
        assertTrue(summary.contains("delta_samples=1"));
        assertTrue(summary.contains("duplicate_calls=2"));
        assertTrue(summary.contains("divergent_duplicate_calls=1"));
    }

    @Test
    void elapsedWindowReportsRawAndStabilizedMotionSeparately() {
        List<String> output = new ArrayList<>();
        VrAimStabilizationDiagnostics diagnostics = new VrAimStabilizationDiagnostics(
                true, TimeUnit.SECONDS.toNanos(1L), output::add
        );

        diagnostics.record(20L, FIRST_GUN, true, pose(0.0D, 0.0D), pose(0.0D, 0.0D), 1L);
        diagnostics.record(
                21L,
                FIRST_GUN,
                true,
                pose(0.004D, 4.0D),
                pose(0.0004D, 0.4D),
                1L + FRAME_NANOS
        );
        diagnostics.record(
                22L,
                FIRST_GUN,
                true,
                pose(-0.004D, -4.0D),
                pose(-0.0004D, -0.4D),
                1L + TimeUnit.SECONDS.toNanos(1L)
        );

        assertEquals(1, output.size());
        String summary = output.get(0);
        assertTrue(summary.contains("reason=interval"));
        assertTrue(summary.contains("mode=ads"));
        assertTrue(summary.contains("unique_frames=3"));
        assertTrue(summary.contains("raw_angle_deg_p95=8.0000"));
        assertTrue(summary.contains("stabilized_angle_deg_p95=0.8000"));
        assertTrue(summary.contains("raw_position_mm_p95=8.0000"));
        assertTrue(summary.contains("stabilized_position_mm_p95=0.8000"));
    }

    private static VrShotPose pose(double x, double yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        return new VrShotPose(
                new Vec3(x, 1.5D, 0.0D),
                new Vec3(Math.sin(radians), 0.0D, Math.cos(radians))
        );
    }
}
