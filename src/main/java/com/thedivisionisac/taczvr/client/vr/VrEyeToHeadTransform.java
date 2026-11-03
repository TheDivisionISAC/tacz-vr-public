package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.Optional;

/**
 * Immutable rigid transform from the active Vivecraft eye-view basis into the HMD-center basis.
 */
public final class VrEyeToHeadTransform {
    private static final double MINIMUM_QUATERNION_LENGTH_SQUARED = 1.0E-12D;

    private final Vec3 eyePositionFromHead;
    private final double rotationX;
    private final double rotationY;
    private final double rotationZ;
    private final double rotationW;

    private VrEyeToHeadTransform(Vec3 eyePositionFromHead, double rotationX, double rotationY,
                                 double rotationZ, double rotationW) {
        this.eyePositionFromHead = eyePositionFromHead;
        this.rotationX = rotationX;
        this.rotationY = rotationY;
        this.rotationZ = rotationZ;
        this.rotationW = rotationW;
    }

    public static Optional<VrEyeToHeadTransform> validated(Vec3 eyePositionFromHead,
                                                            Quaternionfc eyeRotationFromHead) {
        if (eyePositionFromHead == null || eyeRotationFromHead == null
                || !isFinite(eyePositionFromHead)) {
            return Optional.empty();
        }
        double x = eyeRotationFromHead.x();
        double y = eyeRotationFromHead.y();
        double z = eyeRotationFromHead.z();
        double w = eyeRotationFromHead.w();
        double lengthSquared = x * x + y * y + z * z + w * w;
        if (!Double.isFinite(lengthSquared) || lengthSquared < MINIMUM_QUATERNION_LENGTH_SQUARED) {
            return Optional.empty();
        }
        double inverseLength = 1.0D / Math.sqrt(lengthSquared);
        return Optional.of(new VrEyeToHeadTransform(
                eyePositionFromHead,
                x * inverseLength,
                y * inverseLength,
                z * inverseLength,
                w * inverseLength
        ));
    }

    public Vec3 transformPosition(Vec3 eyeLocalPosition) {
        return this.rotate(eyeLocalPosition).add(this.eyePositionFromHead);
    }

    public Vec3 transformDirection(Vec3 eyeLocalDirection) {
        return this.rotate(eyeLocalDirection);
    }

    /**
     * Converts one eye-view-local orientation into the HMD-center basis. The eye-to-head rotation is applied on the
     * left because the supplied local orientation maps model coordinates into the current eye-view basis.
     */
    public Optional<VrRotation> transformRotation(Quaternionfc eyeLocalRotation) {
        if (eyeLocalRotation == null) {
            return Optional.empty();
        }
        Quaternionf eyeToHead = new Quaternionf(
                (float) this.rotationX,
                (float) this.rotationY,
                (float) this.rotationZ,
                (float) this.rotationW
        );
        return VrRotation.validated(eyeToHead.mul(new Quaternionf(eyeLocalRotation)));
    }

    private Vec3 rotate(Vec3 vector) {
        double crossX = this.rotationY * vector.z - this.rotationZ * vector.y;
        double crossY = this.rotationZ * vector.x - this.rotationX * vector.z;
        double crossZ = this.rotationX * vector.y - this.rotationY * vector.x;
        double secondCrossX = this.rotationY * crossZ - this.rotationZ * crossY;
        double secondCrossY = this.rotationZ * crossX - this.rotationX * crossZ;
        double secondCrossZ = this.rotationX * crossY - this.rotationY * crossX;
        return new Vec3(
                vector.x + 2.0D * (this.rotationW * crossX + secondCrossX),
                vector.y + 2.0D * (this.rotationW * crossY + secondCrossY),
                vector.z + 2.0D * (this.rotationW * crossZ + secondCrossZ)
        );
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
