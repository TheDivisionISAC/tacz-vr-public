package com.thedivisionisac.taczvr.server.lrtactical;

import net.minecraft.world.phys.Vec3;

/**
 * Redirects only the launch vector while preserving LRTactical's configured launch magnitude.
 */
public final class LrTacticalThrowMotion {
    private static final double MIN_LENGTH_SQUARED = 1.0E-12D;

    private LrTacticalThrowMotion() {
    }

    public static Vec3 redirect(Vec3 originalVelocity, Vec3 validatedDirection) {
        if (!isFinite(originalVelocity)
                || !isFinite(validatedDirection)
                || originalVelocity.lengthSqr() < MIN_LENGTH_SQUARED
                || validatedDirection.lengthSqr() < MIN_LENGTH_SQUARED) {
            return originalVelocity;
        }
        return validatedDirection.normalize().scale(originalVelocity.length());
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
