package com.thedivisionisac.taczvr.server.shot;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises changing geometry and short-circuit order without constructing a Minecraft server. */
class MuzzleOriginPolicyTest {
    private static final Vec3 MUZZLE = new Vec3(0, 0, 2);

    @Test void sameWorldMovementRejectsPreviouslyValidOriginBeforeHandLookup() {
        Geometry geometry = new Geometry();
        assertEquals(MUZZLE, geometry.validate(MUZZLE).orElseThrow());
        assertEquals(1, geometry.handReads);
        geometry.rayStarts.clear();
        geometry.eye = new Vec3(20, 0, 0);
        assertTrue(geometry.validate(MUZZLE).isEmpty());
        assertEquals(1, geometry.handReads);
        assertTrue(geometry.rayStarts.isEmpty());
    }

    @Test void changedHandDistanceRejectsOriginWhileEyeRemainsInRange() {
        Geometry geometry = new Geometry();
        assertTrue(geometry.validate(MUZZLE).isPresent());
        geometry.rayStarts.clear();
        geometry.hand = new Vec3(20, 0, 0);
        assertTrue(geometry.validate(MUZZLE).isEmpty());
        assertTrue(geometry.rayStarts.isEmpty());
    }

    @Test void changedBorderRejectsOriginBeforeRaycasting() {
        Geometry geometry = new Geometry();
        assertTrue(geometry.validate(MUZZLE).isPresent());
        geometry.rayStarts.clear();
        geometry.insideBorder = false;
        assertTrue(geometry.validate(MUZZLE).isEmpty());
        assertTrue(geometry.rayStarts.isEmpty());
    }

    @Test void newlyClosedEyePathClampsBeforeCheckingHandPath() {
        Geometry geometry = new Geometry();
        assertEquals(MUZZLE, geometry.validate(MUZZLE).orElseThrow());
        geometry.rayStarts.clear();
        geometry.eyeHit = Optional.of(new Vec3(0, 0, 1));
        geometry.handHit = Optional.of(new Vec3(0, 0, 1.5));
        assertEquals(new Vec3(0, 0, 1 - MuzzleOriginClamp.SURFACE_RETREAT_BLOCKS),
                geometry.validate(MUZZLE).orElseThrow());
        assertEquals(List.of(geometry.eye), geometry.rayStarts);
    }

    @Test void newHandOnlyObstructionDropsOrigin() {
        Geometry geometry = new Geometry();
        assertTrue(geometry.validate(MUZZLE).isPresent());
        geometry.rayStarts.clear();
        geometry.handHit = Optional.of(new Vec3(0, 0, 1.5));
        assertTrue(geometry.validate(MUZZLE).isEmpty());
        assertEquals(List.of(geometry.eye, geometry.hand), geometry.rayStarts);
    }

    @Test void obstructionOptOutSkipsBothRaycasts() {
        Geometry geometry = new Geometry();
        geometry.checkObstruction = false;
        geometry.eyeHit = Optional.of(new Vec3(0, 0, 1));
        geometry.handHit = Optional.of(new Vec3(0, 0, 1.5));
        assertEquals(MUZZLE, geometry.validate(MUZZLE).orElseThrow());
        assertTrue(geometry.rayStarts.isEmpty());
    }

    @Test void distanceBoundariesRemainInclusive() {
        Geometry geometry = new Geometry();
        geometry.hand = new Vec3(0, 0, 0.5);
        Vec3 boundary = new Vec3(0, 0, 3);
        assertEquals(boundary, geometry.validate(boundary).orElseThrow());
        assertTrue(geometry.validate(new Vec3(0, 0, 3.001)).isEmpty());
        geometry.hand = new Vec3(0, 0, 0.499);
        assertTrue(geometry.validate(boundary).isEmpty());
    }

    @Test void absentHandKeepsEyeAndBorderChecks() {
        Geometry geometry = new Geometry();
        geometry.hand = null;
        assertEquals(MUZZLE, geometry.validate(MUZZLE).orElseThrow());
        assertEquals(List.of(geometry.eye), geometry.rayStarts);
        geometry.insideBorder = false;
        assertTrue(geometry.validate(MUZZLE).isEmpty());
    }

    @Test void absentOrNonFiniteMuzzleDoesNotQueryWorld() {
        Geometry geometry = new Geometry();
        Vec3[] invalid = {null, new Vec3(Double.NaN, 0, 0),
                new Vec3(0, Double.POSITIVE_INFINITY, 0), new Vec3(0, 0, Double.NEGATIVE_INFINITY)};
        for (Vec3 candidate : invalid) assertTrue(geometry.validate(candidate).isEmpty());
        assertEquals(0, geometry.handReads);
        assertEquals(0, geometry.borderReads);
        assertTrue(geometry.rayStarts.isEmpty());
    }

    /** Mutable callbacks represent changes between independent server validation calls. */
    private static final class Geometry {
        private Vec3 eye = Vec3.ZERO;
        private Vec3 hand = new Vec3(0, 0, 1);
        private boolean insideBorder = true;
        private boolean checkObstruction = true;
        private Optional<Vec3> eyeHit = Optional.empty();
        private Optional<Vec3> handHit = Optional.empty();
        private int handReads;
        private int borderReads;
        private final List<Vec3> rayStarts = new ArrayList<>();

        private Optional<Vec3> validate(Vec3 candidate) {
            return MuzzleOriginPolicy.validate(candidate, eye,
                    () -> { handReads++; return Optional.ofNullable(hand); },
                    3.0, 2.5, checkObstruction,
                    ignored -> { borderReads++; return insideBorder; },
                    (start, end) -> {
                        rayStarts.add(start);
                        return start.equals(eye) ? eyeHit : handHit;
                    });
        }
    }
}
