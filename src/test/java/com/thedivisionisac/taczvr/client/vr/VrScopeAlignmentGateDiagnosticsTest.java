package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the C2B diagnostic owner remains opt-in, bounded-rate, source-aware, and resettable while exercising
 * the provisional C1 observation profile without exposing it as production tuning.
 */
class VrScopeAlignmentGateDiagnosticsTest {
    private static final long FRAME_NANOS = 10_000_000L;
    private static final long INTERVAL_NANOS = 100_000_000L;
    private static final double TOLERANCE = 1.0E-9D;

    @Test
    void disabledDiagnosticsDoNotAdvanceOrEmit() {
        List<String> messages = new ArrayList<>();
        VrScopeAlignmentGateDiagnostics diagnostics = diagnostics(false, messages);

        diagnostics.recordEyeCandidate(aligned(1L), "LEFT", 1L);
        diagnostics.record(aligned(1L), "LEFT", 1L);
        diagnostics.record(aligned(2L), "RIGHT", 1L + FRAME_NANOS);
        diagnostics.recordAppliedAperture(
                2L,
                ResourceLocation.tryParse("tacz:test_gun"),
                ResourceLocation.tryParse("tacz:test_scope"),
                0,
                "RIGHT",
                0.5F,
                0.8F,
                0.4F,
                1L + FRAME_NANOS
        );

        assertTrue(messages.isEmpty());
        assertEquals(0.0D, diagnostics.progress(), TOLERANCE);
        assertFalse(diagnostics.isLatched());
    }

