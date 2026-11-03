package com.thedivisionisac.taczvr.client.compat;

import com.thedivisionisac.taczvr.client.render.VrHandVisibilityPolicy;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.trackers.BowTracker;
import org.vivecraft.client_vr.gameplay.trackers.TelescopeTracker;
import org.vivecraft.client_vr.settings.VRSettings;

/** Mirrors locked Vivecraft 1.3.15 arm guards when planning optional ownership of its native hand draw. */
public final class VivecraftHandVisibility {
    private VivecraftHandVisibility() {
    }

    public static boolean allows(AbstractClientPlayer player, InteractionHand hand, ItemStack stack) {
        if (player == null) return false;
        var holder = ClientDataHolderVR.getInstance();
        var settings = holder.vrSettings;
        var pass = holder.currentPass;
        boolean bow = BowTracker.isBow(stack) && player instanceof LocalPlayer local && holder.bowTracker.isActive(local);
        boolean telescope = TelescopeTracker.isTelescope(stack)
                && ((hand == InteractionHand.OFF_HAND && pass == RenderPass.SCOPEL)
                || (hand == InteractionHand.MAIN_HAND && pass == RenderPass.SCOPER));
        return VrHandVisibilityPolicy.allows(RenderPass.isFirstPerson(pass), pass == RenderPass.THIRD,
                pass == RenderPass.CAMERA, settings.showPlayerHands, settings.mixedRealityRenderHands,
                settings.shouldRenderSelf && settings.modelArmsMode == VRSettings.ModelArmsMode.COMPLETE,
                player.isInvisible(), bow || telescope);
    }
}
