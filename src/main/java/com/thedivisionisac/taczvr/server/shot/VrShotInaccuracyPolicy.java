package com.thedivisionisac.taczvr.server.shot;

import com.thedivisionisac.taczvr.compat.VivecraftServerCompat;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.tacz.guns.util.HitboxHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * Selects TACZ's authored AIM inaccuracy for physical VR aiming without forcing TACZ's visual ADS state.
 */
public final class VrShotInaccuracyPolicy {
    private VrShotInaccuracyPolicy() {
    }

    public static InaccuracyType resolve(LivingEntity shooter, String gunType, boolean enabled) {
        InaccuracyType original = InaccuracyType.getInaccuracyType(shooter);
        boolean vrPlayer = shooter instanceof Player player && VivecraftServerCompat.isVrPlayer(player);
        boolean supportHandSnapped = ServerShotExecutionContext.current()
                .map(ValidatedShotPose::supportHandSnapped)
                .orElse(false);
        return select(
                original,
                resolveHipInaccuracy(shooter),
                enabled,
                vrPlayer,
                gunType,
                supportHandSnapped
        );
    }

    static InaccuracyType select(InaccuracyType original, InaccuracyType hipInaccuracy, boolean enabled,
                                 boolean vrPlayer, String gunType, boolean supportHandSnapped) {
        if (!enabled || !vrPlayer) {
            return original;
        }
        return isPistol(gunType) || supportHandSnapped ? InaccuracyType.AIM : hipInaccuracy;
    }

    private static boolean isPistol(String gunType) {
        return "pistol".equalsIgnoreCase(gunType);
    }

    private static InaccuracyType resolveHipInaccuracy(LivingEntity shooter) {
        if (!shooter.isSwimming() && shooter.getPose() == Pose.SWIMMING) {
            return InaccuracyType.LIE;
        }
        if (shooter.getPose() == Pose.CROUCHING) {
            return InaccuracyType.SNEAK;
        }
        double movement = Math.abs(shooter.walkDist - shooter.walkDistO);
        if (shooter instanceof Player player) {
            movement = HitboxHelper.getPlayerVelocity(player).length();
        }
        return movement > 0.05F ? InaccuracyType.MOVE : InaccuracyType.STAND;
    }
}
