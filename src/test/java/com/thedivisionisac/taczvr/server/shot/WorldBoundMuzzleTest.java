package com.thedivisionisac.taczvr.server.shot;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies permanent world invalidation and fresh geometry checks for every delayed pellet. */
class WorldBoundMuzzleTest {
    @Test void sameWorldAllowsEachBurstPellet() {
        Object world = new Object();
        var muzzle = new WorldBoundMuzzle(world, new Vec3(1, 2, 3));
        assertEquals(new Vec3(1, 2, 3), muzzle.resolve(world, Optional::of).orElseThrow());
        assertTrue(muzzle.resolve(world, Optional::of).isPresent());
    }
    @Test void dimensionChangeExpiresTheAlreadyConsumedSnapshotEvenOnReturn() {
        Object oldWorld = new Object();
        var muzzle = new WorldBoundMuzzle(oldWorld, Vec3.ZERO);
        Function<Vec3, Optional<Vec3>> forbiddenCheck = candidate -> {
            throw new AssertionError("Expired worlds must not invoke geometry validation");
        };
        assertTrue(muzzle.resolve(new Object(), forbiddenCheck).isEmpty());
        assertTrue(muzzle.resolve(oldWorld, forbiddenCheck).isEmpty());
    }
    @Test void equalNamedWorldObjectsAreNotTheSameWorld() {
        String world = new String("overworld");
        var muzzle = new WorldBoundMuzzle(world, Vec3.ZERO);
        assertTrue(muzzle.resolve(new String("overworld"), Optional::of).isEmpty());
    }

    @Test void eachPelletRechecksGeometryAndTransientFailureDoesNotExpireTheWorld() {
        Object world = new Object();
        Vec3 position = new Vec3(0, 0, 2);
        var muzzle = new WorldBoundMuzzle(world, position);
        AtomicReference<Vec3> eye = new AtomicReference<>(Vec3.ZERO);
        AtomicReference<Optional<Vec3>> block = new AtomicReference<>(Optional.empty());
        AtomicBoolean insideBorder = new AtomicBoolean(true);
        AtomicInteger checks = new AtomicInteger();
        Function<Vec3, Optional<Vec3>> revalidate = candidate -> {
            checks.incrementAndGet();
            return MuzzleOriginPolicy.validate(candidate, eye.get(),
                    () -> Optional.of(new Vec3(0, 0, 1)), 3.0, 2.5, true,
                    ignored -> insideBorder.get(), (start, end) -> block.get());
        };
        assertEquals(position, muzzle.resolve(world, revalidate).orElseThrow());
        eye.set(new Vec3(20, 0, 0));
        assertTrue(muzzle.resolve(world, revalidate).isEmpty());
        eye.set(Vec3.ZERO);
        assertEquals(position, muzzle.resolve(world, revalidate).orElseThrow());
        block.set(Optional.of(new Vec3(0, 0, 1)));
        assertEquals(new Vec3(0, 0, 1 - MuzzleOriginClamp.SURFACE_RETREAT_BLOCKS),
                muzzle.resolve(world, revalidate).orElseThrow());
        insideBorder.set(false);
        assertTrue(muzzle.resolve(world, revalidate).isEmpty());
        assertEquals(5, checks.get());
    }
}
