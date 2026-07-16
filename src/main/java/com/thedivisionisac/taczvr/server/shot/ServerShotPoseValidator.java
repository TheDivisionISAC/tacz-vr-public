package com.thedivisionisac.taczvr.server.shot;

import com.thedivisionisac.taczvr.compat.VivecraftServerCompat;
import com.thedivisionisac.taczvr.config.TaczVrServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

import java.util.Optional;

/**
 * Validates direction strictly while degrading an invalid muzzle to eye-origin shooting instead of rejecting the shot.
 */
public final class ServerShotPoseValidator {
    private static final double MIN_DIRECTION_LENGTH_SQUARED = 0.25;
    private static final double MAX_DIRECTION_LENGTH_SQUARED = 2.25;

    private ServerShotPoseValidator() {
    }

    public static Optional<ValidatedShotPose> validate(ServerPlayer player, Vec3 candidateDirection,
                                                        @Nullable Vec3 candidateMuzzle) {
        Optional<ValidatedShotDirection> direction = validateDirection(player, candidateDirection);
        if (direction.isEmpty()) {
            return Optional.empty();
        }
        Vec3 muzzle = TaczVrServerConfig.ENABLE_VR_MUZZLE_ORIGIN.get()
                ? validateMuzzle(player, candidateMuzzle).orElse(null)
                : null;
        return Optional.of(new ValidatedShotPose(direction.get(), muzzle));
    }

    public static Optional<ValidatedShotDirection> validateDirection(ServerPlayer player, Vec3 candidate) {
        if (!isFinite(candidate)) {
            return Optional.empty();
        }
        double lengthSquared = candidate.lengthSqr();
        if (lengthSquared < MIN_DIRECTION_LENGTH_SQUARED || lengthSquared > MAX_DIRECTION_LENGTH_SQUARED) {
            return Optional.empty();
        }

        Vec3 normalized = candidate.normalize();
        Optional<Vec3> serverHandDirection = VivecraftServerCompat.getMainHandDirection(player);
        if (serverHandDirection.isEmpty() && TaczVrServerConfig.REQUIRE_SERVER_VIVECRAFT_POSE.get()) {
            return Optional.empty();
        }
        Vec3 reference = serverHandDirection.orElseGet(player::getLookAngle);
        double maxAngle = serverHandDirection.isPresent()
                ? TaczVrServerConfig.MAX_SERVER_POSE_ANGLE_DEGREES.get()
                : TaczVrServerConfig.MAX_FALLBACK_VIEW_ANGLE_DEGREES.get();
        if (angleDegrees(normalized, reference) > maxAngle) {
            return Optional.empty();
        }
        return Optional.of(new ValidatedShotDirection(normalized));
    }

    private static Optional<Vec3> validateMuzzle(ServerPlayer player, @Nullable Vec3 candidate) {
        if (candidate == null || !isFinite(candidate)) {
            return Optional.empty();
        }

        Vec3 eyePosition = player.getEyePosition();
        if (candidate.distanceToSqr(eyePosition) > square(TaczVrServerConfig.MAX_MUZZLE_EYE_DISTANCE.get())) {
            return Optional.empty();
        }

        Optional<Vec3> serverHandPosition = VivecraftServerCompat.getMainHandPosition(player);
        if (serverHandPosition.isPresent()
                && candidate.distanceToSqr(serverHandPosition.get())
                > square(TaczVrServerConfig.MAX_MUZZLE_HAND_DISTANCE.get())) {
            return Optional.empty();
        }
        if (!player.serverLevel().getWorldBorder().isWithinBounds(BlockPos.containing(candidate))) {
            return Optional.empty();
        }
        if (TaczVrServerConfig.CHECK_MUZZLE_OBSTRUCTION.get()) {
            HitResult eyeHit = clip(player, eyePosition, candidate);
            if (eyeHit.getType() == HitResult.Type.BLOCK) {
                return Optional.of(MuzzleOriginClamp.beforeSurface(eyePosition, eyeHit.getLocation()));
            }
            if (serverHandPosition
                    .map(handPosition -> isObstructed(player, handPosition, candidate))
                    .orElse(false)) {
                return Optional.empty();
            }
        }
        return Optional.of(candidate);
    }

    private static boolean isObstructed(ServerPlayer player, Vec3 start, Vec3 end) {
        return clip(player, start, end).getType() == HitResult.Type.BLOCK;
    }

    private static HitResult clip(ServerPlayer player, Vec3 start, Vec3 end) {
        return player.serverLevel().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
        ));
    }

    private static double square(double value) {
        return value * value;
    }

    private static double angleDegrees(Vec3 first, Vec3 second) {
        double dot = Mth.clamp(first.normalize().dot(second.normalize()), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
