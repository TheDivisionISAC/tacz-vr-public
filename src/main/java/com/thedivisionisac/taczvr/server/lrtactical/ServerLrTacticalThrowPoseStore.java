package com.thedivisionisac.taczvr.server.lrtactical;

import com.thedivisionisac.taczvr.config.TaczVrServerConfig;
import com.thedivisionisac.taczvr.server.shot.ValidatedShotDirection;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-thread-confined handoff between the validated addon packet and LRTactical's release-use entity creation.
 */
public final class ServerLrTacticalThrowPoseStore {
    private static final PendingLrTacticalThrowPoseStore<UUID, ValidatedShotDirection> PENDING =
            new PendingLrTacticalThrowPoseStore<>();

    private ServerLrTacticalThrowPoseStore() {
    }

    public static void put(ServerPlayer player, ValidatedShotDirection direction) {
        PENDING.put(player.getUUID(), direction, player.serverLevel().getGameTime());
    }

    public static Optional<ValidatedShotDirection> consume(ServerPlayer player) {
        return PENDING.consume(
                player.getUUID(),
                player.serverLevel().getGameTime(),
                TaczVrServerConfig.LRTACTICAL_THROW_POSE_TTL_TICKS.get()
        );
    }

    public static void clear(UUID playerId) {
        PENDING.clear(playerId);
    }
}
