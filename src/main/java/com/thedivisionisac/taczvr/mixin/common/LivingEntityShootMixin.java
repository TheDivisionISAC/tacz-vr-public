package com.thedivisionisac.taczvr.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thedivisionisac.taczvr.compat.VivecraftServerCompat;
import com.thedivisionisac.taczvr.compat.VrSprintShootPolicy;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Changes only TACZ 1.1.8-hotfix's server-side sprint timer read inside the final shoot eligibility check. A
 * Vivecraft-confirmed VR player may fire without leaving sprint; non-VR players and every other TACZ rejection path
 * retain upstream behavior.
 */
@Mixin(value = LivingEntityShoot.class, remap = false)
abstract class LivingEntityShootMixin {
    @Shadow
    @Final
    private LivingEntity shooter;

    @ModifyExpressionValue(
            method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)"
                    + "Lcom/tacz/guns/api/entity/ShootResult;",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/tacz/guns/entity/shooter/ShooterDataHolder;sprintTimeS:F"
            ),
            require = 1
    )
    private float taczvr$allowVrPlayerToShootWhileSprinting(float sprintTime) {
        return VrSprintShootPolicy.effectiveSprintTime(
                sprintTime,
                this.shooter instanceof Player player && VivecraftServerCompat.isVrPlayer(player)
        );
    }
}
