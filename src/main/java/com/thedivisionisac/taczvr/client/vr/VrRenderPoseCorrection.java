package com.thedivisionisac.taczvr.client.vr;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Carries the view-space transform from a raw controller pose to its stabilized gun pose. Applying the same delta to
 * the post-rendered Vivecraft main hand keeps the wrist attached without changing controller tracking or gameplay.
 */
public final class VrRenderPoseCorrection {
    private static final float MINIMUM_DETERMINANT = 1.0E-6F;

    private final Matrix4f poseDelta;
    private final Matrix3f normalDelta;

    private VrRenderPoseCorrection(Matrix4f poseDelta, Matrix3f normalDelta) {
        this.poseDelta = poseDelta;
        this.normalDelta = normalDelta;
    }

    public static VrRenderPoseCorrection between(Matrix4f rawPose, Matrix3f rawNormal,
                                                  Matrix4f stabilizedPose, Matrix3f stabilizedNormal) {
        if (!rawPose.isFinite() || !rawNormal.isFinite()
                || !stabilizedPose.isFinite() || !stabilizedNormal.isFinite()
                || Math.abs(rawPose.determinant()) < MINIMUM_DETERMINANT
                || Math.abs(rawNormal.determinant()) < MINIMUM_DETERMINANT) {
            return new VrRenderPoseCorrection(new Matrix4f(), new Matrix3f());
        }
        Matrix4f poseDelta = new Matrix4f(stabilizedPose).mul(new Matrix4f(rawPose).invert());
        Matrix3f normalDelta = new Matrix3f(stabilizedNormal).mul(new Matrix3f(rawNormal).invert());
        return new VrRenderPoseCorrection(poseDelta, normalDelta);
    }

    public void apply(PoseStack poseStack) {
        PoseStack.Pose pose = poseStack.last();
        pose.pose().set(new Matrix4f(this.poseDelta).mul(pose.pose()));
        pose.normal().set(new Matrix3f(this.normalDelta).mul(pose.normal()));
    }
}
