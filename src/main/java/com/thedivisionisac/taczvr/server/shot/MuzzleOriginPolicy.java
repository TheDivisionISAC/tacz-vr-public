package com.thedivisionisac.taczvr.server.shot;

import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Stateless muzzle geometry policy shared by receipt, consumption, and delayed projectile creation.
 * The server-thread caller supplies current positions and authoritative boundary/collision queries.
 * Eye distance precedes optional hand lookup; eye obstruction clamps before hand-only obstruction is checked.
 * Rejection removes only the origin override and does not decide shot eligibility, direction, or grip.
 */
final class MuzzleOriginPolicy {
    private MuzzleOriginPolicy() {
    }

    static Optional<Vec3> validate(@Nullable Vec3 candidate, Vec3 eyePosition,
                                   Supplier<Optional<Vec3>> currentHandPosition,
                                   double maxEyeDistance, double maxHandDistance, boolean checkObstruction,
                                   Predicate<Vec3> insideBorder,
                                   BiFunction<Vec3, Vec3, Optional<Vec3>> firstBlock) {
        if (candidate == null || !isFinite(candidate)) {
            return Optional.empty();
        }
        if (candidate.distanceToSqr(eyePosition) > maxEyeDistance * maxEyeDistance) {
            return Optional.empty();
        }
        Optional<Vec3> handPosition = currentHandPosition.get();
        if (handPosition.isPresent()
                && candidate.distanceToSqr(handPosition.get()) > maxHandDistance * maxHandDistance) {
            return Optional.empty();
        }
        if (!insideBorder.test(candidate)) {
            return Optional.empty();
        }
        if (checkObstruction) {
            Optional<Vec3> eyeHit = firstBlock.apply(eyePosition, candidate);
            if (eyeHit.isPresent()) {
                return Optional.of(MuzzleOriginClamp.beforeSurface(eyePosition, eyeHit.get()));
            }
            if (handPosition.map(hand -> firstBlock.apply(hand, candidate).isPresent()).orElse(false)) {
                return Optional.empty();
            }
        }
        return Optional.of(candidate);
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
