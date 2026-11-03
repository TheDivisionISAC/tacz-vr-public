package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.client.control.VrGunHotbarRetreat;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.settings.VRSettings;

/** Moves the complete controller HUD and Vivecraft's shared touch surface by one room-space correction. */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler", remap = false)
abstract class GuiHandlerHotbarRetreatMixin {
    @Inject(method = "applyGUIModelView", at = @At("RETURN"), cancellable = true, require = 1)
    private static void taczvr$retreatGunHotbar(RenderPass currentPass, PoseStack poseStack,
                                                CallbackInfoReturnable<Vec3> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        if (!TaczVrClientConfig.ENABLE_VR_GUN_HOTBAR_RETREAT.get()
                || !TaczVrClient.vivecraft().isVrActive()
                || minecraft.screen != null
                || minecraft.player == null
                || holder.vrSettings.seated
                || holder.vrSettings.vrHudLockMode == VRSettings.HUDLock.HEAD
                || GuiHandler.GUI_RENDER_POS_ROOM == null
                || GuiHandler.GUI_RENDER_ROTATION_ROOM == null
                || !Float.isFinite(GuiHandler.GUI_SCALE_APPLIED)
                || GuiHandler.GUI_SCALE_APPLIED <= 0.0F) {
            return;
        }

        ItemStack stack = minecraft.player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return;
        }

        Vec3 localAxis = VrGunHotbarRetreat.localBackwardAxis(
                holder.vrSettings.vrHudLockMode == VRSettings.HUDLock.WRIST,
                holder.vrSettings.reverseHands
        );
        Vector3f roomAxis = GuiHandler.GUI_RENDER_ROTATION_ROOM.transformDirection(new Vector3f(
                (float) localAxis.x,
                (float) localAxis.y,
                (float) localAxis.z
        ));
        Vec3 roomOffset = VrGunHotbarRetreat.offset(asVec3(roomAxis));
        if (roomOffset.lengthSqr() <= 1.0E-12D) {
            return;
        }

        double distance = Math.sqrt(roomOffset.lengthSqr());
        double localDistance = distance / GuiHandler.GUI_SCALE_APPLIED;
        poseStack.translate(
                localAxis.x * localDistance,
                localAxis.y * localDistance,
                localAxis.z * localDistance
        );
        GuiHandler.GUI_RENDER_POS_ROOM.add(
                (float) roomOffset.x,
                (float) roomOffset.y,
                (float) roomOffset.z
        );
        callback.setReturnValue(VRPlayer.roomToWorldPos(
                GuiHandler.GUI_RENDER_POS_ROOM,
                holder.vrPlayer.vrdata_world_render
        ));
    }

    private static Vec3 asVec3(Vector3f vector) {
        return new Vec3(vector.x, vector.y, vector.z);
    }
}
