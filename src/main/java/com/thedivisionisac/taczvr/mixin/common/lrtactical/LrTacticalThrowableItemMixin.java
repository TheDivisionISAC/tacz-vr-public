package com.thedivisionisac.taczvr.mixin.common.lrtactical;

import com.thedivisionisac.taczvr.config.TaczVrServerConfig;
import com.thedivisionisac.taczvr.server.lrtactical.LrTacticalThrowMotion;
import com.thedivisionisac.taczvr.server.lrtactical.ServerLrTacticalThrowPoseStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Applies a single validated controller direction after LRTactical creates its entity and before it joins the world.
 */
@Pseudo
@Mixin(targets = "me.xjqsh.lrtactical.item.ThrowableItem", remap = false)
abstract class LrTacticalThrowableItemMixin {
    @Group(name = "taczvr$lrtAddThrowable", min = 1, max = 1)
    @ModifyArg(
            method = "onThrow",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
            ),
            index = 0,
            require = 0
    )
    private Entity taczvr$redirectThrowable(Entity entity) {
        return this.taczvr$applyValidatedDirection(entity);
    }

    @Group(name = "taczvr$lrtAddThrowable", min = 1, max = 1)
    @ModifyArg(
            method = "onThrow",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z",
                    remap = false
            ),
            index = 0,
            require = 0,
            remap = false
    )
    private Entity taczvr$redirectThrowableSrg(Entity entity) {
        return this.taczvr$applyValidatedDirection(entity);
    }

    @Unique
    private Entity taczvr$applyValidatedDirection(Entity entity) {
        if (!TaczVrServerConfig.ENABLE_LRTACTICAL_VR_THROW_DIRECTION.get()
                || !(entity instanceof Projectile projectile)
                || !(projectile.getOwner() instanceof ServerPlayer player)) {
            return entity;
        }
        ServerLrTacticalThrowPoseStore.consume(player).ifPresent(direction ->
                entity.setDeltaMovement(LrTacticalThrowMotion.redirect(
                        entity.getDeltaMovement(), direction.direction()
                ))
        );
        return entity;
    }
}
