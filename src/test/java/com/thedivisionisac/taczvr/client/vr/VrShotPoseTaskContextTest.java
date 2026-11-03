package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies task-local shot snapshots and cleanup independently of TACZ's scheduler. */
class VrShotPoseTaskContextTest {
    private static final VrShotPoseTaskContext.Source SOURCE = new VrShotPoseTaskContext.Source(
            new Object(), new Object(), new Object(), new Object()
    );
    @Test
    void exposesBoundPoseOnlyWhileTheTaskRuns() {
        VrShotPose pose = new VrShotPose(new Vec3(1.0D, 2.0D, 3.0D), new Vec3(0.0D, 0.0D, -1.0D));
        AtomicReference<VrShotPoseTaskContext.Snapshot> observed = new AtomicReference<>();

        VrShotPoseTaskContext.bind(
                snapshot(pose, true), () -> observed.set(VrShotPoseTaskContext.current().orElseThrow())
        ).run();

        assertEquals(pose, observed.get().pose());
        assertTrue(observed.get().supportHandSnapped());
        assertTrue(VrShotPoseTaskContext.current().isEmpty());
    }

    @Test
    void restoresAnOuterTaskAfterNestedExecution() {
        VrShotPose outer = new VrShotPose(Vec3.ZERO, new Vec3(0.0D, 0.0D, -1.0D));
        VrShotPose inner = new VrShotPose(Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D));
        AtomicReference<VrShotPoseTaskContext.Snapshot> restored = new AtomicReference<>();

        VrShotPoseTaskContext.bind(snapshot(outer, false), () -> {
            VrShotPoseTaskContext.bind(snapshot(inner, true), () -> assertEquals(
                    inner, VrShotPoseTaskContext.current().orElseThrow().pose()
            )).run();
            restored.set(VrShotPoseTaskContext.current().orElseThrow());
        }).run();

