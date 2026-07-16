package com.thedivisionisac.taczvr.server.shot;

import com.thedivisionisac.taczvr.config.TaczVrServerConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-thread-confined, bounded, single-use pose store keyed by player UUID and TACZ timestamp.
 */
public final class ServerShotPoseStore {
    private static final PendingShotPoseStore<UUID, ValidatedShotPose> PENDING = new PendingShotPoseStore<>();

    private ServerShotPoseStore() {
    }

    public static void put(ServerPlayer player, long timestamp, ValidatedShotPose pose) {
        long gameTime = player.serverLevel().getGameTime();
        PENDING.put(
                player.getUUID(),
                timestamp,
                pose,
                gameTime,
                TaczVrServerConfig.MAX_PENDING_SHOT_POSES.get(),
                TaczVrServerConfig.SHOT_POSE_TTL_TICKS.get()
        );
    }

    public static Optional<ValidatedShotPose> consume(ServerPlayer player, long timestamp) {
        long gameTime = player.serverLevel().getGameTime();
        return PENDING.consume(
                player.getUUID(), timestamp, gameTime, TaczVrServerConfig.SHOT_POSE_TTL_TICKS.get()
        );
    }

    public static void clear(UUID playerId) {
        PENDING.clear(playerId);
    }
}
