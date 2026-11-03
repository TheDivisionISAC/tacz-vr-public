package com.thedivisionisac.taczvr.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thedivisionisac.taczvr.client.vr.VrRotation;
import com.thedivisionisac.taczvr.client.vr.VrScopeAlignmentCapture;
import com.thedivisionisac.taczvr.client.vr.VrScopeLateDivisionRenderer;
import com.thedivisionisac.taczvr.client.vr.VrScopeMagnificationRuntime;
import com.thedivisionisac.taczvr.client.vr.VrScopeRenderRequest;
import com.thedivisionisac.taczvr.client.vr.VrScopePassOrder;
import com.thedivisionisac.taczvr.client.vr.VrScopeWorldView;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;
import org.vivecraft.mod_compat_vr.shaders.ShadersHelper;

import java.util.List;
import java.util.Optional;

/**
 * Optional Vivecraft 1.20.1-1.3.13 adapter, reached only through plugin-gated client Mixins. Borrows its native right
 * telescope pass/target but never mutates VRData.t0. A temporary getEye result feeds BOTH XRCamera and Vivecraft's
 * full-roll model-view/RVE consumers. SCOPER precedes the ordinary eyes; their first-pass bookkeeping is preserved.
 * ARCANA/AR/OptiFine remain excluded; optional Oculus 1.8.0 uses a separate verified per-pass adapter and final-eye
 * composition. Neither mode changes Vivecraft's ordinary first-eye update semantics.
 */
public final class VivecraftScopeBridge {
    private VivecraftScopeBridge() {
    }

    public static List<?> schedule(List<?> original, boolean includeNonRendered) {
        VrScopeMagnificationRuntime.observe(VrScopeMagnificationRuntime.Hook.SCHEDULE);
        if (includeNonRendered || original == null) return original;
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        long frame = holder.frameIndex;
        if (!VrScopeMagnificationRuntime.startFrame(frame)) {
            return VrScopeMagnificationRuntime.scheduled(frame) ? prepend(original) : original;
        }
        if (ModList.get().isLoaded("oculus")) OculusScopeBridge.clearFrame(frame);
        if (!VrScopeMagnificationRuntime.enabled()) return skipped(original, frame, "inactive");
        if (!VrScopeMagnificationRuntime.hooksReady()) return skipped(original, frame, "hooks_pending");
        if (VrScopePassOrder.beforeEyes(original, RenderPass.LEFT, RenderPass.RIGHT, RenderPass.SCOPER).isEmpty()
                || holder.vrPlayer == null || holder.vrPlayer.vrdata_world_render == null || holder.vrRenderer == null) {
            return skipped(original, frame, "pass_or_tracking_unavailable");
        }
        try {
            String unsupported = unsupportedEnvironment(frame);
            if (unsupported != null) return skipped(original, frame, unsupported);
            Optional<VrScopeAlignmentCapture.LiveScope> live = VrScopeAlignmentCapture.currentScope();
            if (live.isEmpty() || live.get().magnification() <= 1.0D) return skipped(original, frame, "no_magnified_scope");
            Optional<VrScopeRenderRequest> request = VrScopeAlignmentCapture.claimRenderRequest(frame, live.get());
            if (request.isEmpty()) return skipped(original, frame, "no_previous_frame_request");
            if (VrScopeMagnificationRuntime.isShaderFrame()) OculusScopeBridge.expectProjection(request.get());
            VRData tracking = holder.vrPlayer.vrdata_world_render;
            Optional<VrRotation> headRotation = VrRotation.validated(
                    tracking.hmd.getMatrix().getUnnormalizedRotation(new org.joml.Quaternionf()));
            if (headRotation.isEmpty()) return skipped(original, frame, "invalid_head_rotation");
            Optional<VrScopeWorldView> view = VrScopeWorldView.from(request.get(), tracking.hmd.getPosition(), headRotation.get());
            if (view.isPresent() && VrScopeMagnificationRuntime.prepare(frame, request.get(), live.get(),
                    holder.vrRenderer.telescopeFramebufferR, view.get())) {
                VrScopeMagnificationRuntime.scheduleDiagnostic(frame, "scheduled");
                return prepend(original);
            }
            return skipped(original, frame, "invalid_view_or_target");
        } catch (RuntimeException | LinkageError error) {
            VrScopeMagnificationRuntime.fail(error);
        }
        return original;
    }

