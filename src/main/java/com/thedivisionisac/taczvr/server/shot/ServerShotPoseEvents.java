package com.thedivisionisac.taczvr.server.shot;

import com.thedivisionisac.taczvr.TaczVr;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Clears pending client claims whenever a player lifecycle boundary invalidates TACZ timestamps.
 */
@Mod.EventBusSubscriber(modid = TaczVr.MOD_ID)
public final class ServerShotPoseEvents {
    private ServerShotPoseEvents() {
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerShotPoseStore.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        ServerShotPoseStore.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        ServerShotPoseStore.clear(event.getOriginal().getUUID());
        ServerShotPoseStore.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerShotPoseStore.clear(player.getUUID());
        }
    }
}
