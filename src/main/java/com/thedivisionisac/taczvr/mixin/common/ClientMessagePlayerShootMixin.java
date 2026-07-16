package com.thedivisionisac.taczvr.mixin.common;

import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.server.shot.ServerShotPoseStore;
import com.thedivisionisac.taczvr.server.shot.ServerShotExecutionContext;
import com.thedivisionisac.taczvr.server.shot.ValidatedShotPose;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.network.message.ClientMessagePlayerShoot;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.function.Supplier;

/** Replaces only TACZ's pitch/yaw suppliers and exposes the validated muzzle through a call-scoped context. */
@Mixin(value = ClientMessagePlayerShoot.class, remap = false)
abstract class ClientMessagePlayerShootMixin {
    @Redirect(
            method = "lambda$handle$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/api/entity/IGunOperator;shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JF)Lcom/tacz/guns/api/entity/ShootResult;"
            ),
            require = 1
    )
    private static ShootResult taczvr$useValidatedVrDirection(IGunOperator operator, Supplier<Float> originalPitch,
                                                              Supplier<Float> originalYaw, long timestamp,
                                                              float chargeProgress) {
        if (operator instanceof ServerPlayer player) {
            Optional<ValidatedShotPose> pose = ServerShotPoseStore.consume(player, timestamp);
            if (pose.isPresent()) {
                ValidatedShotPose validated = pose.get();
                TaczVr.LOGGER.debug("Using VR shot pose for {} at TACZ timestamp {} (muzzle: {}).",
                        player.getScoreboardName(), timestamp, validated.hasMuzzlePosition());
                return ServerShotExecutionContext.runWith(validated, () -> operator.shoot(
                        validated::pitchDegrees, validated::yawDegrees, timestamp, chargeProgress
                ));
            }
        }
        return operator.shoot(originalPitch, originalYaw, timestamp, chargeProgress);
    }
}
