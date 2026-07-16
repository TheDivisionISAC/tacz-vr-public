package com.thedivisionisac.taczvr.client.control;

import com.thedivisionisac.taczvr.client.render.VrGunHandVisibility;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.thedivisionisac.taczvr.config.VrHandDisplayMode;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Stateless adapters from radial buttons to TACZ's existing client gameplay API.
 */
public final class VrGunRadialActions {
    private VrGunRadialActions() {
    }

    public static void reload() {
        LocalPlayer player = getGunPlayer();
        if (player != null) {
            IClientPlayerGunOperator.fromLocalPlayer(player).reload();
        }
    }

    public static void inspect() {
        LocalPlayer player = getGunPlayer();
        if (player != null) {
            IClientPlayerGunOperator.fromLocalPlayer(player).inspect();
        }
    }

    public static void fireSelect() {
        LocalPlayer player = getGunPlayer();
        if (player != null) {
            IClientPlayerGunOperator.fromLocalPlayer(player).fireSelect();
        }
    }

    public static void openRefit() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = getGunPlayer();
        if (player == null || minecraft.screen != null) {
            return;
        }
        IGun gun = IGun.getIGunOrNull(player.getMainHandItem());
        if (gun != null && !gun.hasAttachmentLock(player.getMainHandItem())) {
            minecraft.setScreen(new GunRefitScreen());
        }
    }

    public static void dropGun() {
        LocalPlayer player = getGunPlayer();
        if (player != null) {
            player.drop(false);
        }
    }

    public static void cycleHandDisplayMode() {
        if (getGunPlayer() == null) {
            return;
        }
        VrHandDisplayMode next = currentHandDisplayMode().next();
        TaczVrClientConfig.VR_HAND_DISPLAY_MODE.set(next);
        VrGunHandVisibility.resetTransientState();
    }

    public static VrHandDisplayMode currentHandDisplayMode() {
        return TaczVrClientConfig.VR_HAND_DISPLAY_MODE.get();
    }

    public static void toggleSupportHandSnap() {
        if (getGunPlayer() == null) {
            return;
        }
        TaczVrClientConfig.ENABLE_VR_SUPPORT_HAND_SNAP.set(!isSupportHandSnapEnabled());
        VrGunHandVisibility.resetTransientState();
    }

    public static boolean isSupportHandSnapEnabled() {
        return TaczVrClientConfig.ENABLE_VR_SUPPORT_HAND_SNAP.get();
    }

    public static void toggleShieldHandSnap() {
        if (getGunPlayer() == null) {
            return;
        }
        TaczVrClientConfig.ENABLE_VR_SHIELD_HAND_SNAP.set(!isShieldHandSnapEnabled());
        VrGunHandVisibility.resetTransientState();
    }

    public static boolean isShieldHandSnapEnabled() {
        return TaczVrClientConfig.ENABLE_VR_SHIELD_HAND_SNAP.get();
    }

    private static LocalPlayer getGunPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || !IGun.mainHandHoldGun(player)) {
            return null;
        }
        return player;
    }
}
