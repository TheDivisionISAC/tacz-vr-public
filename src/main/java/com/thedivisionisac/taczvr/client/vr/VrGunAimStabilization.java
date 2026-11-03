package com.thedivisionisac.taczvr.client.vr;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.thedivisionisac.taczvr.config.VrStabilizationLevel;
import com.thedivisionisac.taczvr.config.VrMagnificationStabilization;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
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
import java.util.concurrent.TimeUnit;

/**
 * Shares one bounded six-degree-of-freedom stabilization result between first-person rendering and immutable TACZ shot
 * snapshots. Direction and position filters release on deliberate motion and keep strict lag limits from tracking.
 * Selected optical magnification optionally scales the common profile after the user's overall slider. It does not
 * depend on aperture progress, so opening/closing the lens cannot continually reset the filter source. Zoom changes
 * naturally invalidate the profile-keyed render/shot cache instead of reusing a previous strength's shot snapshot.
 */
public final class VrGunAimStabilization {
    private static final long MAXIMUM_SHOT_CACHE_AGE_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);
    private static final VrAimStabilizationState STATE = new VrAimStabilizationState();
    private static final VrRelativeRenderStabilizationState RELATIVE_RENDER_STATE =
            new VrRelativeRenderStabilizationState();
    private static final VrAimStabilizationDiagnostics DIAGNOSTICS = new VrAimStabilizationDiagnostics();
    private static final VrFullPoseDiagnostics FULL_POSE_DIAGNOSTICS = new VrFullPoseDiagnostics();
    private static final ThreadLocal<VrRenderPoseCorrection> RENDER_CORRECTION = new ThreadLocal<>();

    private VrGunAimStabilization() {
    }

    public static VrShotPose stabilizeShotPose(LocalPlayer player, ItemStack stack, VrShotPose rawPose) {
        StabilizationProfile profile = profile(player, stack);
        if (profile == null) {
            reset();
            return rawPose;
        }
        return STATE.resolveShotPose(profile, rawPose, System.nanoTime(), MAXIMUM_SHOT_CACHE_AGE_NANOS);
    }

    public static void stabilizeControllerPose(ItemStack stack, Matrix4f controllerPose, Matrix3f controllerNormal) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !TaczVrClientConfig.ENABLE_VR_AIM_STABILIZATION.get()) {
            reset();
            return;
        }

        boolean viewRelativeStabilization = TaczVrClientConfig.ENABLE_VR_VIEW_RELATIVE_STABILIZATION.get();
        Optional<VrRenderTrackingSnapshot> tracking = viewRelativeStabilization || FULL_POSE_DIAGNOSTICS.isEnabled()
                ? TaczVrClient.vivecraft().getRenderTrackingSnapshot()
                : Optional.empty();
        Optional<VrShotPose> rawRenderPose = tracking.map(VrRenderTrackingSnapshot::handPose);
        if (rawRenderPose.isEmpty()) {
            rawRenderPose = TaczVrClient.vivecraft().getMainHandRenderPose();
        }
        if (rawRenderPose.isEmpty()) {
            reset();
            return;
        }
        StabilizationProfile profile = profile(player, stack);
        if (profile == null) {
            reset();
            return;
        }
        VrShotPose raw = rawRenderPose.get();
        long frameIndex = TaczVrClient.vivecraft().getRenderFrameIndex().orElse(Long.MIN_VALUE);
        long nowNanos = System.nanoTime();
        VrShotPose stabilized = STATE.updateRenderPose(
                profile,
                frameIndex,
                raw,
                profile.strength(),
                profile.maximumPositionOffset(),
                profile.directionReleaseSpeed(),
                profile.positionReleaseSpeed(),
                nowNanos
        );
        DIAGNOSTICS.record(frameIndex, profile.gunId(), profile.aiming(), raw, stabilized, nowNanos);
        Matrix4f rawControllerPose = new Matrix4f(controllerPose);
        Matrix3f rawControllerNormal = new Matrix3f(controllerNormal);
        if (viewRelativeStabilization && tracking.isPresent()) {
            VrRelativeRenderPose rawRelativePose = VrRelativeRenderPose.from(tracking.get());
            VrRelativeRenderPose stabilizedAimPose = VrRelativeRenderPose.fromStabilizedAim(
                    tracking.get(), stabilized
            );
            VrRelativeRenderPose relativePose = RELATIVE_RENDER_STATE.update(
                    profile,
                    frameIndex,
                    stabilizedAimPose,
                    profile.strength(),
                    profile.maximumViewRotationOffset(),
                    profile.maximumViewPositionOffset(),
                    profile.directionReleaseSpeed(),
                    profile.positionReleaseSpeed(),
                    nowNanos
            );
            VrRelativeRenderPoseCorrection.apply(
                    controllerPose, controllerNormal, rawRelativePose, relativePose
            );
        } else {
            RELATIVE_RENDER_STATE.reset();
            applyWorldPoseCorrection(controllerPose, controllerNormal, raw, stabilized);
        }
        if (tracking.isPresent()) {
            FULL_POSE_DIAGNOSTICS.record(
                    frameIndex,
                    profile.gunId(),
                    profile.aiming(),
                    TaczVrClient.vivecraft().getRenderPassName(),
                    tracking.get(),
                    rawControllerPose,
                    controllerPose,
                    nowNanos
            );
        }
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
        STATE.reset();
        RELATIVE_RENDER_STATE.reset();
        DIAGNOSTICS.reset();
        FULL_POSE_DIAGNOSTICS.reset();
        discardMainHandRenderCorrection();
    }

    private static StabilizationProfile profile(LocalPlayer player, ItemStack stack) {
        if (!TaczVrClientConfig.ENABLE_VR_AIM_STABILIZATION.get()) {
            return null;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return null;
        }

        ResourceLocation gunId = gun.getGunId(stack);
        boolean aiming = IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
        double validatedStrength = aiming
                ? TaczVrClientConfig.VR_ADS_STABILIZATION_STRENGTH.get()
                : TaczVrClientConfig.VR_HIP_STABILIZATION_STRENGTH.get();
        double strength = VrStabilizationLevel.effectiveStrength(
                validatedStrength,
                TaczVrClientConfig.VR_STABILIZATION_LEVEL.get()
        );
        boolean scaleWithZoom = TaczVrClientConfig.ENABLE_VR_MAGNIFICATION_SCALED_STABILIZATION.get();
        strength = VrMagnificationStabilization.effectiveStrength(
                strength, scaleWithZoom ? opticalMagnification(gun, stack) : 1.0D, scaleWithZoom
        );
        double maximumPositionOffset = aiming
                ? TaczVrClientConfig.VR_ADS_STABILIZATION_MAX_POSITION_OFFSET.get()
                : TaczVrClientConfig.VR_HIP_STABILIZATION_MAX_POSITION_OFFSET.get();
        double maximumViewRotationOffset = aiming
                ? TaczVrClientConfig.VR_ADS_VIEW_ROTATION_MAX_OFFSET.get()
                : TaczVrClientConfig.VR_HIP_VIEW_ROTATION_MAX_OFFSET.get();
        double maximumViewPositionOffset = aiming
                ? TaczVrClientConfig.VR_ADS_VIEW_POSITION_MAX_OFFSET.get()
                : TaczVrClientConfig.VR_HIP_VIEW_POSITION_MAX_OFFSET.get();
        return new StabilizationProfile(
                gunId,
                strength,
                maximumPositionOffset,
                maximumViewRotationOffset,
                maximumViewPositionOffset,
                TaczVrClientConfig.VR_STABILIZATION_RELEASE_SPEED.get(),
                TaczVrClientConfig.VR_STABILIZATION_POSITION_RELEASE_SPEED.get(),
                aiming
        );
    }

    private static double opticalMagnification(IGun gun, ItemStack stack) {
        ResourceLocation scope = gun.getAttachmentId(stack, AttachmentType.SCOPE);
        if (DefaultAssets.isEmptyAttachmentId(scope)) scope = gun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
        if (DefaultAssets.isEmptyAttachmentId(scope)
                || TimelessAPI.getClientAttachmentIndex(scope).filter(index -> index.isScope()).isEmpty()) return 1.0D;
        return gun.getAimingZoom(stack);
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

    /** All settings that define one continuous filter source and its matching shot snapshot. */
    private record StabilizationProfile(ResourceLocation gunId, double strength, double maximumPositionOffset,
                                        double maximumViewRotationOffset, double maximumViewPositionOffset,
                                        double directionReleaseSpeed, double positionReleaseSpeed, boolean aiming) {
    }
}
