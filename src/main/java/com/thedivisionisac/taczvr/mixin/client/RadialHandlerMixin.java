package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.client.control.VrGunRadialActions;
import com.thedivisionisac.taczvr.client.control.VrGunRadialState;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Owns TACZ radial open/close semantics. LAST_PRESS is a Vivecraft internal contract cleared to prevent its repeat-last
 * action feature from stealing an empty-release reload.
 */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler", remap = false)
abstract class RadialHandlerMixin {
    @Shadow
    private static Vector2i LAST_PRESS;

    @Inject(method = "setOverlayShowing", at = @At("HEAD"), require = 1)
    private static void taczvr$selectGunMenu(boolean showing, @Coerce Object controller,
                                             CallbackInfoReturnable<Boolean> callback) {
        if (showing) {
            boolean gunMenu = taczvr$shouldShowGunMenu(controller);
            VrGunRadialState.beginGunMenu(gunMenu);
            if (gunMenu) {
                LAST_PRESS = null;
            }
            return;
        }
        if (VrGunRadialState.closeGunMenuWithoutSelection()
                && TaczVrClientConfig.RELOAD_ON_EMPTY_RADIAL_RELEASE.get()) {
            VrGunRadialActions.reload();
        }
    }

    @Unique
    private static boolean taczvr$shouldShowGunMenu(Object controller) {
        if (!TaczVrClientConfig.ENABLE_VR_GUN_RADIAL_MENU.get()
                || !TaczVrClient.vivecraft().isVrActive()
                || (controller != null && !"RIGHT".equals(controller.toString()))) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && IGun.mainHandHoldGun(player);
    }
}
