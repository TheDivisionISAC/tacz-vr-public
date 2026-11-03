package com.thedivisionisac.taczvr.client.event;

import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.client.TaczVrClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Clears optical snapshots, diagnostic gates, and hand-anchor observations when a client connection ends. Forge dispatches
 * this client-only event on its main event bus; both it and resource reload cleanup use the render-state owner's
 * thread-aware reset entry point. The handler changes no gameplay or render state beyond discarding transient
 * observations.
 */
@Mod.EventBusSubscriber(modid = TaczVr.MOD_ID, value = Dist.CLIENT)
public final class ClientVrTransientStateHandler {
    private ClientVrTransientStateHandler() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TaczVrClient.resetTransientState();
    }
}
