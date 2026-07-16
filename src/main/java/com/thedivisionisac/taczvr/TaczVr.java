package com.thedivisionisac.taczvr;

import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.thedivisionisac.taczvr.compat.VivecraftServerCompat;
import com.thedivisionisac.taczvr.config.TaczVrServerConfig;
import com.thedivisionisac.taczvr.network.TaczVrNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Registers addon configuration, networking, and side-specific bootstrap code.
 */
@Mod(TaczVr.MOD_ID)
public final class TaczVr {
    public static final String MOD_ID = "taczvr";
    public static final String TACZ_MOD_ID = "tacz";
    public static final String VIVECRAFT_MOD_ID = "vivecraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TaczVr() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TaczVrClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TaczVrServerConfig.SPEC);
        modBus.addListener(this::commonSetup);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.thedivisionisac.taczvr.client.TaczVrClient.init(modBus));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            TaczVrNetwork.register();
            VivecraftServerCompat.init();
            LOGGER.info(
                    "TACZ VR loaded. TACZ present: {}, Vivecraft present: {}",
                    ModList.get().isLoaded(TACZ_MOD_ID),
                    ModList.get().isLoaded(VIVECRAFT_MOD_ID)
            );
        });
    }
}
