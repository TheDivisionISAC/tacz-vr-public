package com.thedivisionisac.taczvr.client.vr;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VrRenderPoseCorrectionTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void movesAControllerRelativeHandWithTheStabilizedGunPose() {
        Matrix4f rawPose = new Matrix4f().translation(1.0F, 2.0F, 3.0F).rotateY(0.25F);
        Matrix3f rawNormal = new Matrix3f().rotationY(0.25F);
        Matrix4f stabilizedPose = new Matrix4f().translation(0.96F, 2.03F, 2.98F).rotateY(0.20F);
        Matrix3f stabilizedNormal = new Matrix3f().rotationY(0.20F);
        Matrix4f localHand = new Matrix4f().translation(0.08F, -0.12F, 0.03F).rotateX(0.15F);
        Matrix3f localHandNormal = new Matrix3f().rotationX(0.15F);

        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(new Matrix4f(rawPose).mul(localHand));
        poseStack.last().normal().set(new Matrix3f(rawNormal).mul(localHandNormal));

        VrRenderPoseCorrection.between(rawPose, rawNormal, stabilizedPose, stabilizedNormal).apply(poseStack);

        assertMatrixEquals(new Matrix4f(stabilizedPose).mul(localHand), poseStack.last().pose());
        assertMatrixEquals(new Matrix3f(stabilizedNormal).mul(localHandNormal), poseStack.last().normal());
    }

    private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                assertEquals(expected.get(column, row), actual.get(column, row), EPSILON);
            }
        }
    }

    private static void assertMatrixEquals(Matrix3f expected, Matrix3f actual) {
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                assertEquals(expected.get(column, row), actual.get(column, row), EPSILON);
            }
        }
    }
}
