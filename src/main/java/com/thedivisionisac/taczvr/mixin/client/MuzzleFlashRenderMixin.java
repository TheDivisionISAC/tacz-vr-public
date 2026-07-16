package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.resource.GunDisplayInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client_vr.ClientDataHolderVR;

/**
 * Prevents TACZ's deferred muzzle-flash matrix from leaking between stereo eye passes.
 */
@Mixin(value = MuzzleFlashRender.class, remap = false)
public abstract class MuzzleFlashRenderMixin {
    @Shadow
    private static boolean muzzleFlashStartMark;

    @Inject(method = "renderMuzzleFlash", at = @At("HEAD"), require = 1)
    private static void taczvr$capturePoseForCurrentEye(GunDisplayInstance display,
                                                         PoseStack poseStack,
                                                         BedrockModel bedrockModel,
                                                         long time,
                                                         CallbackInfo ci) {
        if (!TaczVrClientConfig.CORRECT_VR_MUZZLE_FLASH_PER_EYE.get()
                || !TaczVrClient.vivecraft().isVrActive()) {
            return;
        }

        RenderPass currentPass = ClientDataHolderVR.getInstance().currentPass;
        if (RenderPass.isFirstPerson(currentPass)) {
            muzzleFlashStartMark = true;
        }
    }
}
