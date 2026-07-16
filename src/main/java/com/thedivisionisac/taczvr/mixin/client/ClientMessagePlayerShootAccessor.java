package com.thedivisionisac.taczvr.mixin.client;

import com.tacz.guns.network.message.ClientMessagePlayerShoot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads TACZ's private timestamp so the addon packet can use the same correlation key. */
@Mixin(value = ClientMessagePlayerShoot.class, remap = false)
public interface ClientMessagePlayerShootAccessor {
    @Accessor("timestamp")
    long taczvr$getTimestamp();
}
