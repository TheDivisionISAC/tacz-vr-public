package com.thedivisionisac.taczvr.client.vr;

import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

import java.util.Optional;

/**
 * Converts the latest rendered muzzle locator into a per-gun forward distance on the authoritative controller ray.
 * Lateral model offsets are intentionally discarded because they caused visible and ballistic divergence in VR.
 */
public final class VrMuzzlePoseCache {
    private static final long MAX_AGE_NANOS = 1_000_000_000L;
    private static final double FALLBACK_FORWARD_DISTANCE = 0.5D;
    private static final double MIN_FORWARD_DISTANCE = 0.15D;
    private static final double MAX_FORWARD_DISTANCE = 2.5D;

    @Nullable
    private static ResourceLocation renderedGunId;
    private static double forwardDistance;
    private static long updatedAtNanos;

    private VrMuzzlePoseCache() {
    }

    public static synchronized void update(ItemStack stack, Vector3f localOffset) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return;
        }
        renderedGunId = gun.getGunId(stack);
        // Vivecraft's controller forward is local -Z in this render path.
        double candidateDistance = -localOffset.z;
        forwardDistance = Double.isFinite(candidateDistance) && candidateDistance >= MIN_FORWARD_DISTANCE
                ? Math.max(MIN_FORWARD_DISTANCE, Math.min(candidateDistance, MAX_FORWARD_DISTANCE))
                : FALLBACK_FORWARD_DISTANCE;
        updatedAtNanos = System.nanoTime();
    }

    public static synchronized void clear(ItemStack stack) {
        if (matchesGun(stack)) {
            renderedGunId = null;
            forwardDistance = 0.0D;
            updatedAtNanos = 0L;
        }
    }

    public static Optional<Vec3> resolve(ItemStack stack, VrShotPose shotPose) {
        if (!finite(shotPose.position()) || !finite(shotPose.direction())
                || shotPose.direction().lengthSqr() < 1.0E-8D) {
            return Optional.empty();
        }
        double distance = getFreshForwardDistance(stack).orElse(FALLBACK_FORWARD_DISTANCE);
        Vec3 muzzle = shotPose.position().add(shotPose.direction().normalize().scale(distance));
        return finite(muzzle) ? Optional.of(muzzle) : Optional.empty();
    }

    private static synchronized Optional<Double> getFreshForwardDistance(ItemStack stack) {
        if (renderedGunId == null
                || System.nanoTime() - updatedAtNanos > MAX_AGE_NANOS
                || !matchesGun(stack)) {
            return Optional.empty();
        }
        return Optional.of(forwardDistance);
    }

    private static boolean matchesGun(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun != null && renderedGunId != null && renderedGunId.equals(gun.getGunId(stack));
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
