package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Immutable controller pose expressed relative to the current HMD. Filtering this small render-only pose targets
 * visible hand-to-head micro-motion without turning the HMD pose itself into a gameplay or shooting input.
 */
record VrRelativeRenderPose(Vec3 position, VrRotation rotation) {
    static VrRelativeRenderPose from(VrRenderTrackingSnapshot tracking) {
        Quaternionf inverseHead = tracking.headRotation().toQuaternion().conjugate();
        Vector3f relativePosition = inverseHead.transform(toVector(
                tracking.handPose().position().subtract(tracking.headPose().position())
        ));
        Quaternionf relativeRotation = inverseHead.mul(tracking.handRotation().toQuaternion()).normalize();
        return new VrRelativeRenderPose(
                toVec3(relativePosition),
                VrRotation.validated(relativeRotation).orElse(tracking.handRotation())
        );
    }

    static VrRelativeRenderPose fromStabilizedAim(VrRenderTrackingSnapshot tracking, VrShotPose stabilizedWorldPose) {
        VrRelativeRenderPose rawRelativePose = from(tracking);
        Quaternionf inverseHead = tracking.headRotation().toQuaternion().conjugate();
        Vector3f relativePosition = inverseHead.transform(toVector(
                stabilizedWorldPose.position().subtract(tracking.headPose().position())
        ));
        Vector3f rawRelativeDirection = inverseHead.transform(toVector(tracking.handPose().direction())).normalize();
        Vector3f stabilizedRelativeDirection = inverseHead
                .transform(toVector(stabilizedWorldPose.direction()))
                .normalize();
        Quaternionf directionCorrection = new Quaternionf()
                .rotationTo(rawRelativeDirection, stabilizedRelativeDirection);
        Quaternionf relativeRotation = directionCorrection
                .mul(rawRelativePose.rotation().toQuaternion())
                .normalize();
        return new VrRelativeRenderPose(
                toVec3(relativePosition),
                VrRotation.validated(relativeRotation).orElse(rawRelativePose.rotation())
        );
    }

    private static Vector3f toVector(Vec3 vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }

    private static Vec3 toVec3(Vector3f vector) {
        return new Vec3(vector.x, vector.y, vector.z);
    }
}
