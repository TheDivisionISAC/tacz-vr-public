package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.control.VrGunRadialActions;
import com.thedivisionisac.taczvr.client.control.VrGunRadialState;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces Vivecraft's radial widgets with TACZ actions only for an active gun-menu session. */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.gui.GuiRadial", remap = false)
abstract class GuiRadialMixin extends Screen {
    protected GuiRadialMixin() {
        super(Component.empty());
    }

    // GuiRadial overrides Screen.init: named in dev, SRG-named in a production Vivecraft jar.
    @Inject(method = {"init", "m_7856_"}, at = @At("RETURN"), require = 1)
    private void taczvr$buildGunMenu(CallbackInfo callback) {
        if (!VrGunRadialState.isGunMenuActive()) {
            return;
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        this.clearWidgets();
        this.taczvr$addButton("menu.taczvr.reload", centerX - 52, centerY - 84, 104, VrGunRadialActions::reload);
        this.taczvr$addButton("menu.taczvr.refit", centerX - 142, centerY - 24, 104, VrGunRadialActions::openRefit);
        this.taczvr$addButton("menu.taczvr.inspect", centerX + 38, centerY - 24, 104, VrGunRadialActions::inspect);
        this.taczvr$addButton("menu.taczvr.fire_select", centerX - 142, centerY + 36, 104,
                VrGunRadialActions::fireSelect);
        Component handMode = Component.translatable(
                "menu.taczvr.hand_mode",
                Component.translatable(VrGunRadialActions.currentHandDisplayMode().translationKey())
        );
        this.taczvr$addButton(handMode, centerX + 38, centerY + 36, 124,
                VrGunRadialActions::cycleHandDisplayMode);
        Component supportSnap = Component.translatable(
                "menu.taczvr.support_snap",
                Component.translatable(VrGunRadialActions.isSupportHandSnapEnabled()
                        ? "menu.taczvr.enabled"
                        : "menu.taczvr.disabled")
        );
        this.taczvr$addButton(supportSnap, centerX - 152, centerY + 86, 96,
                VrGunRadialActions::toggleSupportHandSnap);
        Component shieldSnap = Component.translatable(
                "menu.taczvr.shield_snap",
                Component.translatable(VrGunRadialActions.isShieldHandSnapEnabled()
                        ? "menu.taczvr.enabled"
                        : "menu.taczvr.disabled")
        );
        this.taczvr$addButton(shieldSnap, centerX - 48, centerY + 86, 96,
                VrGunRadialActions::toggleShieldHandSnap);
        this.taczvr$addButton("menu.taczvr.drop", centerX + 56, centerY + 86, 96, VrGunRadialActions::dropGun);
    }

    private void taczvr$addButton(String translationKey, int x, int y, int width, Runnable action) {
        this.taczvr$addButton(Component.translatable(translationKey), x, y, width, action);
    }

    private void taczvr$addButton(Component label, int x, int y, int width, Runnable action) {
        this.addRenderableWidget(Button.builder(label, button -> {
                    VrGunRadialState.markActionSelected();
                    action.run();
                })
                .bounds(x, y, width, 20)
                .build());
    }
}
