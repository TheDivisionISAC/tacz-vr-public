package com.thedivisionisac.taczvr.mixin.client;

import com.thedivisionisac.taczvr.TaczVr;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.client.render.VrGunHandPoseCache;
import com.thedivisionisac.taczvr.client.vr.VrShotPose;
import com.thedivisionisac.taczvr.client.vr.VrShotPoseTaskContext;
import com.thedivisionisac.taczvr.client.vr.VrMuzzlePoseCache;
import com.thedivisionisac.taczvr.client.vr.VrGunAimStabilization;
import com.thedivisionisac.taczvr.compat.VrSprintShootPolicy;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.thedivisionisac.taczvr.network.TaczVrNetwork;
import com.thedivisionisac.taczvr.network.message.C2SVrShotPose;
import com.tacz.guns.api.entity.IGunOperator;
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

/**
 * Pins TACZ's doShoot/scheduled-send hooks to the supported version. Captures pose, muzzle and grip with their source
 * on the client thread, then dispatches the addon/original pair together on that thread. A replaced session drops
 * obsolete work. A synchronized replacement with the same slot/item/gun key is freshly sampled at dispatch;
 * a different weapon retains the original fallback. No asynchronous item or VR API reads.
 */
@Mixin(value = LocalPlayerShoot.class, remap = false)
abstract class LocalPlayerShootMixin {
    @Unique
    private VrShotPoseTaskContext.Snapshot taczvr$capturedShot;

    @Redirect(
            method = "shoot()Lcom/tacz/guns/api/entity/ShootResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/api/entity/IGunOperator;getSynSprintTime()F"
            ),
            require = 1
    )
    private float taczvr$allowActiveVrPlayerToShootWhileSprinting(IGunOperator operator) {
        return VrSprintShootPolicy.effectiveSprintTime(
                operator.getSynSprintTime(),
                TaczVrClient.vivecraft().isVrActive()
        );
    }

    @Inject(method = "doShoot", at = @At("HEAD"), require = 1)
    private void taczvr$captureShotPose(GunDisplayInstance display, IGun gun, ItemStack stack, GunData gunData,
                                        long delay, float chargeProgress, CallbackInfo callback) {
        this.taczvr$capturedShot = null;
        VrShotPoseTaskContext.Source source = taczvr$currentSource();
        if (source != null && source.heldItem() == stack) {
            this.taczvr$capturedShot = taczvr$snapshotForSource(source);
        }
    }

    @Unique
    private static VrShotPoseTaskContext.Snapshot taczvr$snapshotForSource(VrShotPoseTaskContext.Source source) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener listener = minecraft.getConnection();
        LocalPlayer player = minecraft.player;
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (!TaczVrClientConfig.ENABLE_VR_SHOT_DIRECTION.get()
                || !TaczVrClient.vivecraft().isVrActive()
                || source == null || source.heldItem() != stack || listener == null
                || source.connection() != listener.getConnection() || source.world() != minecraft.level
                || source.player() != player
                || !TaczVrNetwork.isRemotePresent(listener.getConnection())) {
            return null;
        }
        VrShotPose pose = TaczVrClient.vivecraft().getMainHandShotPose()
                .map(raw -> VrGunAimStabilization.stabilizeShotPose(player, stack, raw))
                .orElse(null);
        if (pose != null) {
            Vec3 muzzle = TaczVrClientConfig.ENABLE_VR_MUZZLE_ORIGIN.get()
                    ? VrMuzzlePoseCache.resolve(stack, pose).orElse(null)
                    : null;
            return new VrShotPoseTaskContext.Snapshot(
                    pose, muzzle, VrGunHandPoseCache.isSupportHandSnapped(stack), source
            );
        }
        return null;
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
        VrShotPoseTaskContext.Snapshot snapshot = this.taczvr$capturedShot;
        this.taczvr$capturedShot = null;
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
        VrShotPoseTaskContext.Snapshot snapshot = VrShotPoseTaskContext.current().orElse(null);
        if (snapshot == null || !(message instanceof ClientMessagePlayerShoot shootMessage)) {
            channel.sendToServer(message);
            return;
        }
        long timestamp = ((ClientMessagePlayerShootAccessor) shootMessage).taczvr$getTimestamp();
        VrShotPoseTaskContext.dispatch(
                snapshot,
                Minecraft.getInstance()::execute,
                () -> taczvr$currentSource(),
                current -> {
                    VrShotPoseTaskContext.Snapshot fresh = taczvr$snapshotForSource(current);
                    TaczVr.LOGGER.debug("VR shot pose recaptured after held-stack synchronization at TACZ timestamp {} (ready: {}).",
                            timestamp, fresh != null);
                    return fresh;
                },
                captured -> TaczVrNetwork.sendToServer(C2SVrShotPose.pose(
                        timestamp, captured.pose().direction(), captured.muzzlePosition(), captured.supportHandSnapped()
                )),
                () -> channel.sendToServer(message)
        );
    }

    @Unique
    private static VrShotPoseTaskContext.Source taczvr$currentSource() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener listener = minecraft.getConnection();
        LocalPlayer player = minecraft.player;
        if (listener == null || minecraft.level == null || player == null) return null;
        ItemStack stack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        var gunId = gun == null ? null : gun.getGunId(stack);
        VrShotPoseTaskContext.WeaponKey key = gunId == null ? null
                : new VrShotPoseTaskContext.WeaponKey(player.getInventory().selected, stack.getItem(), gunId);
        return new VrShotPoseTaskContext.Source(
                listener.getConnection(), minecraft.level, player, stack, key);
    }
}