    @Test
    void eyeCandidatesAreRateLimitedPerRenderEyeWithoutAdvancingSharedState() {
        List<String> messages = new ArrayList<>();
        VrScopeAlignmentGateDiagnostics diagnostics = diagnostics(true, messages);
        long time = 1L;

        diagnostics.recordEyeCandidate(aligned(1L), "LEFT", time);
        diagnostics.recordEyeCandidate(aligned(1L), "RIGHT", time);
        diagnostics.recordEyeCandidate(aligned(2L), "LEFT", time + FRAME_NANOS);
        diagnostics.recordEyeCandidate(aligned(2L), "RIGHT", time + FRAME_NANOS);

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("TACZVR_SCOPE_EYE_GATE_DIAG frame=1 pass=LEFT"));
        assertTrue(messages.get(1).contains("TACZVR_SCOPE_EYE_GATE_DIAG frame=1 pass=RIGHT"));
        assertTrue(messages.get(0).contains("eye_score="));
        assertEquals(0.0D, diagnostics.progress(), TOLERANCE);
        assertFalse(diagnostics.isLatched());
    }

    @Test
    void authorEyeBoxSamplesAreRateLimitedAndDoNotAdvanceState() {
        List<String> messages = new ArrayList<>();
        VrScopeAlignmentGateDiagnostics diagnostics = diagnostics(true, messages);
        long time = 1L;

        diagnostics.recordAuthorEyeBox(authorEyeBox(1L), "LEFT", time);
        diagnostics.recordAuthorEyeBox(authorEyeBox(2L), "LEFT", time + FRAME_NANOS);
        diagnostics.recordAuthorEyeBox(authorEyeBox(1L), "RIGHT", time);

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("TACZVR_SCOPE_AUTHOR_EYEBOX_DIAG frame=1 pass=LEFT"));
        assertTrue(messages.get(0).contains("author_cone_half_deg="));
        assertTrue(messages.get(0).contains("pupil_margin="));
        assertTrue(messages.get(0).contains("scope_view_distance_ratio="));
        assertTrue(messages.get(0).contains("scope_view_axial_ratio="));
        assertTrue(messages.get(0).contains("scope_view_lateral_ratio="));
        assertTrue(messages.get(0).contains("pupil_progress="));
        assertTrue(messages.get(0).contains("scope_view_axial_progress="));
        assertTrue(messages.get(0).contains("scope_view_lateral_progress="));
        assertTrue(messages.get(0).contains("scope_view_progress="));
        assertTrue(messages.get(0).contains("combined_progress="));
        assertTrue(messages.get(0).contains("acquisition_pupil_progress="));
        assertTrue(messages.get(0).contains("acquisition_axial_progress="));
        assertTrue(messages.get(0).contains("acquisition_lateral_progress="));
        assertTrue(messages.get(0).contains("acquisition_geometry_progress="));
        assertTrue(messages.get(1).contains("pass=RIGHT"));
        assertEquals(0.0D, diagnostics.progress(), TOLERANCE);
        assertFalse(diagnostics.isLatched());
    }

    @Test
    void appliedApertureIsReportedPerEyeAtBoundedRateAndResettable() {
        List<String> messages = new ArrayList<>();
        VrScopeAlignmentGateDiagnostics diagnostics = diagnostics(true, messages);
        ResourceLocation gunId = ResourceLocation.tryParse("tacz:test_gun");
        ResourceLocation scopeId = ResourceLocation.tryParse("tacz:test_scope");
        long time = 1L;

        diagnostics.recordAppliedAperture(1L, gunId, scopeId, 0, "LEFT", 0.5F, 0.8F, 0.4F, time);
        diagnostics.recordAppliedAperture(
                2L,
                gunId,
                scopeId,
                0,
                "LEFT",
                0.75F,
                0.8F,
                0.6F,
                time + FRAME_NANOS
        );
        diagnostics.recordAppliedAperture(1L, gunId, scopeId, 0, "RIGHT", 0.5F, 0.8F, 0.4F, time);

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("TACZVR_SCOPE_APPLIED_APERTURE_DIAG frame=1 pass=LEFT"));
        assertTrue(messages.get(0).contains("physical_progress=0.500000"));
        assertTrue(messages.get(0).contains("author_radius_modifier=0.800000"));
        assertTrue(messages.get(0).contains("applied_radius_modifier=0.400000"));
        assertTrue(messages.get(1).contains("pass=RIGHT"));
        assertEquals(0.0D, diagnostics.progress(), TOLERANCE);
        assertFalse(diagnostics.isLatched());

        diagnostics.reset();
        diagnostics.recordAppliedAperture(
                3L,
                gunId,
                scopeId,
                0,
                "LEFT",
                0.75F,
                0.8F,
                0.6F,
                time + 2L * FRAME_NANOS
        );

        assertEquals(3, messages.size());
        assertTrue(messages.get(2).contains("frame=3 pass=LEFT"));
    }

    @Test
    void divisionProjectionReportsFinalTaczCircleFacts() {
        List<String> messages = new ArrayList<>();
        VrScopeAlignmentGateDiagnostics diagnostics = diagnostics(true, messages);
        VrScopeDivisionProjectionSample sample = VrScopeDivisionProjectionSampleTest.measuredWithGunOffset(0.0D);

        diagnostics.recordDivisionProjection(sample, "RIGHT", 1L);

        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("current_safe_tacz_radius_modifier="));
        assertTrue(messages.get(0).contains("current_tacz_center_x="));
        assertTrue(messages.get(0).contains("current_tacz_center_y="));
    }

    @Test
    void firstFrameIsClosedAndLatchTransitionEmitsImmediately() {
        List<String> messages = new ArrayList<>();
        VrScopeAlignmentGateDiagnostics diagnostics = diagnostics(true, messages);
        long time = 1L;

        diagnostics.record(aligned(1L), "LEFT", time);
        diagnostics.record(aligned(2L), "RIGHT", time + FRAME_NANOS);

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("progress=0.000000 latched=false"));
        assertTrue(messages.get(1).contains("latched=true"));
        assertTrue(diagnostics.progress() > 0.0D);
    }

    @Test
    void unchangedSourceIsRateLimitedButIntervalExpiryEmits() {
        List<String> messages = new ArrayList<>();
        VrScopeAlignmentGateDiagnostics diagnostics = diagnostics(true, messages);
        long time = 1L;

        diagnostics.record(aligned(1L), "LEFT", time);
        diagnostics.record(aligned(2L), "RIGHT", time + FRAME_NANOS);
        diagnostics.record(aligned(3L), "LEFT", time + 2L * FRAME_NANOS);
        assertEquals(2, messages.size());

        diagnostics.record(aligned(4L), "LEFT", time + INTERVAL_NANOS + FRAME_NANOS);
        assertEquals(3, messages.size());
    }

    @Test
    void sourceChangeReseedsClosedAndEmitsWithoutWaiting() {
        List<String> messages = new ArrayList<>();
        VrScopeAlignmentGateDiagnostics diagnostics = diagnostics(true, messages);
        long time = 1L;
        diagnostics.record(aligned(1L), "LEFT", time);
        diagnostics.record(aligned(2L), "RIGHT", time + FRAME_NANOS);
        assertTrue(diagnostics.isLatched());

        diagnostics.record(withScope(aligned(3L), "tacz:other_scope"), "LEFT", time + 2L * FRAME_NANOS);

        assertEquals(3, messages.size());
        assertTrue(messages.get(2).contains("scope=tacz:other_scope"));
        assertTrue(messages.get(2).contains("progress=0.000000 latched=false"));
    }

    @Test
    void resetClearsProgressAndAllowsImmediateReseedLog() {
        List<String> messages = new ArrayList<>();
        VrScopeAlignmentGateDiagnostics diagnostics = diagnostics(true, messages);
        long time = 1L;
        diagnostics.record(aligned(1L), "LEFT", time);
        diagnostics.record(aligned(2L), "RIGHT", time + FRAME_NANOS);
        assertTrue(diagnostics.progress() > 0.0D);

        diagnostics.reset();
        diagnostics.record(aligned(3L), "LEFT", time + 2L * FRAME_NANOS);

        assertEquals(3, messages.size());
        assertEquals(0.0D, diagnostics.progress(), TOLERANCE);
        assertFalse(diagnostics.isLatched());
        assertTrue(messages.get(2).contains("progress=0.000000 latched=false"));
    }

    @Test
    void provisionalProfileSeparatesObservedAlignmentFromObliquePose() {
        VrScopeAlignmentGateProfile profile = VrScopeAlignmentGateDiagnostics.provisionalProfile();

        assertTrue(profile.score(aligned(1L)) >= profile.enterScore());
        assertEquals(0.0D, profile.score(
                VrScopeAlignmentGateProfileTest.sample(2L, 0.10D, 0.26D, 70.0D, 40.0D, 80.0D)
        ), TOLERANCE);
    }

    private static VrScopeAlignmentGateDiagnostics diagnostics(boolean enabled, List<String> messages) {
        return new VrScopeAlignmentGateDiagnostics(
                enabled,
                INTERVAL_NANOS,
                VrScopeAlignmentGateDiagnostics.provisionalProfile(),
                messages::add
        );
    }

    private static VrScopeAlignmentSample aligned(long frameIndex) {
        return VrScopeAlignmentGateProfileTest.sample(frameIndex, 0.11D, 0.035D, 20.0D, 5.0D, 20.0D);
    }

    private static VrScopeAlignmentSample withScope(VrScopeAlignmentSample sample, String scopeId) {
        return new VrScopeAlignmentSample(
                sample.frameIndex(),
                sample.gunId(),
                ResourceLocation.tryParse(scopeId),
                sample.viewIndex(),
                sample.lensPosition(),
                sample.opticalAxis(),
                sample.lensDistance(),
                sample.viewForwardDistance(),
                sample.viewLateralOffset(),
                sample.opticalAxialDistance(),
                sample.opticalLateralOffset(),
                sample.viewCenterAngleDegrees(),
                sample.opticalAxisAngleDegrees(),
                sample.coaxialAngleDegrees()
        );
    }

    private static VrScopeAuthorEyeBoxSample authorEyeBox(long frameIndex) {
        return VrScopeAuthorEyeBoxSample.measured(
                frameIndex,
                ResourceLocation.tryParse("tacz:test_gun"),
                ResourceLocation.tryParse("tacz:test_scope"),
                0,
                new VrScopeModelAperture.Transformed(
                        new net.minecraft.world.phys.Vec3(0.0D, 0.0D, -0.20D),
                        new net.minecraft.world.phys.Vec3(1.0D, 0.0D, 0.0D),
                        new net.minecraft.world.phys.Vec3(0.0D, 1.0D, 0.0D),
                        new net.minecraft.world.phys.Vec3(0.0D, 0.0D, -1.0D),
                        0.04D,
                        0.04D
                ),
                net.minecraft.world.phys.Vec3.ZERO,
                new net.minecraft.world.phys.Vec3(0.0D, 0.0D, -1.0D)
        ).orElseThrow();
    }
}
