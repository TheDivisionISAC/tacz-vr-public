package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.render.TaczArmRenderScope;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents Vivecraft's PlayerModel mixin from applying live controller rotations to a skin arm that TACZ has already
 * positioned through its gun animation. The override exists only inside {@link TaczArmRenderScope}.
 */
@Pseudo
@Mixin(targets = "org.vivecraft.client.ClientVRPlayers", remap = false)
abstract class ClientVrPlayersTaczArmMixin {
    @Inject(
            method = "isVRPlayer(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void taczvr$hideTrackedPoseFromTaczArm(Entity entity,
                                                    CallbackInfoReturnable<Boolean> callback) {
        if (TaczArmRenderScope.isActive() && entity instanceof LocalPlayer) {
            callback.setReturnValue(false);
        }
    }
}
