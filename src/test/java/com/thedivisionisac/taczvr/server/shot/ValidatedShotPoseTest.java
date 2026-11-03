package com.thedivisionisac.taczvr.server.shot;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Muzzle fallback preserves accepted direction/grip and cannot make a consumed pending pose reusable. */
class ValidatedShotPoseTest {
    @Test void consumedPoseKeepsDirectionAndGripWhenMovedEyeInvalidatesMuzzle() {
        var direction = new ValidatedShotDirection(new Vec3(1, 0, 0));
        Vec3 muzzle = new Vec3(0, 0, 2);
        var original = new ValidatedShotPose(direction, muzzle, true);
        var pending = new PendingShotPoseStore<String, ValidatedShotPose>();
        pending.put("player", 100L, original, 20L, 16, 10L);
        AtomicInteger checks = new AtomicInteger();
        ValidatedShotPose consumed = pending.consume("player", 100L, 21L, 10L)
                .map(pose -> pose.revalidateMuzzle(candidate -> {
                    checks.incrementAndGet();
                    return MuzzleOriginPolicy.validate(candidate, new Vec3(20, 0, 0),
                            () -> { throw new AssertionError("Hand lookup after eye rejection"); },
                            3.0, 2.5, true,
                            ignored -> { throw new AssertionError("Border query after eye rejection"); },
                            (start, end) -> { throw new AssertionError("Raycast after eye rejection"); });
                })).orElseThrow();
        assertSame(direction, consumed.direction());
        assertTrue(consumed.supportHandSnapped());
        assertFalse(consumed.hasMuzzlePosition());
        assertNull(consumed.muzzlePosition());
        assertEquals(muzzle, original.muzzlePosition());
        assertEquals(1, checks.get());
        assertTrue(pending.consume("player", 100L, 21L, 10L).isEmpty());
    }

    @Test void clampKeepsDirectionAndDoesNotPromoteGrip() {
        var direction = new ValidatedShotDirection(new Vec3(1, 0, 0));
        Vec3 muzzle = new Vec3(0, 0, 2);
        Vec3 clamped = new Vec3(0, 0, 1 - MuzzleOriginClamp.SURFACE_RETREAT_BLOCKS);
        var original = new ValidatedShotPose(direction, muzzle, false);
        ValidatedShotPose refreshed = original.revalidateMuzzle(candidate -> {
            assertEquals(muzzle, candidate);
            return Optional.of(clamped);
        });
        assertSame(direction, refreshed.direction());
        assertFalse(refreshed.supportHandSnapped());
        assertEquals(clamped, refreshed.muzzlePosition());
        assertEquals(muzzle, original.muzzlePosition());
    }

    @Test void directionOnlyPoseSkipsGeometryLookup() {
        var direction = new ValidatedShotDirection(new Vec3(0, 0, 1));
        var original = new ValidatedShotPose(direction, null, true);
        ValidatedShotPose refreshed = original.revalidateMuzzle(candidate -> {
            throw new AssertionError("Direction-only pose has no muzzle");
        });
        assertSame(direction, refreshed.direction());
        assertTrue(refreshed.supportHandSnapped());
        assertNull(refreshed.muzzlePosition());
    }
}
