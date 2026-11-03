package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that opt-in scope telemetry stays silent by default and remains bounded without hiding source changes.
 */
class VrScopeAlignmentDiagnosticsTest {
    private static final ResourceLocation GUN_ID = ResourceLocation.tryParse("tacz:test_gun");
    private static final ResourceLocation FIRST_SCOPE_ID = ResourceLocation.tryParse("tacz:first_scope");
    private static final ResourceLocation SECOND_SCOPE_ID = ResourceLocation.tryParse("tacz:second_scope");

    @Test
    void disabledDiagnosticsProduceNoOutput() {
        List<String> output = new ArrayList<>();
        VrScopeAlignmentDiagnostics diagnostics = new VrScopeAlignmentDiagnostics(false, 1L, output::add);
        VrScopeAlignmentSample sample = sample(1L, FIRST_SCOPE_ID);

        diagnostics.record(sample, "LEFT", 1L);
        diagnostics.recordStereoComparison(sample, sample, "RIGHT", 1L);

        assertTrue(output.isEmpty());
    }

    @Test
    void rateLimitsOneSourceButEmitsIntervalsAndSourceChanges() {
        List<String> output = new ArrayList<>();
        VrScopeAlignmentDiagnostics diagnostics = new VrScopeAlignmentDiagnostics(true, 100L, output::add);

        diagnostics.record(sample(1L, FIRST_SCOPE_ID), "LEFT", 1L);
        diagnostics.record(sample(2L, FIRST_SCOPE_ID), "RIGHT", 100L);
        diagnostics.record(sample(3L, FIRST_SCOPE_ID), "LEFT", 101L);
        diagnostics.record(sample(4L, SECOND_SCOPE_ID), "LEFT", 102L);

        assertEquals(3, output.size());
        assertTrue(output.get(0).contains("TACZVR_SCOPE_DIAG frame=1 pass=LEFT"));
        assertTrue(output.get(0).contains("lens_z_m=-0.240000"));
        assertTrue(output.get(0).contains("axis_z=-1.000000"));
        assertTrue(output.get(1).contains("frame=3"));
        assertTrue(output.get(2).contains("scope=tacz:second_scope"));
    }

    @Test
    void stereoComparisonIsBoundedAndReportsEyeCenteredDeltas() {
        List<String> output = new ArrayList<>();
        VrScopeAlignmentDiagnostics diagnostics = new VrScopeAlignmentDiagnostics(true, 100L, output::add);
        VrScopeAlignmentSample reference = sample(1L, FIRST_SCOPE_ID);
        VrScopeAlignmentSample candidate = VrScopeAlignmentSample.measured(
                1L,
                GUN_ID,
                FIRST_SCOPE_ID,
                0,
                new Vec3(0.001D, -0.012D, -0.240D),
                new Vec3(0.001D, 0.0D, -1.0D)
        ).orElseThrow();

        diagnostics.record(reference, "LEFT", 1L);
        diagnostics.recordStereoComparison(reference, candidate, "CENTER", 1L);
        diagnostics.recordStereoComparison(reference, candidate, "RIGHT", 1L);
        diagnostics.recordStereoComparison(reference, candidate, "RIGHT", 50L);
        diagnostics.recordStereoComparison(reference, candidate, "RIGHT", 101L);

        List<String> stereoOutput = output.stream()
                .filter(line -> line.startsWith("TACZVR_SCOPE_STEREO"))
                .toList();
        assertEquals(2, stereoOutput.size());
        assertTrue(stereoOutput.get(0).contains("reference_pass=LEFT candidate_pass=RIGHT"));
        assertTrue(stereoOutput.get(0).contains("lens_delta_m=0.002236"));
        assertTrue(stereoOutput.get(0).contains("axis_delta_deg="));
    }

    @Test
    void resetAllowsTheCurrentSourceToEmitImmediatelyAgain() {
        List<String> output = new ArrayList<>();
        VrScopeAlignmentDiagnostics diagnostics = new VrScopeAlignmentDiagnostics(true, 1_000L, output::add);

        diagnostics.record(sample(1L, FIRST_SCOPE_ID), "LEFT", 1L);
        diagnostics.reset();
        diagnostics.record(sample(2L, FIRST_SCOPE_ID), "LEFT", 2L);

        assertEquals(2, output.size());
    }

    private static VrScopeAlignmentSample sample(long frameIndex, ResourceLocation scopeId) {
        return VrScopeAlignmentSample.measured(
                frameIndex,
                GUN_ID,
                scopeId,
                0,
                new Vec3(0.0D, -0.01D, -0.24D),
                new Vec3(0.0D, 0.0D, -1.0D)
        ).orElseThrow();
    }
}