        assertEquals(outer, restored.get().pose());
        assertEquals(false, restored.get().supportHandSnapped());
        assertTrue(VrShotPoseTaskContext.current().isEmpty());
    }

    @Test
    void dispatchReadsLiveSourceOnlyOnTheClientExecutorAndKeepsPacketOrder() {
        ArrayDeque<Runnable> clientQueue = new ArrayDeque<>();
        List<String> sent = new ArrayList<>();
        VrShotPoseTaskContext.Snapshot shot = snapshot(
                new VrShotPose(Vec3.ZERO, new Vec3(0, 0, -1)), true
        );
        VrShotPoseTaskContext.dispatch(shot, clientQueue::add,
                () -> { sent.add("read_source"); return SOURCE; },
                value -> { assertEquals(shot.muzzlePosition(), value.muzzlePosition()); sent.add("pose"); },
                () -> sent.add("tacz"));
        assertTrue(sent.isEmpty());
        clientQueue.remove().run();
        assertEquals(List.of("read_source", "pose", "tacz"), sent);
    }

    @Test
    void changedHeldItemFallsBackToOriginalWithoutRecomputingMuzzle() {
        ArrayDeque<Runnable> clientQueue = new ArrayDeque<>();
        AtomicReference<VrShotPoseTaskContext.Source> live = new AtomicReference<>(SOURCE);
        List<String> sent = new ArrayList<>();
        VrShotPoseTaskContext.dispatch(snapshot(new VrShotPose(Vec3.ZERO, new Vec3(0, 0, -1)), true),
                clientQueue::add, live::get, value -> sent.add("pose"), () -> sent.add("tacz"));
        live.set(new VrShotPoseTaskContext.Source(
                SOURCE.connection(), SOURCE.world(), SOURCE.player(), new Object()
        ));
        clientQueue.remove().run();
        assertEquals(List.of("tacz"), sent);
    }

    @Test
    void disconnectedOrReplacedSessionDropsTheObsoletePacketPair() {
        VrShotPoseTaskContext.Source[] changed = {
                null,
                new VrShotPoseTaskContext.Source(new Object(), SOURCE.world(), SOURCE.player(), SOURCE.heldItem()),
                new VrShotPoseTaskContext.Source(SOURCE.connection(), new Object(), SOURCE.player(), SOURCE.heldItem()),
                new VrShotPoseTaskContext.Source(SOURCE.connection(), SOURCE.world(), new Object(), SOURCE.heldItem())
        };
        for (VrShotPoseTaskContext.Source current : changed) {
            List<String> sent = new ArrayList<>();
            VrShotPoseTaskContext.dispatch(snapshot(new VrShotPose(Vec3.ZERO, new Vec3(0, 0, -1)), true),
                    Runnable::run, () -> current, value -> sent.add("pose"), () -> sent.add("tacz"));
            assertTrue(sent.isEmpty());
        }
    }

    @Test
    void delayedTaskKeepsTheCapturedMuzzleAndGrip() {
        VrShotPose pose = new VrShotPose(Vec3.ZERO, new Vec3(0, 0, -1));
        AtomicReference<Vec3> mutableMuzzle = new AtomicReference<>(new Vec3(0, 0, -0.5));
        VrShotPoseTaskContext.Snapshot shot = new VrShotPoseTaskContext.Snapshot(
                pose, mutableMuzzle.get(), true, SOURCE
        );
        AtomicReference<VrShotPoseTaskContext.Snapshot> observed = new AtomicReference<>();
        Runnable task = VrShotPoseTaskContext.bind(shot, () -> observed.set(
                VrShotPoseTaskContext.current().orElseThrow()
        ));
        mutableMuzzle.set(new Vec3(20, 20, 20));
        task.run();
        assertEquals(new Vec3(0, 0, -0.5), observed.get().muzzlePosition());
        assertTrue(observed.get().supportHandSnapped());
    }

    @Test
    void nullSnapshotPreservesTheOriginalSynchronousPath() {
        List<String> sent = new ArrayList<>();
        VrShotPoseTaskContext.dispatch(null,
                task -> { throw new AssertionError("Non-VR send must not be queued"); },
                () -> { throw new AssertionError("Non-VR send must not read VR source"); },
                value -> { throw new AssertionError("Non-VR send must not emit pose"); },
                () -> sent.add("tacz"));
        assertEquals(List.of("tacz"), sent);
    }

    @Test
    void failingTaskAndNullNestedTaskRestoreThreadLocal() {
        VrShotPoseTaskContext.Snapshot shot = snapshot(new VrShotPose(Vec3.ZERO, new Vec3(0, 0, -1)), true);
        assertThrows(IllegalStateException.class, () -> VrShotPoseTaskContext.bind(shot, () -> {
            VrShotPoseTaskContext.bind(null, () -> assertTrue(VrShotPoseTaskContext.current().isEmpty())).run();
            assertEquals(shot, VrShotPoseTaskContext.current().orElseThrow());
            throw new IllegalStateException("task failed");
        }).run());
        assertTrue(VrShotPoseTaskContext.current().isEmpty());
    }

    @Test
    void failedOptionalPoseSendStillAttemptsOriginalPacket() {
        List<String> sent = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> VrShotPoseTaskContext.dispatch(
                snapshot(new VrShotPose(Vec3.ZERO, new Vec3(0, 0, -1)), true),
                Runnable::run, () -> SOURCE,
                value -> { throw new IllegalStateException("optional send failed"); },
                () -> sent.add("tacz")
        ));
        assertEquals(List.of("tacz"), sent);
    }

    private static VrShotPoseTaskContext.Snapshot snapshot(VrShotPose pose, boolean snapped) {
        return new VrShotPoseTaskContext.Snapshot(pose, pose.position(), snapped, SOURCE);
    }

    @Test
    void synchronizedStacksUseFreshMuzzlesOnClientExecutorAndKeepPacketOrder() {
        var key = new VrShotPoseTaskContext.WeaponKey(0, "gun_item", "qbz");
        var source = new VrShotPoseTaskContext.Source(SOURCE.connection(), SOURCE.world(), SOURCE.player(), new Object(), key);
        var original = new VrShotPoseTaskContext.Snapshot(new VrShotPose(Vec3.ZERO, new Vec3(0, 0, -1)),
                new Vec3(99, 99, 99), true, source);
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        List<String> events = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            int shot = i;
            var replacement = new VrShotPoseTaskContext.Source(source.connection(), source.world(), source.player(),
                    new Object(), key);
            VrShotPoseTaskContext.dispatch(original, queue::add, () -> replacement, current -> {
                events.add("capture" + shot);
                return new VrShotPoseTaskContext.Snapshot(original.pose(), new Vec3(0, 0, -shot), false, current);
            }, value -> {
                assertEquals(new Vec3(0, 0, -shot), value.muzzlePosition());
                assertEquals(false, value.supportHandSnapped());
                events.add("pose" + shot);
            }, () -> events.add("tacz" + shot));
        }
        assertTrue(events.isEmpty());
        while (!queue.isEmpty()) queue.remove().run();
        assertEquals(List.of("capture1", "pose1", "tacz1", "capture2", "pose2", "tacz2",
                "capture3", "pose3", "tacz3"), events);
    }

    @Test
    void differentSlotItemTypeGunOrMissingKeyCannotRefresh() {
        var key = new VrShotPoseTaskContext.WeaponKey(0, "gun_item", "qbz");
        var source = new VrShotPoseTaskContext.Source(SOURCE.connection(), SOURCE.world(), SOURCE.player(), new Object(), key);
        var shot = new VrShotPoseTaskContext.Snapshot(new VrShotPose(Vec3.ZERO, new Vec3(0, 0, -1)),
                Vec3.ZERO, false, source);
        VrShotPoseTaskContext.WeaponKey[] changed = {
                null, new VrShotPoseTaskContext.WeaponKey(1, "gun_item", "qbz"),
                new VrShotPoseTaskContext.WeaponKey(0, "other_item", "qbz"),
                new VrShotPoseTaskContext.WeaponKey(0, "gun_item", "m107")
        };
        for (var other : changed) {
            List<String> events = new ArrayList<>();
            var live = new VrShotPoseTaskContext.Source(source.connection(), source.world(), source.player(), new Object(), other);
            VrShotPoseTaskContext.dispatch(shot, Runnable::run, () -> live,
                    ignored -> { throw new AssertionError("Different weapon must not recapture"); },
                    value -> events.add("pose"), () -> events.add("tacz"));
            assertEquals(List.of("tacz"), events);
        }
    }

    @Test
    void failedOrStaleRecaptureDoesNotReuseCapturedMuzzle() {
        var key = new VrShotPoseTaskContext.WeaponKey(0, "gun_item", "qbz");
        var source = new VrShotPoseTaskContext.Source(SOURCE.connection(), SOURCE.world(), SOURCE.player(), new Object(), key);
        var live = new VrShotPoseTaskContext.Source(source.connection(), source.world(), source.player(), new Object(), key);
        var shot = new VrShotPoseTaskContext.Snapshot(new VrShotPose(Vec3.ZERO, new Vec3(0, 0, -1)),
                new Vec3(99, 99, 99), true, source);
        for (boolean stale : new boolean[]{false, true}) {
            List<String> events = new ArrayList<>();
            VrShotPoseTaskContext.dispatch(shot, Runnable::run, () -> live, ignored -> stale ? shot : null,
                    value -> events.add("pose"), () -> events.add("tacz"));
            assertEquals(List.of("tacz"), events);
        }
    }

    @Test
    void reconnectNeverRefreshesOldWorkEvenForTheSameWeaponKey() {
        var key = new VrShotPoseTaskContext.WeaponKey(0, "gun_item", "qbz");
        var source = new VrShotPoseTaskContext.Source(SOURCE.connection(), SOURCE.world(), SOURCE.player(), new Object(), key);
        var live = new VrShotPoseTaskContext.Source(new Object(), source.world(), source.player(), new Object(), key);
        var shot = new VrShotPoseTaskContext.Snapshot(new VrShotPose(Vec3.ZERO, new Vec3(0, 0, -1)), Vec3.ZERO, false, source);
        VrShotPoseTaskContext.dispatch(shot, Runnable::run, () -> live,
                ignored -> { throw new AssertionError("Session was replaced"); },
                value -> { throw new AssertionError("Old session pose sent"); },
                () -> { throw new AssertionError("Old session shot sent"); });
    }
}
