package com.thedivisionisac.taczvr.mixin.common;

import com.thedivisionisac.taczvr.server.shot.ServerShotExecutionContext;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Copies the call-scoped muzzle into TACZ's script API instance and relocates each spawned kinetic bullet without
 * replacing TACZ spread, Lua, ammo, or burst scheduling.
 */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
abstract class ModernKineticGunScriptAPIMixin {
    @Unique
    @Nullable
    private Vec3 taczvr$muzzlePosition;

    @Inject(method = "setShooter", at = @At("TAIL"), require = 1)
    private void taczvr$captureShotContext(LivingEntity shooter, CallbackInfo callback) {
        this.taczvr$muzzlePosition = ServerShotExecutionContext.current()
                .map(context -> context.muzzlePosition())
                .orElse(null);
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
            bullet.setPos(this.taczvr$muzzlePosition);
            ((EntityKineticBulletAccessor) bullet).taczvr$setStartPos(this.taczvr$muzzlePosition);
        }
        return level.addFreshEntity(entity);
    }
}
