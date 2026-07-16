package com.thedivisionisac.taczvr.server.shot;

import net.minecraft.world.phys.Vec3;

/**
 * Moves an obstructed muzzle from a block-face hit toward the player's safe side by a small collision margin.
 */
final class MuzzleOriginClamp {
    static final double SURFACE_RETREAT_BLOCKS = 1.0D / 64.0D;

    private MuzzleOriginClamp() {
    }

    static Vec3 beforeSurface(Vec3 safeOrigin, Vec3 surfaceHit) {
        Vec3 towardSafeSide = safeOrigin.subtract(surfaceHit);
        double distance = towardSafeSide.length();
        if (!Double.isFinite(distance) || distance < 1.0E-8D) {
            return safeOrigin;
        }
        double retreat = Math.min(distance, SURFACE_RETREAT_BLOCKS);
        return surfaceHit.add(towardSafeSide.scale(retreat / distance));
    }
}
