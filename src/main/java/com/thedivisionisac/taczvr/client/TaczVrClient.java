package com.thedivisionisac.taczvr.client;

import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.client.compat.VivecraftBridge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Owns client lifecycle initialization and the process-wide safe Vivecraft bridge.
 */
public final class TaczVrClient {
    private static VivecraftBridge vivecraft = VivecraftBridge.unavailable();

    private TaczVrClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(TaczVrClient::clientSetup);
    }

    public static VivecraftBridge vivecraft() {
        return vivecraft;
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            vivecraft = VivecraftBridge.create();
            TaczVr.LOGGER.info("TACZ VR client initialized. Vivecraft bridge: {}", vivecraft.bridgeName());
        });
    }
}
