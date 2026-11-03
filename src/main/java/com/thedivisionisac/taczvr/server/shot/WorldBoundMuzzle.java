package com.thedivisionisac.taczvr.server.shot;

import net.minecraft.world.phys.Vec3;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Server-thread lease for an already validated absolute muzzle held by a delayed TACZ script.
 * World identity, not dimension-name equality, bounds its lifetime; a mismatched generation permanently expires it.
 * Expiration returns no override so TACZ retains its current-world projectile origin.
 * Even in the same world, every use must pass a fresh geometry check; a failed check affects only that use.
 */
public final class WorldBoundMuzzle {
    private Object world;
    private Vec3 position;

    public WorldBoundMuzzle(Object world, Vec3 position) {
        this.world = Objects.requireNonNull(world);
        this.position = Objects.requireNonNull(position);
    }

    public Optional<Vec3> resolve(Object currentWorld, Function<Vec3, Optional<Vec3>> revalidate) {
        if (this.world != currentWorld) {
            this.world = null;
            this.position = null;
        }
        return Optional.ofNullable(this.position).flatMap(revalidate);
    }
}
