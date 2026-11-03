package com.thedivisionisac.taczvr.mixin.common;

import com.thedivisionisac.taczvr.config.TaczVrServerConfig;
import com.thedivisionisac.taczvr.server.shot.ServerShotExecutionContext;
import com.thedivisionisac.taczvr.server.shot.ServerShotPoseValidator;
import com.thedivisionisac.taczvr.server.shot.WorldBoundMuzzle;
import com.thedivisionisac.taczvr.server.shot.VrShotInaccuracyPolicy;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Copies the call-scoped muzzle into TACZ's script API instance and relocates each spawned kinetic bullet without
 * replacing TACZ spread, Lua, ammo, or burst scheduling. A world-bound lease expires the override on dimension
 * replacement; the native current-world origin remains intact when a delayed callback outlives that world.
 * Each pellet also rechecks its owner's current muzzle distances and obstruction before moving the native origin.
 */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
abstract class ModernKineticGunScriptAPIMixin {
    @Shadow
    private CommonGunIndex gunIndex;

    @Unique
    @Nullable
    private WorldBoundMuzzle taczvr$muzzlePosition;

    @Inject(method = "setShooter", at = @At("TAIL"), require = 1)
    private void taczvr$captureShotContext(LivingEntity shooter, CallbackInfo callback) {
        this.taczvr$muzzlePosition = ServerShotExecutionContext.current()
                .map(context -> context.muzzlePosition())
                .map(position -> new WorldBoundMuzzle(shooter.level(), position))
                .orElse(null);
    }

    @Redirect(
            method = "shootOnce(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/resource/pojo/data/gun/InaccuracyType;"
                            + "getInaccuracyType(Lnet/minecraft/world/entity/LivingEntity;)"
                            + "Lcom/tacz/guns/resource/pojo/data/gun/InaccuracyType;"
            ),
            require = 1
    )
    private InaccuracyType taczvr$useAimInaccuracyForVrShots(LivingEntity shooter) {
        return VrShotInaccuracyPolicy.resolve(
                shooter,
                this.gunIndex.getType(),
                TaczVrServerConfig.ENABLE_VR_PHYSICAL_GRIP_ACCURACY.get()
        );
    }

    // Level.addFreshEntity is named in dev and SRG-named in production; exactly one redirect must apply.
    @Group(name = "taczvr$addFreshEntity", min = 1, max = 1)
    @Redirect(
            method = "lambda$shootOnce$2",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
            ),
            require = 0
    )
    private boolean taczvr$spawnBulletAtMuzzle(Level level, Entity entity) {
        return this.taczvr$spawnBulletAtMuzzleImpl(level, entity);
    }

    @Group(name = "taczvr$addFreshEntity", min = 1, max = 1)
    @Redirect(
            method = "lambda$shootOnce$2",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z",
                    remap = false
            ),
            require = 0
    )
    private boolean taczvr$spawnBulletAtMuzzleSrg(Level level, Entity entity) {
        return this.taczvr$spawnBulletAtMuzzleImpl(level, entity);
    }

    @Unique
    private boolean taczvr$spawnBulletAtMuzzleImpl(Level level, Entity entity) {
        if (this.taczvr$muzzlePosition != null && entity instanceof EntityKineticBullet bullet) {
            this.taczvr$muzzlePosition.resolve(level, candidate -> {
                if (!(bullet.getOwner() instanceof ServerPlayer player) || player.level() != level) {
                    return Optional.empty();
                }
                return ServerShotPoseValidator.validateMuzzle(player, candidate);
            }).ifPresent(position -> {
                bullet.setPos(position);
                ((EntityKineticBulletAccessor) bullet).taczvr$setStartPos(position);
            });
        }
        return level.addFreshEntity(entity);
    }
}
