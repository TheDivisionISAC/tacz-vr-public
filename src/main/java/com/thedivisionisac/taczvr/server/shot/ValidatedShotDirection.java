package com.thedivisionisac.taczvr.server.shot;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Normalized server-approved direction with TACZ-compatible pitch/yaw conversion.
 */
public record ValidatedShotDirection(Vec3 direction) {
    public float pitchDegrees() {
        double horizontal = Math.sqrt(this.direction.x * this.direction.x + this.direction.z * this.direction.z);
        return (float) Math.toDegrees(Mth.atan2(-this.direction.y, horizontal));
    }

    public float yawDegrees() {
        return (float) Math.toDegrees(Mth.atan2(-this.direction.x, this.direction.z));
    }
}
