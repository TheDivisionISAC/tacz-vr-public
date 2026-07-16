package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Main-thread snapshot of the controller facts required by the current shooting protocol.
 */
public record VrShotPose(Vec3 position, Vec3 direction) {
    private static final double MIN_DIRECTION_LENGTH_SQUARED = 1.0E-8;

    /**
     * Builds a normalized pose only from finite tracking data. A transient invalid OpenVR frame must degrade to the
     * ordinary TACZ path instead of entering the stabilization filters or a render matrix.
     */
    public static Optional<VrShotPose> validated(Vec3 position, Vec3 direction) {
        if (!isFinite(position) || !isFinite(direction)
                || direction.lengthSqr() < MIN_DIRECTION_LENGTH_SQUARED) {
            return Optional.empty();
        }
        return Optional.of(new VrShotPose(position, direction.normalize()));
    }

    private static boolean isFinite(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }
}
