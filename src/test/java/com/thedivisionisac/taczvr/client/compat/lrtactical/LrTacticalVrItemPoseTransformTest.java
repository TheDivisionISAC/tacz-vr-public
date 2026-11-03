package com.thedivisionisac.taczvr.client.compat.lrtactical;

import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrTacticalVrItemPoseTransformTest {
    private static final float EPSILON = 0.00001F;

    @Test
    void positionOnlyTransformMapsAnimatedPalmToControllerOrigin() {
        Vector3f palm = new Vector3f(0.35F, -1.125F, 0.2F);
        Matrix4f transform = LrTacticalVrItemPoseTransform
                .createPositionOnlyControllerToModel(palm, 0.6F);

        Vector3f mappedPalm = transform.transformPosition(new Vector3f(palm));

        assertVector(0.0F, 0.0F, 0.0F, mappedPalm);
    }

    @Test
    void positionOnlyTransformKeepsControllerAlignedModelAxes() {
        Matrix4f transform = LrTacticalVrItemPoseTransform
                .createPositionOnlyControllerToModel(new Vector3f(0.2F, -0.8F, 0.1F), 0.6F);

        assertVector(-0.6F, 0.0F, 0.0F, transform.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)));
        assertVector(0.0F, -0.6F, 0.0F, transform.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)));
        assertVector(0.0F, 0.0F, 0.6F, transform.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F)));
    }

    @Test
    void inactiveFrameRefreshesAlignmentFromCurrentPalm() {
        Vector3f current = new Vector3f(0.2F, -0.8F, 0.1F);
        Vector3f stable = new Vector3f(0.4F, -1.0F, 0.3F);

        Vector3f selected = LrTacticalVrItemPoseTransform.chooseAlignmentPalm(
                current, stable, null, false
        );

        assertVector(current.x, current.y, current.z, selected);
        assertNotSame(current, selected);
    }

    @Test
    void activeFrameKeepsStablePalmSoAuthoredMotionSurvivesRootAlignment() {
        Vector3f stable = new Vector3f(0.2F, -0.8F, 0.1F);
        Vector3f animated = new Vector3f(0.45F, -1.1F, -0.05F);
        Vector3f selected = LrTacticalVrItemPoseTransform.chooseAlignmentPalm(
                animated, stable, null, true
        );
        Matrix4f transform = LrTacticalVrItemPoseTransform.createPositionOnlyControllerToModel(selected, 0.6F);

        assertVector(stable.x, stable.y, stable.z, selected);
        assertVector(0.0F, 0.0F, 0.0F, transform.transformPosition(new Vector3f(stable)));
        Vector3f animatedResult = transform.transformPosition(new Vector3f(animated));
        float expectedMotion = new Vector3f(animated).sub(stable).mul(0.6F).length();
        assertEquals(expectedMotion, animatedResult.length(), 0.00001F);
    }

    @Test
    void coldFirstDrawUsesStaticIdlePalmInsteadOfTransientAnimatedPalm() {
        Vector3f animated = new Vector3f(0.45F, -1.1F, -0.05F);
        Vector3f staticIdle = new Vector3f(0.2F, -0.8F, 0.1F);

        Vector3f selected = LrTacticalVrItemPoseTransform.chooseAlignmentPalm(
                animated, null, staticIdle, true
        );

        assertVector(staticIdle.x, staticIdle.y, staticIdle.z, selected);
        assertNotSame(staticIdle, selected);
    }

    @Test
    void coldFrameUsesStaticIdleBeforeDrawTrackIsReportedActive() {
        Vector3f transientDrawPose = new Vector3f(0.45F, -1.1F, -0.05F);
        Vector3f staticIdle = new Vector3f(0.2F, -0.8F, 0.1F);

        Vector3f selected = LrTacticalVrItemPoseTransform.chooseAlignmentPalm(
                transientDrawPose, null, staticIdle, false
        );

        assertVector(staticIdle.x, staticIdle.y, staticIdle.z, selected);
    }

    @Test
    void coldLatchKeepsStaticPalmAcrossMultipleUnreportedDrawFrames() {
        Vector3f transientDrawPose = new Vector3f(0.45F, -1.1F, -0.05F);
        Vector3f cachedStaticIdle = new Vector3f(0.2F, -0.8F, 0.1F);

        Vector3f selected = LrTacticalVrItemPoseTransform.chooseAlignmentPalm(
                transientDrawPose, cachedStaticIdle, null, true
        );

        assertVector(cachedStaticIdle.x, cachedStaticIdle.y, cachedStaticIdle.z, selected);
        assertFalse(LrTacticalVrItemPoseTransform.shouldStoreStablePalm(true, false));
    }

    @Test
    void coldLatchWaitsForManipulationThenReleasesAfterItEnds() {
        LrTacticalVrItemPoseTransform.ColdGripPhase phase =
                LrTacticalVrItemPoseTransform.ColdGripPhase.WAITING_FOR_MANIPULATION;

        phase = LrTacticalVrItemPoseTransform.nextColdGripPhase(phase, false, false);
        assertEquals(LrTacticalVrItemPoseTransform.ColdGripPhase.WAITING_FOR_MANIPULATION, phase);
        phase = LrTacticalVrItemPoseTransform.nextColdGripPhase(phase, true, false);
        assertEquals(LrTacticalVrItemPoseTransform.ColdGripPhase.MANIPULATION_ACTIVE, phase);
        phase = LrTacticalVrItemPoseTransform.nextColdGripPhase(phase, true, false);
        assertEquals(LrTacticalVrItemPoseTransform.ColdGripPhase.MANIPULATION_ACTIVE, phase);
        phase = LrTacticalVrItemPoseTransform.nextColdGripPhase(phase, false, false);
        assertEquals(LrTacticalVrItemPoseTransform.ColdGripPhase.NONE, phase);
    }

    @Test
    void coldLatchTimesOutWhenNoManipulationTrackEverAppears() {
        LrTacticalVrItemPoseTransform.ColdGripPhase phase =
                LrTacticalVrItemPoseTransform.ColdGripPhase.WAITING_FOR_MANIPULATION;

        phase = LrTacticalVrItemPoseTransform.nextColdGripPhase(phase, false, true);

        assertEquals(LrTacticalVrItemPoseTransform.ColdGripPhase.NONE, phase);
    }

    @Test
    void coldTimerStartsOnlyOnFirstEligibleRender() {
        long firstEligibleRenderNs = 5_000_000_000L;

        long startedNs = LrTacticalVrItemPoseTransform.ensureColdGripTimerStarted(
                Long.MIN_VALUE, firstEligibleRenderNs
        );

        assertEquals(firstEligibleRenderNs, startedNs);
        assertEquals(startedNs, LrTacticalVrItemPoseTransform.ensureColdGripTimerStarted(
                startedNs, firstEligibleRenderNs + 2_000_000_000L
        ));
    }

    @Test
    void coldModelIsHiddenOnlyUntilManipulationTrackAppears() {
        assertTrue(LrTacticalVrItemPoseTransform.shouldSuppressColdModel(
                LrTacticalVrItemPoseTransform.ColdGripPhase.WAITING_FOR_MANIPULATION
        ));
        assertFalse(LrTacticalVrItemPoseTransform.shouldSuppressColdModel(
                LrTacticalVrItemPoseTransform.ColdGripPhase.MANIPULATION_ACTIVE
        ));
        assertFalse(LrTacticalVrItemPoseTransform.shouldSuppressColdModel(
                LrTacticalVrItemPoseTransform.ColdGripPhase.NONE
        ));
    }

    @Test
    void heldItemObserverRestartsColdLatchForItemOrSlotChanges() {
        ResourceLocation ibuprofen = new ResourceLocation("lrtactical", "ibuprofen");
        ResourceLocation milk = new ResourceLocation("lrtactical", "condensed_milk");

        assertFalse(LrTacticalVrItemPoseTransform.heldItemSelectionChanged(
                ibuprofen, 2, ibuprofen, 2
        ));
        assertTrue(LrTacticalVrItemPoseTransform.heldItemSelectionChanged(
                ibuprofen, 2, ibuprofen, 3
        ));
        assertTrue(LrTacticalVrItemPoseTransform.heldItemSelectionChanged(
                ibuprofen, 2, milk, 2
        ));
    }

    @Test
    void redrawPrefersCachedIdlePalmOverStaticSeed() {
        Vector3f animated = new Vector3f(0.45F, -1.1F, -0.05F);
        Vector3f cached = new Vector3f(0.2F, -0.8F, 0.1F);
        Vector3f staticIdle = new Vector3f(0.25F, -0.85F, 0.15F);

        Vector3f selected = LrTacticalVrItemPoseTransform.chooseAlignmentPalm(
                animated, cached, staticIdle, true
        );

        assertVector(cached.x, cached.y, cached.z, selected);
    }

    @Test
    void drawOrUseFrameOnlySeedsStablePalmFromStaticIdle() {
        assertFalse(LrTacticalVrItemPoseTransform.shouldRefreshStablePalm(true));
        assertTrue(LrTacticalVrItemPoseTransform.shouldRefreshStablePalm(false));
        assertFalse(LrTacticalVrItemPoseTransform.shouldStoreStablePalm(true, false));
        assertTrue(LrTacticalVrItemPoseTransform.shouldStoreStablePalm(true, true));
        assertTrue(LrTacticalVrItemPoseTransform.shouldStoreStablePalm(false, false));
    }

    private static void assertVector(float x, float y, float z, Vector3f actual) {
        assertEquals(x, actual.x, EPSILON);
        assertEquals(y, actual.y, EPSILON);
        assertEquals(z, actual.z, EPSILON);
    }
}
