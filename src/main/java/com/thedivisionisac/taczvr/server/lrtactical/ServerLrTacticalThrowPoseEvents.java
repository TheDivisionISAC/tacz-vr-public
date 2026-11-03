package com.thedivisionisac.taczvr.server.lrtactical;

import com.thedivisionisac.taczvr.TaczVr;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drops pending throw claims across player lifecycle boundaries.
 */
@Mod.EventBusSubscriber(modid = TaczVr.MOD_ID)
public final class ServerLrTacticalThrowPoseEvents {
    private ServerLrTacticalThrowPoseEvents() {
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerLrTacticalThrowPoseStore.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        ServerLrTacticalThrowPoseStore.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        ServerLrTacticalThrowPoseStore.clear(event.getOriginal().getUUID());
        ServerLrTacticalThrowPoseStore.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLrTacticalThrowPoseStore.clear(player.getUUID());
        }
    }
}
