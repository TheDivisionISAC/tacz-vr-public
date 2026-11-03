package com.thedivisionisac.taczvr.client;

import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.client.compat.VivecraftBridge;
import com.thedivisionisac.taczvr.client.vr.VrScopeAlignmentCapture;
import com.thedivisionisac.taczvr.client.render.VrScopeImageShader;
import com.thedivisionisac.taczvr.client.render.VrGunHandVisibility;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Owns client lifecycle initialization and the process-wide safe Vivecraft bridge.
 * Resource replacement and disconnect clear transient observations on the client thread, including inactive VR.
 */
public final class TaczVrClient {
    private static VivecraftBridge vivecraft = VivecraftBridge.unavailable();

    private TaczVrClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(TaczVrClient::clientSetup);
        modBus.addListener(VrScopeImageShader::register);
        modBus.addListener(TaczVrClient::registerReloadListeners);
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

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(
                (ResourceManagerReloadListener) resourceManager ->
                        resetTransientState()
        );
    }

    /**
     * Clears observations on the owning thread. The presence guard avoids initializing Vivecraft-backed
     * hand caches when the optional mod is absent; being outside VR does not skip cleanup.
     */
    public static void resetTransientState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(TaczVrClient::resetTransientState);
            return;
        }
        VrScopeAlignmentCapture.resetTransientState();
        if (ModList.get().isLoaded(TaczVr.VIVECRAFT_MOD_ID)) {
            VrGunHandVisibility.resetTransientState();
        }
    }
}
