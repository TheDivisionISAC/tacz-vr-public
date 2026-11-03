package com.thedivisionisac.taczvr.compat;

import com.thedivisionisac.taczvr.TaczVr;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.vivecraft.api.VRAPI;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;

import java.util.Optional;

/**
 * Optional common-side Vivecraft pose lookup used to cross-check client shot claims.
 */
public final class VivecraftServerCompat {
    private static final Bridge UNAVAILABLE = new Bridge() {
        @Override
        public boolean isVrPlayer(Player player) {
            return false;
        }

        @Override
        public Optional<MainHandPose> getMainHandPose(Player player) {
            return Optional.empty();
        }
    };
    private static Bridge bridge = UNAVAILABLE;
    private static boolean failureLogged;

    private VivecraftServerCompat() {
    }

    public static void init() {
        if (!ModList.get().isLoaded(TaczVr.VIVECRAFT_MOD_ID)) {
            return;
        }
        try {
            bridge = new ApiBridge();
        } catch (LinkageError | RuntimeException error) {
            disable("initialization", error);
        }
    }

    public static Optional<Vec3> getMainHandDirection(Player player) {
        try {
            return bridge.getMainHandPose(player).map(MainHandPose::direction);
        } catch (LinkageError | RuntimeException error) {
            disable("server pose query", error);
            return Optional.empty();
        }
    }

    public static boolean isVrPlayer(Player player) {
        try {
            return bridge.isVrPlayer(player);
        } catch (LinkageError | RuntimeException error) {
            disable("server VR-player query", error);
            return false;
        }
    }

    public static Optional<Vec3> getMainHandPosition(Player player) {
        try {
            return bridge.getMainHandPose(player).map(MainHandPose::position);
        } catch (LinkageError | RuntimeException error) {
            disable("server pose query", error);
            return Optional.empty();
        }
    }

    private static void disable(String operation, Throwable error) {
        bridge = UNAVAILABLE;
        if (!failureLogged) {
            failureLogged = true;
            TaczVr.LOGGER.warn("Disabling the Vivecraft server bridge after a failed {}.", operation, error);
        }
    }

    private interface Bridge {
        boolean isVrPlayer(Player player);

        Optional<MainHandPose> getMainHandPose(Player player);
    }

    private static final class ApiBridge implements Bridge {
        private final VRAPI api = VRAPI.instance();

        @Override
        public boolean isVrPlayer(Player player) {
            return this.api.isVRPlayer(player);
        }

        @Override
        public Optional<MainHandPose> getMainHandPose(Player player) {
            if (!this.isVrPlayer(player)) {
                return Optional.empty();
            }
            VRPose pose = this.api.getVRPose(player);
            if (pose == null) {
                return Optional.empty();
            }
            VRBodyPartData hand = pose.getMainHand();
            if (hand == null) {
                return Optional.empty();
            }
            Vec3 direction = hand.getDir();
            Vec3 position = hand.getPos();
            if (!isFinite(position) || !isFinite(direction) || direction.lengthSqr() < 1.0E-8) {
                return Optional.empty();
            }
            return Optional.of(new MainHandPose(position, direction.normalize()));
        }
    }

    private record MainHandPose(Vec3 position, Vec3 direction) {
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
