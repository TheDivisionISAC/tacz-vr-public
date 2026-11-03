package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.client.compat.lrtactical.LrTacticalVrItemSupport;
import com.thedivisionisac.taczvr.client.control.VrGunRadialState;
import com.thedivisionisac.taczvr.client.control.VrScopeZoomAction;
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
 * Owns TACZ radial open/close semantics. LAST_PRESS is a Vivecraft internal contract cleared so a centered short press
 * clicks the explicit reload button instead of repeating an older selection.
 * At the same 1.3.13 press boundary, a held use button can consume a scope zoom chord before opening the menu;
 * the original single-button radial and release behavior then cannot issue an accidental reload for that chord.
 */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler", remap = false)
abstract class RadialHandlerMixin {
    @Shadow
    private static Vector2i LAST_PRESS;

    @Inject(method = "setOverlayShowing", at = @At("HEAD"), cancellable = true, require = 1)
    private static void taczvr$selectGunMenu(boolean showing, @Coerce Object controller,
                                             CallbackInfoReturnable<Boolean> callback) {
        if (showing) {
            if (VrScopeZoomAction.tryCycle(controller)) {
                LAST_PRESS = null;
                VrGunRadialState.closeMenu();
                callback.setReturnValue(false);
                return;
            }
            VrGunRadialState.MenuKind menuKind = taczvr$selectMenu(controller);
            VrGunRadialState.beginMenu(menuKind);
            if (menuKind != VrGunRadialState.MenuKind.NONE) {
                LAST_PRESS = null;
            }
            return;
        }
        VrGunRadialState.closeMenu();
    }

    @Unique
    private static VrGunRadialState.MenuKind taczvr$selectMenu(Object controller) {
        if (!TaczVrClientConfig.ENABLE_VR_GUN_RADIAL_MENU.get()
                || !TaczVrClient.vivecraft().isVrActive()
                || (controller != null && !"RIGHT".equals(controller.toString()))) {
            return VrGunRadialState.MenuKind.NONE;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            return VrGunRadialState.MenuKind.NONE;
        }
        if (IGun.mainHandHoldGun(player)) {
            return VrGunRadialState.MenuKind.TACZ_GUN;
        }
        return LrTacticalVrItemSupport.isRenderSupported(player.getMainHandItem())
                ? VrGunRadialState.MenuKind.LRTACTICAL_ITEM
                : VrGunRadialState.MenuKind.NONE;
    }
}
