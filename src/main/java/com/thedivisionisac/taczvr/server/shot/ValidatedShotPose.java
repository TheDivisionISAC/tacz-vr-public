package com.thedivisionisac.taczvr.server.shot;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Single-use server result; the muzzle remains optional even when direction validation succeeds.
 */
public record ValidatedShotPose(ValidatedShotDirection direction, @Nullable Vec3 muzzlePosition) {
    public float pitchDegrees() {
        return this.direction.pitchDegrees();
    }

    public float yawDegrees() {
        return this.direction.yawDegrees();
    }

    public boolean hasMuzzlePosition() {
        return this.muzzlePosition != null;
    }
}
