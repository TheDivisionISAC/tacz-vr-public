package com.thedivisionisac.taczvr.client.control;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.input.ZoomKey;
import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client-thread use+radial chord (right A+B in Vivecraft's Oculus defaults). Called once per radial press, before any
 * menu is opened, so a consumed zoom gesture cannot click the centered reload button on release. The held physical
 * use binding is required even with toggle-aim enabled. TACZ's native controller zoom action owns eligibility and
 * server synchronization; this adapter never edits attachment NBT, creates synthetic clicks or changes aiming.
 * A fixed/built-in scope consumes the eligible chord without a packet, avoiding no-op item synchronization and
 * a one-frame scope-image rejection, while still preventing the radial release from triggering reload.
 */
public final class VrScopeZoomAction {
    private static final boolean DEBUG = Boolean.getBoolean("taczvr.debugScopeMagnification");

    private VrScopeZoomAction() {
    }

    public static boolean tryCycle(Object controller) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!TaczVrClientConfig.ENABLE_VR_SCOPE_ZOOM_CHORD.get() || !TaczVrClient.vivecraft().isVrActive()
                || (controller != null && !"RIGHT".equals(controller.toString()))
                || minecraft.level == null || minecraft.screen != null || minecraft.getOverlay() != null
                || player == null || player.isSpectator() || !minecraft.options.keyUse.isDown()) return false;
        ItemStack item = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(item);
        if (gun == null) return false;
        ResourceLocation scope = gun.getAttachmentId(item, AttachmentType.SCOPE);
        boolean builtIn = DefaultAssets.isEmptyAttachmentId(scope);
        if (builtIn) scope = gun.getBuiltInAttachmentId(item, AttachmentType.SCOPE);
        if (DefaultAssets.isEmptyAttachmentId(scope)) return false;
        var optical = TimelessAPI.getClientAttachmentIndex(scope).filter(index -> index.isScope());
        if (optical.isEmpty() || !IClientPlayerGunOperator.fromLocalPlayer(player).isAim()) return false;
        // TACZ's native zoom mutates attachment data even for a one-step cycle; built-in optics always use step 0.
        if (builtIn || !VrScopeZoomPolicy.hasAlternatives(optical.get().getZoom(), optical.get().getViews())) {
            if (DEBUG) TaczVr.LOGGER.info("TACZVR_SCOPE_ZOOM action=fixed_scope_noop scope={}", scope);
            return true;
        }
        boolean consumed = ZoomKey.onZoomControllerPress(true);
        if (DEBUG && consumed) {
            TaczVr.LOGGER.info("TACZVR_SCOPE_ZOOM action=cycle scope={} previous_zoom={}", scope, gun.getAimingZoom(item));
        }
        return consumed;
    }
}