    private static List<?> skipped(List<?> original, long frame, String reason) {
        VrScopeMagnificationRuntime.scheduleDiagnostic(frame, reason);
        return original;
    }

    private static List<?> prepend(List<?> original) {
        return VrScopePassOrder.<Object>beforeEyes(new java.util.ArrayList<>(original),
                RenderPass.LEFT, RenderPass.RIGHT, RenderPass.SCOPER).orElseGet(() -> new java.util.ArrayList<>(original));
    }

    private static String unsupportedEnvironment(long frame) {
        ModList mods = ModList.get();
        if (mods.isLoaded("taczexpands") || mods.isLoaded("acceleratedrendering")
                || mods.isLoaded("iris") || mods.isLoaded("optifine") || OptifineHelper.isOptifineLoaded()) {
            return "unsupported_renderer";
        }
        if (mods.isLoaded("oculus")) return OculusScopeBridge.prepareFrame(frame);
        return ShadersHelper.isShaderActive() ? "unsupported_renderer" : null;
    }

    public static Object eyePose(Object data, Object pass, Object original) {
        VrScopeMagnificationRuntime.observe(VrScopeMagnificationRuntime.Hook.EYE);
        if (pass != RenderPass.SCOPER || !VrScopeMagnificationRuntime.isScopePass()
                || !VrScopeMagnificationRuntime.enabled()
                || !(data instanceof VRData tracking)) return original;
        try {
            Optional<VrScopeWorldView> local = VrScopeMagnificationRuntime.worldView()
                    .inTrackingSpace(tracking.origin, tracking.rotation_radians, tracking.worldScale);
            if (local.isEmpty()) return original;
            VrScopeWorldView pose = local.get();
            Matrix4f matrix = new Matrix4f().rotation(pose.rotation().toQuaternion());
            Vector3f position = new Vector3f((float) pose.position().x, (float) pose.position().y, (float) pose.position().z);
            Vector3f direction = matrix.transformDirection(new Vector3f(0, 0, -1));
            VRData.VRDevicePose result = tracking.new VRDevicePose(tracking, matrix, position, direction);
            VrScopeMagnificationRuntime.cameraApplied();
            return result;
        } catch (RuntimeException | LinkageError error) {
            VrScopeMagnificationRuntime.fail(error);
            return original;
        }
    }

    public static void renderView(Object pass, boolean renderLevel, Runnable original) {
        VrScopeMagnificationRuntime.observe(VrScopeMagnificationRuntime.Hook.PASS);
        ClientDataHolderVR holder = ClientDataHolderVR.getInstance();
        long frame = holder.frameIndex;
        if (pass == RenderPass.SCOPER && VrScopeMagnificationRuntime.scheduled(frame)) {
            RenderTarget target = holder.vrRenderer.telescopeFramebufferR;
            boolean first = holder.isFirstPass;
            boolean success = false;
            holder.isFirstPass = false;
            try {
                if (!renderLevel || !VrScopeMagnificationRuntime.begin(frame, target)) return;
                VrScopeLateDivisionRenderer.withRestoredRenderState(original);
                success = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == target.frameBufferId
                        && (!VrScopeMagnificationRuntime.isShaderFrame() || OculusScopeBridge.scopeComplete());
            } finally {
                try {
                    // An upstream world-render exception can skip Vivecraft's tail. Restore its cached entity even
                    // when propagating that exception; do not treat an incomplete world texture as usable.
                    Minecraft minecraft = Minecraft.getInstance();
                    ((GameRendererExtension) minecraft.gameRenderer).vivecraft$restoreRVEPos(minecraft.getCameraEntity());
                } finally {
                    holder.isFirstPass = first;
                    VrScopeMagnificationRuntime.finish(success);
                }
            }
            return;
        }
        boolean first = holder.isFirstPass;
        boolean restoreFirst = pass == RenderPass.LEFT && VrScopeMagnificationRuntime.takeFirstEye(frame);
        if (restoreFirst) holder.isFirstPass = true;
        try {
            original.run();
        } finally {
            if (restoreFirst) holder.isFirstPass = first;
        }
    }
}
