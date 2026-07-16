package com.thedivisionisac.taczvr.client.vr;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Shares one bounded six-degree-of-freedom stabilization result between first-person rendering and immutable TACZ shot
 * snapshots. Direction and position filters release on deliberate motion and keep strict lag limits from tracking.
 */
public final class VrGunAimStabilization {
    private static final AdaptiveDirectionFilter DIRECTION_FILTER = new AdaptiveDirectionFilter();
    private static final AdaptivePositionFilter POSITION_FILTER = new AdaptivePositionFilter();
    private static final ThreadLocal<VrRenderPoseCorrection> RENDER_CORRECTION = new ThreadLocal<>();

    private VrGunAimStabilization() {
    }

    public static VrShotPose stabilizeShotPose(LocalPlayer player, ItemStack stack, VrShotPose rawPose) {
        return stabilize(player, stack, rawPose, System.nanoTime());
    }

    public static void stabilizeControllerPose(ItemStack stack, Matrix4f controllerPose, Matrix3f controllerNormal) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !TaczVrClientConfig.ENABLE_VR_AIM_STABILIZATION.get()) {
            reset();
            return;
        }

        Optional<VrShotPose> rawRenderPose = TaczVrClient.vivecraft().getMainHandRenderPose();
        if (rawRenderPose.isEmpty()) {
            reset();
            return;
        }
        VrShotPose raw = rawRenderPose.get();
        VrShotPose stabilized = stabilize(player, stack, raw, System.nanoTime());
        Matrix4f rawControllerPose = new Matrix4f(controllerPose);
        Matrix3f rawControllerNormal = new Matrix3f(controllerNormal);
        applyWorldPoseCorrection(controllerPose, controllerNormal, raw, stabilized);
        RENDER_CORRECTION.set(VrRenderPoseCorrection.between(
                rawControllerPose, rawControllerNormal, controllerPose, controllerNormal
        ));
    }

    public static void applyMainHandRenderCorrection(PoseStack poseStack) {
        VrRenderPoseCorrection correction = RENDER_CORRECTION.get();
        RENDER_CORRECTION.remove();
        if (correction != null) {
            correction.apply(poseStack);
        }
    }

    public static void discardMainHandRenderCorrection() {
        RENDER_CORRECTION.remove();
    }

    public static void reset() {
        DIRECTION_FILTER.reset();
        POSITION_FILTER.reset();
        discardMainHandRenderCorrection();
    }

    private static VrShotPose stabilize(LocalPlayer player, ItemStack stack, VrShotPose rawPose, long nowNanos) {
        if (!TaczVrClientConfig.ENABLE_VR_AIM_STABILIZATION.get()) {
            reset();
            return rawPose;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            reset();
            return rawPose;
        }

        ResourceLocation gunId = gun.getGunId(stack);
        boolean aiming = IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
        double strength = aiming
                ? TaczVrClientConfig.VR_ADS_STABILIZATION_STRENGTH.get()
                : TaczVrClientConfig.VR_HIP_STABILIZATION_STRENGTH.get();
        double maximumPositionOffset = aiming
                ? TaczVrClientConfig.VR_ADS_STABILIZATION_MAX_POSITION_OFFSET.get()
                : TaczVrClientConfig.VR_HIP_STABILIZATION_MAX_POSITION_OFFSET.get();
        Vec3 direction = DIRECTION_FILTER.update(
                gunId,
                rawPose.direction(),
                strength,
                TaczVrClientConfig.VR_STABILIZATION_RELEASE_SPEED.get(),
                nowNanos
        );
        Vec3 position = POSITION_FILTER.update(
                gunId,
                rawPose.position(),
                strength,
                maximumPositionOffset,
                TaczVrClientConfig.VR_STABILIZATION_POSITION_RELEASE_SPEED.get(),
                nowNanos
        );
        return new VrShotPose(position, direction);
    }

    private static void applyWorldPoseCorrection(Matrix4f controllerPose, Matrix3f controllerNormal,
                                                 VrShotPose raw, VrShotPose stabilized) {
        Quaternionf worldToView = new Quaternionf(
                Minecraft.getInstance().gameRenderer.getMainCamera().rotation()
        ).conjugate();
        Vector3f rawViewDirection = worldToView.transform(toVector(raw.direction())).normalize();
        Vector3f stabilizedViewDirection = worldToView.transform(toVector(stabilized.direction())).normalize();
        if (!rawViewDirection.isFinite() || !stabilizedViewDirection.isFinite()) {
            return;
        }

        Quaternionf correction = new Quaternionf().rotationTo(rawViewDirection, stabilizedViewDirection);
        Vector3f translation = controllerPose.getTranslation(new Vector3f()).add(
                worldToView.transform(toVector(stabilized.position().subtract(raw.position())))
        );
        Matrix4f basis = new Matrix4f(controllerPose).setTranslation(0.0F, 0.0F, 0.0F);
        controllerPose.set(new Matrix4f().rotation(correction).mul(basis)).setTranslation(translation);
        controllerNormal.set(new Matrix3f().rotation(correction).mul(controllerNormal));
    }

    private static Vector3f toVector(Vec3 direction) {
        return new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
    }
}
