package com.thedivisionisac.taczvr.server.shot;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Function;

/**
 * Single-use server result; the muzzle remains optional even when direction validation succeeds.
 * Muzzle revalidation creates an immutable copy and preserves the accepted direction and grip declaration.
 */
public record ValidatedShotPose(ValidatedShotDirection direction, @Nullable Vec3 muzzlePosition,
                                boolean supportHandSnapped) {
    public ValidatedShotPose revalidateMuzzle(Function<Vec3, Optional<Vec3>> revalidate) {
        Vec3 currentMuzzle = Optional.ofNullable(this.muzzlePosition).flatMap(revalidate).orElse(null);
        return new ValidatedShotPose(this.direction, currentMuzzle, this.supportHandSnapped);
    }

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
