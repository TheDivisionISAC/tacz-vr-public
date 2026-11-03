package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrFullPoseDiagnosticsTest {
    private static final ResourceLocation FIRST_GUN = new ResourceLocation("tacz", "first");
    private static final ResourceLocation SECOND_GUN = new ResourceLocation("tacz", "second");

    @Test
    void disabledDiagnosticsProduceNoOutput() {
        List<String> output = new ArrayList<>();
        VrFullPoseDiagnostics diagnostics = new VrFullPoseDiagnostics(false, 1L, output::add);

        diagnostics.record(1L, FIRST_GUN, false, "LEFT", snapshot(0.0D, 0.0D, 0.0D),
                new Matrix4f(), new Matrix4f(), 1L);

        assertTrue(output.isEmpty());
    }

    @Test
    void detectsRollThatDoesNotChangeForwardDirection() {
        List<String> output = new ArrayList<>();
        VrFullPoseDiagnostics diagnostics = new VrFullPoseDiagnostics(
                true, TimeUnit.SECONDS.toNanos(10L), output::add
        );
        VrRenderTrackingSnapshot first = snapshot(0.0D, 0.0D, 0.0D);
        VrRenderTrackingSnapshot rolled = snapshot(1.0D, 0.0D, 0.0D);

        diagnostics.record(1L, FIRST_GUN, true, "LEFT", first,
                matrix(0.0D, -0.032F), matrix(0.0D, -0.032F), 1L);
        diagnostics.record(2L, FIRST_GUN, true, "LEFT", rolled,
                matrix(1.0D, -0.032F), matrix(0.0D, -0.032F), 2L);
        diagnostics.record(3L, SECOND_GUN, false, "LEFT", rolled,
                matrix(1.0D, -0.032F), matrix(1.0D, -0.032F), 3L);

        assertEquals(1, output.size());
        String summary = output.get(0);
        assertEquals(1.0D, value(summary, "hand_rotation_deg_p95"), 1.0E-3D);
        assertEquals(1.0D, value(summary, "hand_twist_deg_p95"), 1.0E-3D);
        assertEquals(1.0D, value(summary, "head_relative_twist_deg_p95"), 1.0E-3D);
        assertEquals(1.0D, value(summary, "raw_view_rotation_deg_p95"), 1.0E-3D);
        assertEquals(0.0D, value(summary, "stabilized_view_rotation_deg_p95"), 1.0E-3D);
    }

    @Test
    void separatesWorldMotionFromHeadRelativeMotion() {
        List<String> output = new ArrayList<>();
        VrFullPoseDiagnostics diagnostics = new VrFullPoseDiagnostics(
                true, TimeUnit.SECONDS.toNanos(10L), output::add
        );
        VrRenderTrackingSnapshot first = snapshot(0.0D, 0.0D, 0.0D);
        VrRenderTrackingSnapshot together = snapshot(0.0D, 2.0D, 2.0D);

        diagnostics.record(1L, FIRST_GUN, false, "LEFT", first,
                new Matrix4f(), new Matrix4f(), 1L);
        diagnostics.record(2L, FIRST_GUN, false, "LEFT", together,
                new Matrix4f(), new Matrix4f(), 2L);
        diagnostics.record(3L, SECOND_GUN, false, "LEFT", together,
                new Matrix4f(), new Matrix4f(), 3L);

        String summary = output.get(0);
        assertEquals(2.0D, value(summary, "hand_rotation_deg_p95"), 1.0E-3D);
        assertEquals(2.0D, value(summary, "head_rotation_deg_p95"), 1.0E-3D);
        assertEquals(0.0D, value(summary, "head_relative_rotation_deg_p95"), 1.0E-3D);
        assertEquals(0.0D, value(summary, "head_relative_position_mm_p95"), 1.0E-3D);
    }

    @Test
    void reportsStereoViewDifferencesWithoutAddingAnotherFrameSample() {
        List<String> output = new ArrayList<>();
        VrFullPoseDiagnostics diagnostics = new VrFullPoseDiagnostics(
                true, TimeUnit.SECONDS.toNanos(10L), output::add
        );
        VrRenderTrackingSnapshot tracking = snapshot(0.0D, 0.0D, 0.0D);

        diagnostics.record(1L, FIRST_GUN, false, "LEFT", tracking,
                matrix(0.0D, -0.032F), matrix(0.0D, -0.032F), 1L);
        diagnostics.record(1L, FIRST_GUN, false, "RIGHT", tracking,
                matrix(0.0D, 0.032F), matrix(0.0D, 0.032F), 2L);
        diagnostics.record(2L, SECOND_GUN, false, "LEFT", tracking,
                new Matrix4f(), new Matrix4f(), 3L);

        String summary = output.get(0);
        assertTrue(summary.contains("primary_pass=LEFT"));
        assertTrue(summary.contains("duplicate_pass=RIGHT"));
        assertTrue(summary.contains("unique_frames=1"));
        assertTrue(summary.contains("duplicate_calls=1"));
        assertEquals(64.0D, value(summary, "duplicate_raw_view_position_mm_max"), 1.0E-3D);
        assertEquals(64.0D, value(summary, "duplicate_stabilized_view_position_mm_max"), 1.0E-3D);
    }

    private static VrRenderTrackingSnapshot snapshot(double handRollDegrees,
                                                       double handAndPositionYawDegrees,
                                                       double headYawDegrees) {
        Quaternionf handRotation = new Quaternionf()
                .rotateY((float) Math.toRadians(handAndPositionYawDegrees))
                .rotateZ((float) Math.toRadians(handRollDegrees));
        Quaternionf headRotation = new Quaternionf().rotateY((float) Math.toRadians(headYawDegrees));
        Vector3f handPosition = handRotation.transform(new Vector3f(0.4F, 0.0F, 0.0F));
        Vector3f handDirection = handRotation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        Vector3f headDirection = headRotation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        return VrRenderTrackingSnapshot.validated(
                new Vec3(handPosition.x, handPosition.y, handPosition.z),
                new Vec3(handDirection.x, handDirection.y, handDirection.z),
                handRotation,
                Vec3.ZERO,
                new Vec3(headDirection.x, headDirection.y, headDirection.z),
                headRotation
        ).orElseThrow();
    }

    private static Matrix4f matrix(double rollDegrees, float x) {
        return new Matrix4f()
                .rotationZ((float) Math.toRadians(rollDegrees))
                .setTranslation(x, 0.0F, 0.0F);
    }

    private static double value(String summary, String key) {
        String prefix = key + "=";
        for (String token : summary.split(" ")) {
            if (token.startsWith(prefix)) {
                return Double.parseDouble(token.substring(prefix.length()));
            }
        }
        throw new AssertionError("Missing diagnostic field " + key + " in: " + summary);
    }
}
