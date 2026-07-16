package com.thedivisionisac.taczvr.client.event;

import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.tacz.guns.api.event.common.GunFireEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Adds short main-hand haptics after TACZ confirms a local client-side gun-fire event.
 */
@Mod.EventBusSubscriber(modid = TaczVr.MOD_ID, value = Dist.CLIENT)
public final class GunFireHapticHandler {
    private static final float SHOT_HAPTIC_DURATION_SECONDS = 0.04F;

    private GunFireHapticHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGunFire(GunFireEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!TaczVrClientConfig.ENABLE_HAPTICS.get()
                || !event.getLogicalSide().isClient()
                || event.isCanceled()
                || minecraft.player == null
                || event.getShooter() != minecraft.player
                || !TaczVrClient.vivecraft().isVrActive()) {
            return;
        }
        TaczVrClient.vivecraft().triggerMainHandHaptic(SHOT_HAPTIC_DURATION_SECONDS);
    }
}
