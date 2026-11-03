package com.thedivisionisac.taczvr.client.vr;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.Optional;

/**
 * Immutable normalized rotation copied from Vivecraft tracking. Keeping primitive components here prevents mutable
 * JOML objects and optional Vivecraft API types from escaping the compatibility bridge.
 */
public record VrRotation(double x, double y, double z, double w) {
    private static final double MINIMUM_LENGTH_SQUARED = 1.0E-12D;

    public static Optional<VrRotation> validated(Quaternionfc rotation) {
        if (rotation == null) {
            return Optional.empty();
        }
        return validated(rotation.x(), rotation.y(), rotation.z(), rotation.w());
    }

    public static Optional<VrRotation> validated(double x, double y, double z, double w) {
        double lengthSquared = x * x + y * y + z * z + w * w;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(w)
                || !Double.isFinite(lengthSquared) || lengthSquared < MINIMUM_LENGTH_SQUARED) {
            return Optional.empty();
        }
        double inverseLength = 1.0D / Math.sqrt(lengthSquared);
        return Optional.of(new VrRotation(
                x * inverseLength,
                y * inverseLength,
                z * inverseLength,
                w * inverseLength
        ));
    }

    public Quaternionf toQuaternion() {
        return new Quaternionf((float) this.x, (float) this.y, (float) this.z, (float) this.w).normalize();
    }
}
