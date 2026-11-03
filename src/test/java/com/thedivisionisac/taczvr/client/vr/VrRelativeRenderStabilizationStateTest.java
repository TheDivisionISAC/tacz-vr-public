package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies stereo de-duplication and HMD-relative filtering independently from shot stabilization. */
class VrRelativeRenderStabilizationStateTest {
    private static final long FRAME_NANOS = 11_111_111L;

    @Test
    void stereoEyesReuseOneFullPoseCorrectionEvenIfDuplicateTrackingDiffers() {
        VrRelativeRenderStabilizationState state = new VrRelativeRenderStabilizationState();
        VrRenderTrackingSnapshot leftTracking = snapshot(0.0D, Vec3.ZERO, 0.0D);
        VrRenderTrackingSnapshot divergentRightTracking = snapshot(0.0D, Vec3.ZERO, 15.0D);

        VrRelativeRenderPose left = state.update(
                "gun", 10L, VrRelativeRenderPose.from(leftTracking),
                0.96D, 1.0D, 0.008D, 120.0D, 0.8D, 1L
        );
        VrRelativeRenderPose right = state.update(
                "gun", 10L, VrRelativeRenderPose.from(divergentRightTracking), 0.96D, 1.0D, 0.008D,
                120.0D, 0.8D, 1L + 1L
        );

        assertSame(left, right);
    }

    @Test
    void synchronousHeadAndHandMotionLeavesRelativePoseUnchanged() {
        VrRelativeRenderPose first = VrRelativeRenderPose.from(snapshot(0.0D, Vec3.ZERO, 0.0D));
        VrRelativeRenderPose together = VrRelativeRenderPose.from(
                snapshot(25.0D, new Vec3(1.0D, 0.25D, -0.5D), 0.0D)
        );

        assertEquals(first.position().x, together.position().x, 1.0E-6D);
        assertEquals(first.position().y, together.position().y, 1.0E-6D);
        assertEquals(first.position().z, together.position().z, 1.0E-6D);
        assertEquals(0.0D, angleDegrees(first.rotation(), together.rotation()), 1.0E-5D);
    }

    @Test
    void adsFilterDampsSmallRelativeRoll() {
        VrRelativeRenderStabilizationState state = new VrRelativeRenderStabilizationState();
        VrRenderTrackingSnapshot identity = snapshot(0.0D, Vec3.ZERO, 0.0D);
        VrRenderTrackingSnapshot rolled = snapshot(0.0D, Vec3.ZERO, 0.3D);
        state.update(
                "gun", 1L, VrRelativeRenderPose.from(identity),
                0.96D, 1.0D, 0.008D, 120.0D, 0.8D, 1L
        );

        VrRelativeRenderPose filtered = state.update(
                "gun", 2L, VrRelativeRenderPose.from(rolled),
                0.96D, 1.0D, 0.008D, 120.0D, 0.8D, 1L + FRAME_NANOS
        );

        assertTrue(angleDegrees(VrRelativeRenderPose.from(identity).rotation(), filtered.rotation()) < 0.3D);
    }

    private static VrRenderTrackingSnapshot snapshot(double headYawDegrees, Vec3 headPosition,
                                                       double relativeHandRollDegrees) {
        Quaternionf headRotation = new Quaternionf().rotateY((float) Math.toRadians(headYawDegrees));
        Quaternionf relativeHandRotation = new Quaternionf()
                .rotateZ((float) Math.toRadians(relativeHandRollDegrees));
        Quaternionf handRotation = new Quaternionf(headRotation).mul(relativeHandRotation);
        Vector3f relativeHandPosition = new Vector3f(0.4F, -0.25F, 0.2F);
        Vector3f worldHandOffset = new Quaternionf(headRotation).transform(relativeHandPosition);
        Vec3 handPosition = headPosition.add(worldHandOffset.x, worldHandOffset.y, worldHandOffset.z);
        Vector3f handDirection = new Quaternionf(handRotation).transform(new Vector3f(0.0F, 0.0F, 1.0F));
        Vector3f headDirection = new Quaternionf(headRotation).transform(new Vector3f(0.0F, 0.0F, 1.0F));
        return VrRenderTrackingSnapshot.validated(
                handPosition,
                new Vec3(handDirection.x, handDirection.y, handDirection.z),
                handRotation,
                headPosition,
                new Vec3(headDirection.x, headDirection.y, headDirection.z),
                headRotation
        ).orElseThrow();
    }

    private static double angleDegrees(VrRotation first, VrRotation second) {
        double dot = Math.abs(
                first.x() * second.x()
                        + first.y() * second.y()
                        + first.z() * second.z()
                        + first.w() * second.w()
        );
        return Math.toDegrees(2.0D * Math.acos(Math.max(0.0D, Math.min(1.0D, dot))));
    }
}
