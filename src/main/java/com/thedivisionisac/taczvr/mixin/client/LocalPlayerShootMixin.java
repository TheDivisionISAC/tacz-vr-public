package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.client.vr.VrShotPose;
import com.thedivisionisac.taczvr.client.vr.VrShotPoseTaskContext;
import com.thedivisionisac.taczvr.client.vr.VrMuzzlePoseCache;
import com.thedivisionisac.taczvr.client.vr.VrGunAimStabilization;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.thedivisionisac.taczvr.network.TaczVrNetwork;
import com.thedivisionisac.taczvr.network.message.C2SVrShotPose;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.network.message.ClientMessagePlayerShoot;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Captures on the main thread, binds the snapshot to TACZ's scheduled task, and sends it before TACZ's packet. */
@Mixin(value = LocalPlayerShoot.class, remap = false)
abstract class LocalPlayerShootMixin {
    @Unique
    private VrShotPose taczvr$capturedShotPose;

    @Inject(method = "doShoot", at = @At("HEAD"), require = 1)
    private void taczvr$captureShotPose(GunDisplayInstance display, IGun gun, ItemStack stack, GunData gunData,
                                        long delay, float chargeProgress, CallbackInfo callback) {
        if (!TaczVrClientConfig.ENABLE_VR_SHOT_DIRECTION.get()
                || !TaczVrClient.vivecraft().isVrActive()) {
            this.taczvr$capturedShotPose = null;
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        this.taczvr$capturedShotPose = TaczVrClient.vivecraft().getMainHandShotPose()
                .map(pose -> player == null ? pose : VrGunAimStabilization.stabilizeShotPose(player, stack, pose))
                .orElse(null);
    }

    @Redirect(
            method = "doShoot",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/ScheduledExecutorService;scheduleAtFixedRate(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            ),
            require = 1
    )
    private ScheduledFuture<?> taczvr$bindPoseToScheduledShot(ScheduledExecutorService executor, Runnable action,
                                                               long initialDelay, long period, TimeUnit unit) {
        VrShotPose snapshot = this.taczvr$capturedShotPose;
        this.taczvr$capturedShotPose = null;
        return executor.scheduleAtFixedRate(
                VrShotPoseTaskContext.bind(snapshot, action), initialDelay, period, unit
        );
    }

    @Redirect(
            method = "lambda$doShoot$2",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V"
            ),
            require = 1
    )
    private void taczvr$sendVrPoseBeforeTaczShoot(SimpleChannel channel, Object message) {
        VrShotPose shotPose = VrShotPoseTaskContext.current().orElse(null);
        if (shotPose != null && message instanceof ClientMessagePlayerShoot shootMessage) {
            ClientPacketListener listener = Minecraft.getInstance().getConnection();
            if (listener != null && TaczVrNetwork.isRemotePresent(listener.getConnection())) {
                long timestamp = ((ClientMessagePlayerShootAccessor) shootMessage).taczvr$getTimestamp();
                LocalPlayer player = Minecraft.getInstance().player;
                Vec3 muzzle = TaczVrClientConfig.ENABLE_VR_MUZZLE_ORIGIN.get() && player != null
                        ? VrMuzzlePoseCache.resolve(player.getMainHandItem(), shotPose).orElse(null)
                        : null;
                TaczVrNetwork.sendToServer(C2SVrShotPose.pose(timestamp, shotPose.direction(), muzzle));
            }
        }
        channel.sendToServer(message);
    }
}
