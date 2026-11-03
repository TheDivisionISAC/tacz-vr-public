package com.thedivisionisac.taczvr.client.compat.lrtactical;

import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.thedivisionisac.taczvr.network.TaczVrNetwork;
import com.thedivisionisac.taczvr.network.message.C2SLrTacticalThrowPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Bridges Vivecraft's main attack trigger to LRTactical's vanilla hold-and-release item-use contract. Identification
 * stays registry-based so LRTactical remains an optional runtime integration rather than a compile-time dependency.
 * Active eligible main-hand use refreshes the existing pose message once per client tick so server-timed cooking
 * completion also has a fresh direction. Release still sends immediately; opt-out/non-VR/offhand use stays unchanged.
 */
@Mod.EventBusSubscriber(modid = TaczVr.MOD_ID, value = Dist.CLIENT)
public final class LrTacticalVrInputBridge {
    private static final LrTacticalTriggerUseState TRIGGER_STATE = new LrTacticalTriggerUseState();

    private LrTacticalVrInputBridge() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockTriggerAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && isEligible(Minecraft.getInstance())) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void bridgeTriggerUse(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LrTacticalVrItemPoseTransform.observeHeldItem(minecraft.player);
        LrTacticalTriggerUseState.Step step = TRIGGER_STATE.update(
                isEligible(minecraft),
                minecraft.options.keyAttack.isDown(),
                minecraft.options.keyUse.isDown()
        );

        switch (step.keyTransition()) {
            case PRESS -> minecraft.options.keyUse.setDown(true);
            case RELEASE -> minecraft.options.keyUse.setDown(false);
            case NONE -> {
            }
        }

        LocalPlayer player = minecraft.player;
        if (step.ensureUseStarted()
                && player != null
                && !player.isUsingItem()
                && minecraft.gameMode != null) {
            minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
        if (!step.releaseUsingItem() && player != null && isEligible(minecraft)
                && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            sendControllerDirection(minecraft);
        }
        if (step.releaseUsingItem()
                && player != null
                && player.isUsingItem()
                && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && minecraft.gameMode != null) {
            sendControllerDirection(minecraft);
            minecraft.gameMode.releaseUsingItem(player);
        }
    }

    private static void sendControllerDirection(Minecraft minecraft) {
        ClientPacketListener listener = minecraft.getConnection();
        if (listener == null || !TaczVrNetwork.isRemotePresent(listener.getConnection())) {
            return;
        }
        TaczVrClient.vivecraft().getMainHandShotPose().ifPresent(pose ->
                TaczVrNetwork.sendToServer(C2SLrTacticalThrowPose.pose(pose.direction()))
        );
    }

    private static boolean isEligible(Minecraft minecraft) {
        if (!ModList.get().isLoaded("lrtactical")
                || !TaczVrClientConfig.ENABLE_LRTACTICAL_THROWABLE_VR_COMPAT.get()
                || !TaczVrClient.vivecraft().isVrActive()
                || minecraft.screen != null) {
            return false;
        }
        LocalPlayer player = minecraft.player;
        return player != null
                && !player.isSpectator()
                && minecraft.gameMode != null
                && (!player.isUsingItem() || player.getUsedItemHand() == InteractionHand.MAIN_HAND)
                && isThrowable(player.getMainHandItem());
    }

    public static boolean isThrowable(ItemStack stack) {
        return LrTacticalVrItemSupport.isThrowable(stack);
    }
}
