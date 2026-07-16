package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.tacz.guns.client.renderer.entity.EntityBulletRenderer;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import org.joml.Vector3f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Decouples VR tracers from TACZ's camera-relative legacy muzzle offset after bullets gain a physical muzzle origin.
 * The synthetic lambda target is version-sensitive.
 */
@Mixin(value = EntityBulletRenderer.class, remap = false)
abstract class EntityBulletRendererMixin {
    @Redirect(
            method = "lambda$renderTracerAmmo$1",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/tacz/guns/client/renderer/item/GunItemRendererWrapper;muzzleRenderOffset:Lorg/joml/Vector3f;",
                    opcode = Opcodes.GETSTATIC
            ),
            require = 1
    )
    private Vector3f taczvr$removeLegacyTracerMuzzleOffset() {
        if (TaczVrClientConfig.DECOUPLE_VR_TRACER_OFFSET.get() && TaczVrClient.vivecraft().isVrActive()) {
            return new Vector3f(
                    TaczVrClientConfig.VR_TRACER_HORIZONTAL_CORRECTION.get().floatValue(),
                    TaczVrClientConfig.VR_TRACER_VERTICAL_CORRECTION.get().floatValue(),
                    0.0F
            );
        }
        return GunItemRendererWrapper.muzzleRenderOffset;
    }
}
