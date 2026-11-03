package com.thedivisionisac.taczvr.client.control;

import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import net.minecraft.world.phys.Vec3;

/** Produces one fixed room-space HUD retreat whenever the caller has already confirmed a held TACZ gun. */
public final class VrGunHotbarRetreat {
    private static final double EPSILON = 1.0E-12D;

    private VrGunHotbarRetreat() {
    }

    public static Vec3 offset(Vec3 backwardAxis) {
        return fixedOffset(backwardAxis, TaczVrClientConfig.VR_GUN_HOTBAR_RETREAT_DISTANCE.get());
    }

    /**
     * Returns the HUD-local axis that maps back along the physical controller for Vivecraft's HAND/WRIST rotations.
     */
    public static Vec3 localBackwardAxis(boolean wristHud, boolean reverseHands) {
        if (!wristHud) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return new Vec3(reverseHands ? 1.0D : -1.0D, 0.0D, 0.0D);
    }

    static Vec3 fixedOffset(Vec3 backwardAxis, double distance) {
        double lengthSquared = backwardAxis.lengthSqr();
        if (!Double.isFinite(lengthSquared) || lengthSquared <= EPSILON
                || !Double.isFinite(distance) || distance <= 0.0D) {
            return Vec3.ZERO;
        }
        return backwardAxis.scale(distance / Math.sqrt(lengthSquared));
    }
}
