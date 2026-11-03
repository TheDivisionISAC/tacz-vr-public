package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies unified aim conversion and direct correction in Vivecraft's native HMD-relative matrix space. */
class VrRelativeRenderPoseCorrectionTest {
    @Test
    void stabilizedWorldAimBecomesOneHeadRelativeFullPoseTarget() {
        VrRenderTrackingSnapshot tracking = snapshot(25.0D, 0.4D);
        Vec3 stabilizedPosition = tracking.handPose().position().add(0.004D, -0.002D, 0.001D);
        Quaternionf directionTurn = new Quaternionf().rotateY((float) Math.toRadians(0.5D));
        Vector3f stabilizedDirection = directionTurn.transform(toVector(tracking.handPose().direction())).normalize();
        VrShotPose stabilizedWorldPose = new VrShotPose(
                stabilizedPosition,
                new Vec3(stabilizedDirection.x, stabilizedDirection.y, stabilizedDirection.z)
        );

        VrRelativeRenderPose target = VrRelativeRenderPose.fromStabilizedAim(tracking, stabilizedWorldPose);

        Quaternionf inverseHead = tracking.headRotation().toQuaternion().conjugate();
        Vector3f expectedDirection = inverseHead.transform(stabilizedDirection).normalize();
        Vector3f rawRelativeDirection = inverseHead
                .transform(toVector(tracking.handPose().direction()))
                .normalize();
        Quaternionf rawRelativeRotation = VrRelativeRenderPose.from(tracking).rotation().toQuaternion();
        Vector3f localForward = new Quaternionf(rawRelativeRotation).conjugate()
                .transform(rawRelativeDirection)
                .normalize();
        Vector3f actualDirection = target.rotation().toQuaternion().transform(localForward).normalize();

        assertTrue(expectedDirection.dot(actualDirection) > 0.999999F);
        Vector3f expectedPosition = inverseHead.transform(toVector(
                stabilizedPosition.subtract(tracking.headPose().position())
        ));
        assertEquals(expectedPosition.x, target.position().x, 1.0E-6D);
        assertEquals(expectedPosition.y, target.position().y, 1.0E-6D);
        assertEquals(expectedPosition.z, target.position().z, 1.0E-6D);
    }

    @Test
    void directCorrectionMovesTowardFilteredPoseInsteadOfDoublingRawError() {
        VrRelativeRenderPose raw = new VrRelativeRenderPose(
                new Vec3(0.4D, -0.25D, 0.2D),
                rotation(0.4D)
        );
        VrRelativeRenderPose stabilized = new VrRelativeRenderPose(
                new Vec3(0.399D, -0.249D, 0.198D),
                rotation(0.1D)
        );
        Vector3f eyeOffset = new Vector3f(-0.032F, 0.0F, 0.0F);
        Matrix4f controllerPose = new Matrix4f()
                .rotation(raw.rotation().toQuaternion())
                .setTranslation(toVector(raw.position()).add(eyeOffset));
        Matrix3f controllerNormal = new Matrix3f().rotation(raw.rotation().toQuaternion());

        VrRelativeRenderPoseCorrection.apply(controllerPose, controllerNormal, raw, stabilized);

        Quaternionf actualRotation = controllerPose.getNormalizedRotation(new Quaternionf()).normalize();
        assertTrue(Math.abs(actualRotation.dot(stabilized.rotation().toQuaternion())) > 0.999999F);
        Vector3f expectedTranslation = toVector(stabilized.position()).add(eyeOffset);
        Vector3f actualTranslation = controllerPose.getTranslation(new Vector3f());
        assertEquals(expectedTranslation.x, actualTranslation.x, 1.0E-6F);
        assertEquals(expectedTranslation.y, actualTranslation.y, 1.0E-6F);
        assertEquals(expectedTranslation.z, actualTranslation.z, 1.0E-6F);
    }

    private static VrRenderTrackingSnapshot snapshot(double headYawDegrees, double relativeRollDegrees) {
        Quaternionf headRotation = new Quaternionf().rotateY((float) Math.toRadians(headYawDegrees));
        Quaternionf relativeRotation = new Quaternionf().rotateZ((float) Math.toRadians(relativeRollDegrees));
        Quaternionf handRotation = new Quaternionf(headRotation).mul(relativeRotation);
        Vec3 headPosition = new Vec3(3.0D, 1.6D, -2.0D);
        Vector3f handOffset = headRotation.transform(new Vector3f(0.4F, -0.25F, 0.2F));
        Vec3 handPosition = headPosition.add(handOffset.x, handOffset.y, handOffset.z);
        Vector3f handDirection = handRotation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        Vector3f headDirection = headRotation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        return VrRenderTrackingSnapshot.validated(
                handPosition,
                new Vec3(handDirection.x, handDirection.y, handDirection.z),
                handRotation,
                headPosition,
                new Vec3(headDirection.x, headDirection.y, headDirection.z),
                headRotation
        ).orElseThrow();
    }

    private static VrRotation rotation(double rollDegrees) {
        return VrRotation.validated(new Quaternionf().rotateZ((float) Math.toRadians(rollDegrees))).orElseThrow();
    }

    private static Vector3f toVector(Vec3 vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }
}
