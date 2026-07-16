package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderHandEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents SimpleBedrockModel's Forge hand event from rendering a second TACZ gun beside Vivecraft's item pass. */
@Pseudo
@Mixin(targets = "com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler", remap = false)
abstract class FirstPersonRenderHandlerMixin {
    @Inject(
            method = "onRenderHand(Lnet/minecraftforge/client/event/RenderHandEvent;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private static void taczvr$deferTaczGunToVivecraft(RenderHandEvent event, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (TaczVrClientConfig.ENABLE_VR_GUN_RENDERING.get()
                && TaczVrClient.vivecraft().isVrActive()
                && minecraft.player != null
                && IGun.getIGunOrNull(minecraft.player.getMainHandItem()) != null) {
            callback.cancel();
        }
    }
}
