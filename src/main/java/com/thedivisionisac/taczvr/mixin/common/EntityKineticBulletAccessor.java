package com.thedivisionisac.taczvr.mixin.common;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Updates TACZ's private damage-falloff origin when the physical bullet position moves to the muzzle. */
@Mixin(value = EntityKineticBullet.class, remap = false)
public interface EntityKineticBulletAccessor {
    @Accessor("startPos")
    void taczvr$setStartPos(Vec3 startPos);
}
