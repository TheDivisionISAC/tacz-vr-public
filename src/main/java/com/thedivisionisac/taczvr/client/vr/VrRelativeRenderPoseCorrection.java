package com.thedivisionisac.taczvr.client.vr;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Applies one direct HMD-relative pose delta to Vivecraft's already view-relative controller matrix. Keeping the
 * correction in the matrix's native space avoids a second world-to-camera conversion and preserves stereo eye offset.
 */
final class VrRelativeRenderPoseCorrection {
    private VrRelativeRenderPoseCorrection() {
    }

    static void apply(Matrix4f controllerPose, Matrix3f controllerNormal,
                      VrRelativeRenderPose rawPose, VrRelativeRenderPose stabilizedPose) {
        Quaternionf correction = stabilizedPose.rotation().toQuaternion()
                .mul(rawPose.rotation().toQuaternion().conjugate())
                .normalize();
        Vector3f translationDelta = toVector(stabilizedPose.position().subtract(rawPose.position()));
        if (!correction.isFinite() || !translationDelta.isFinite()) {
            return;
        }

        Vector3f translation = controllerPose.getTranslation(new Vector3f()).add(translationDelta);
        Matrix4f basis = new Matrix4f(controllerPose).setTranslation(0.0F, 0.0F, 0.0F);
        controllerPose.set(new Matrix4f().rotation(correction).mul(basis)).setTranslation(translation);
        controllerNormal.set(new Matrix3f().rotation(correction).mul(controllerNormal));
    }

    private static Vector3f toVector(net.minecraft.world.phys.Vec3 vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }
}
