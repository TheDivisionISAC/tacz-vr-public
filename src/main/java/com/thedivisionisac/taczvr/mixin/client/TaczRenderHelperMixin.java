package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.client.render.TaczArmRenderScope;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * Keeps TACZ's deferred first-person skin arms in the gun animation's pose space. Vivecraft replaces the normal
 * player renderer for tracked players, but that renderer applies controller-driven body rotations which do not
 * belong inside TACZ's right/left-hand model groups.
 */
@Mixin(value = RenderHelper.class, remap = false)
abstract class TaczRenderHelperMixin {
    @Group(name = "taczvr$firstPersonArmRenderer", min = 1, max = 1)
    @Redirect(
            method = "renderFirstPersonArm",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;"
            ),
            require = 0
    )
    private static EntityRenderer<?> taczvr$useVanillaArmRenderer(EntityRenderDispatcher dispatcher, Entity entity) {
        return taczvr$resolveArmRenderer(dispatcher, entity);
    }

    @Group(name = "taczvr$firstPersonArmRenderer", min = 1, max = 1)
    @Redirect(
            method = "renderFirstPersonArm",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;m_114382_(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
                    remap = false
            ),
            require = 0
    )
    private static EntityRenderer<?> taczvr$useVanillaArmRendererSrg(EntityRenderDispatcher dispatcher,
                                                                      Entity entity) {
        return taczvr$resolveArmRenderer(dispatcher, entity);
    }

    @Unique
    private static EntityRenderer<?> taczvr$resolveArmRenderer(EntityRenderDispatcher dispatcher, Entity entity) {
        if (entity instanceof LocalPlayer player
                && TaczVrClient.vivecraft().isVrActive()) {
            Map<String, EntityRenderer<? extends Player>> skinMap = dispatcher.getSkinMap();
            EntityRenderer<? extends Player> renderer = skinMap.get(player.getModelName());
            if (renderer == null) {
                renderer = skinMap.get("default");
            }
            if (renderer != null) {
                return renderer;
            }
        }

        return dispatcher.getRenderer(entity);
    }

    @Group(name = "taczvr$rightSkinArm", min = 1, max = 1)
    @Redirect(
            method = "renderFirstPersonArm",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/player/PlayerRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V"
            ),
            require = 0
    )
    private static void taczvr$renderRightSkinArm(PlayerRenderer renderer, PoseStack poseStack,
                                                   MultiBufferSource buffer, int light,
                                                   AbstractClientPlayer player) {
        taczvr$renderSkinArm(() -> renderer.renderRightHand(poseStack, buffer, light, player));
    }

    @Group(name = "taczvr$rightSkinArm", min = 1, max = 1)
    @Redirect(
            method = "renderFirstPersonArm",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/player/PlayerRenderer;m_117770_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V",
                    remap = false
            ),
            require = 0
    )
    private static void taczvr$renderRightSkinArmSrg(PlayerRenderer renderer, PoseStack poseStack,
                                                      MultiBufferSource buffer, int light,
                                                      AbstractClientPlayer player) {
        taczvr$renderSkinArm(() -> renderer.renderRightHand(poseStack, buffer, light, player));
    }

    @Group(name = "taczvr$leftSkinArm", min = 1, max = 1)
    @Redirect(
            method = "renderFirstPersonArm",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/player/PlayerRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V"
            ),
            require = 0
    )
    private static void taczvr$renderLeftSkinArm(PlayerRenderer renderer, PoseStack poseStack,
                                                  MultiBufferSource buffer, int light,
                                                  AbstractClientPlayer player) {
        taczvr$renderSkinArm(() -> renderer.renderLeftHand(poseStack, buffer, light, player));
    }

    @Group(name = "taczvr$leftSkinArm", min = 1, max = 1)
    @Redirect(
            method = "renderFirstPersonArm",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/player/PlayerRenderer;m_117813_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V",
                    remap = false
            ),
            require = 0
    )
    private static void taczvr$renderLeftSkinArmSrg(PlayerRenderer renderer, PoseStack poseStack,
                                                     MultiBufferSource buffer, int light,
                                                     AbstractClientPlayer player) {
        taczvr$renderSkinArm(() -> renderer.renderLeftHand(poseStack, buffer, light, player));
    }

    @Unique
    private static void taczvr$renderSkinArm(Runnable renderCall) {
        if (TaczVrClient.vivecraft().isVrActive()) {
            TaczArmRenderScope.run(renderCall);
        } else {
            renderCall.run();
        }
    }
}
