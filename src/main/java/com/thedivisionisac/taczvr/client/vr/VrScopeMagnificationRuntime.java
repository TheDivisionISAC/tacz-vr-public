package com.thedivisionisac.taczvr.client.vr;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.EnumSet;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Client render-thread owner of the optional magnification transaction and per-eye image planes. It holds no
 * Vivecraft types, so lifecycle cleanup is safe with that optional mod absent. The native compatibility adapter
 * schedules and renders; this owner publishes only a complete current-frame image and validates every composition.
 * Unsupported hooks never arm a world pass; unexpected adapter failures latch off until reload or explicit reset.
 */
public final class VrScopeMagnificationRuntime {
    public enum Hook { SCHEDULE, PASS, EYE, PROJECTION, COMPOSITE, OVERLAY }
    private static final EnumSet<Hook> OBSERVED = EnumSet.noneOf(Hook.class);
    private static final VrScopeMagnificationState STATE = new VrScopeMagnificationState();
    private static final VrScopeCompatibilityDiagnostics COMPATIBILITY = new VrScopeCompatibilityDiagnostics();
    private static final LensFrame[] LENSES = new LensFrame[2];
    private static RenderTarget target;
    private static int texture = -1;
    private static VrScopeWorldView view;
    private static boolean ownedScopeDraw;
    private static BooleanSupplier shaderFinalEyeReady;
    private static Runnable shaderReset;
    private static int targetWidth;
    private static int targetHeight;
    private static boolean failed;
    private static long lastSchedule = Long.MIN_VALUE;
    private static long lastDiagnostic;
    private static long lastScheduleDiagnostic;
    private static final long[] LAST_COMPOSITE_DIAGNOSTIC = new long[2];
    private static final boolean DEBUG = Boolean.getBoolean("taczvr.debugScopeMagnification");

    private VrScopeMagnificationRuntime() {
    }

    public static void observe(Hook hook) {
        if (RenderSystem.isOnRenderThread()) OBSERVED.add(hook);
    }
    public static boolean hooksReady() { return OBSERVED.size() == Hook.values().length; }

    /** Actionable compatibility notices are on by default; detailed frame evidence remains opt-in and bounded. */
    public static void scheduleDiagnostic(long frame, String reason) {
        long now = System.nanoTime();
        String notice = COMPATIBILITY.notice(reason, now);
        if (notice != null) {
            TaczVr.LOGGER.warn("TACZVR_SCOPE_MAGNIFICATION unavailable reason={} hint={}", reason, notice);
        }
        if (!DEBUG || !TaczVrClientConfig.ENABLE_VR_SCOPE_MAGNIFICATION.get()) return;
        if (lastScheduleDiagnostic != 0 && now - lastScheduleDiagnostic < 1_000_000_000L) return;
        lastScheduleDiagnostic = now;
        EnumSet<Hook> missing = EnumSet.allOf(Hook.class);
        missing.removeAll(OBSERVED);
        TaczVr.LOGGER.info("TACZVR_SCOPE_MAGNIFICATION stage=schedule frame={} reason={} missing_hooks={}",
                frame, reason, missing);
    }

    public static boolean enabled() {
        Minecraft minecraft = Minecraft.getInstance();
        return !failed && TaczVrClientConfig.ENABLE_VR_SCOPE_MAGNIFICATION.get()
                && TaczVrClientConfig.ENABLE_VR_PHYSICAL_SCOPE_GATE.get()
                && TaczVrClient.vivecraft().isVrActive() && minecraft.level != null && minecraft.player != null
                && minecraft.screen == null && !minecraft.player.isSpectator();
    }

    /** Returns false for repeated scheduler queries without consuming newly captured facts from the same frame. */
    public static boolean startFrame(long frame) {
        if (frame == lastSchedule) return false;
        lastSchedule = frame;
        STATE.reset();
        target = null;
        texture = -1;
        view = null;
        ownedScopeDraw = false;
        shaderFinalEyeReady = null;
        return true;
    }

    /** Optional adapter supplies a frame-local proof, keeping Oculus/Vivecraft types out of this state owner. */
    public static void configureShaderFrame(BooleanSupplier finalEyeReady) { shaderFinalEyeReady = finalEyeReady; }
    public static void registerShaderReset(Runnable reset) { shaderReset = reset; }
    public static boolean isShaderFrame() { return shaderFinalEyeReady != null; }
    public static boolean lateReplayReady() {
        return shaderFinalEyeReady == null || shaderFinalEyeReady.getAsBoolean();
    }

    public static boolean prepare(long frame, VrScopeRenderRequest request,
                                  VrScopeAlignmentCapture.LiveScope live, RenderTarget framebuffer,
                                  VrScopeWorldView camera) {
        if (camera == null || framebuffer == null || framebuffer.frameBufferId <= 0
                || framebuffer.getColorTextureId() <= 0 || !live.matches(request)) return false;
        if (!STATE.prepare(frame, request, live.world(), live.item(), framebuffer)) return false;
        target = framebuffer;
        texture = framebuffer.getColorTextureId();
        targetWidth = framebuffer.width;
        targetHeight = framebuffer.height;
        view = camera;
        return true;
    }

    public static boolean scheduled(long frame) { return STATE.scheduled(frame); }
    public static boolean takeFirstEye(long frame) { return STATE.takeFirstEye(frame); }
    public static boolean isScopePass() { return RenderSystem.isOnRenderThread() && ownedScopeDraw; }
    public static boolean isSourceTexture(int textureId) { return target != null && textureId == texture; }
    public static VrScopeWorldView worldView() { return view; }

    public static boolean begin(long frame, RenderTarget framebuffer) {
        Optional<VrScopeAlignmentCapture.LiveScope> live = VrScopeAlignmentCapture.currentScope();
        boolean began = framebuffer != null && enabled() && live.isPresent() && live.get().matches(STATE.request())
                && framebuffer == target && texture == framebuffer.getColorTextureId()
                && framebuffer.width == targetWidth && framebuffer.height == targetHeight
                && STATE.begin(frame, live.get().world(), live.get().item(), framebuffer);
        if (began) ownedScopeDraw = true;
        return began;
    }

    public static void cameraApplied() { STATE.cameraApplied(); }

    public static Matrix4f projection(Matrix4f original) {
        observe(Hook.PROJECTION);
        if (!isScopePass() || failed) return original;
        VrScopeRenderRequest request = STATE.request();
        Optional<Matrix4f> result = VrScopeWorldView.magnifiedProjection(
                original, request.magnification(), request.halfFovTangent());
        if (result.isEmpty()) {
            fail(new IllegalStateException("Unsupported scope projection; retaining the original matrix"));
            return original;
        }
        STATE.projectionApplied();
        return result.get();
    }

    public static void finish(boolean success) {
        ownedScopeDraw = false;
        boolean complete = STATE.finish(success && enabled() && target != null
                && target.frameBufferId > 0 && target.getColorTextureId() == texture
                && target.width == targetWidth && target.height == targetHeight);
        long now = System.nanoTime();
        if (DEBUG && (lastDiagnostic == 0 || now - lastDiagnostic > 1_000_000_000L)) {
            lastDiagnostic = now;
            VrScopeRenderRequest request = STATE.request();
            TaczVr.LOGGER.info("TACZVR_SCOPE_MAGNIFICATION stage=world complete={} source_frame={} scope={} zoom={} field_tan={}",
                    complete, request == null ? -1 : request.sourceFrameIndex(),
                    request == null ? null : request.scopeId(), request == null ? 0 : request.magnification(),
                    request == null ? 0 : request.halfFovTangent());
        }
    }

    static void recordLens(String pass, long frame, BedrockAttachmentModel model, ResourceLocation gun,
                           ResourceLocation scope, int viewIndex, int divisionIndex, VrScopeLensView lens) {
        int eye = VrScopeRenderPass.fromName(pass).eyeIndex();
        if (eye >= 0) LENSES[eye] = new LensFrame(frame, model, gun, scope, viewIndex, divisionIndex, lens);
    }

    public static void composite(BedrockAttachmentModel model, int divisionIndex) {
        observe(Hook.COMPOSITE);
        if (!enabled() || isScopePass() || target == null) return;
        // Final reticle replay must not overwrite Vivecraft's global eye effects with raw world RGB.
        if (VrScopeLateDivisionRenderer.isReplaying() && !VrScopeLateDivisionRenderer.isImageReplay()) return;
        if (isShaderFrame() && (!VrScopeLateDivisionRenderer.isReplaying() || !lateReplayReady())) return;
        int eye = VrScopeRenderPass.fromName(TaczVrClient.vivecraft().getRenderPassName()).eyeIndex();
        long frame = TaczVrClient.vivecraft().getRenderFrameIndex().orElse(-1L);
        if (eye < 0) return;
        LensFrame lens = LENSES[eye];
        // A model can have a separate reflex sight before the selected scope layer. This is an ordinary skipped
        // draw, not a failed composition; it must not spend the selected layer's bounded diagnostic slot.
        if (lens != null && lens.divisionIndex() != divisionIndex) return;
        Optional<VrScopeAlignmentCapture.LiveScope> live = VrScopeAlignmentCapture.currentScope();
        String rejection = imageRejection(model, frame, lens, live);
        if (rejection != null) {
            compositeDiagnostic(eye, frame, divisionIndex, lens, rejection, false);
            return;
        }
        try {
            boolean drawn = VrScopeMagnificationCompositor.draw(lens.view(), STATE.request().halfFovTangent(), texture,
                    (~(divisionIndex + 1)) & 0xFF, target.frameBufferId);
            compositeDiagnostic(eye, frame, divisionIndex, lens, drawn ? "drawn" : "stencil_or_geometry", drawn);
        } catch (RuntimeException | LinkageError error) {
            fail(error);
        }
    }

    private static String imageRejection(BedrockAttachmentModel model, long frame, LensFrame lens,
                                         Optional<VrScopeAlignmentCapture.LiveScope> live) {
        if (target == null) return "no_image";
        if (lens == null) return "missing_lens";
        if (lens.frame() != frame) return "stale_lens";
        if (lens.model() != model) return "lens_model";
        if (live.isEmpty()) return "missing_live_scope";
        VrScopeAlignmentCapture.LiveScope scope = live.get();
        if (scope.model() != model) return "live_model";
        if (!scope.matches(STATE.request()) || lens.viewIndex() != scope.viewIndex()) return "live_request";
        if (!lens.gun().equals(scope.gunId()) || !lens.scope().equals(scope.scopeId())) return "lens_identity";
        if (!STATE.imageReady(frame, scope.world(), scope.item(), target)) return "image_not_ready";
        if (target.frameBufferId <= 0 || target.getColorTextureId() != texture
                || target.width != targetWidth || target.height != targetHeight) return "target_changed";
        return lens.view().corners(STATE.request().halfFovTangent()).isEmpty() ? "lens_geometry" : null;
    }

    private static void compositeDiagnostic(int eye, long frame, int divisionIndex, LensFrame lens,
                                            String reason, boolean drawn) {
        if (!DEBUG) return;
        long now = System.nanoTime();
        if (LAST_COMPOSITE_DIAGNOSTIC[eye] != 0 && now - LAST_COMPOSITE_DIAGNOSTIC[eye] < 1_000_000_000L) return;
        LAST_COMPOSITE_DIAGNOSTIC[eye] = now;
        VrScopeRenderRequest request = STATE.request();
        TaczVr.LOGGER.info("TACZVR_SCOPE_MAGNIFICATION stage=composite eye={} frame={} drawn={} reason={} "
                        + "division={} lens_frame={} lens_view={} source_frame={} scope={} zoom={}",
                eye, frame, drawn, reason, divisionIndex, lens == null ? -1 : lens.frame(),
                lens == null ? -1 : lens.viewIndex(), request == null ? -1 : request.sourceFrameIndex(),
                request == null ? null : request.scopeId(), request == null ? 0 : request.magnification());
    }

    public static void fail(Throwable error) {
        STATE.invalidateImage();
        if (!failed) TaczVr.LOGGER.warn("Disabling experimental scope magnification until reload after an adapter failure", error);
        failed = true;
    }

    public static void reset() {
        if (shaderReset != null) {
            shaderReset.run();
            shaderReset = null;
        }
        STATE.reset();
        OBSERVED.clear();
        COMPATIBILITY.reset();
        target = null;
        texture = -1;
        view = null;
        ownedScopeDraw = false;
        shaderFinalEyeReady = null;
        LENSES[0] = LENSES[1] = null;
        failed = false;
        lastSchedule = Long.MIN_VALUE;
        lastDiagnostic = 0;
        lastScheduleDiagnostic = 0;
        LAST_COMPOSITE_DIAGNOSTIC[0] = LAST_COMPOSITE_DIAGNOSTIC[1] = 0;
    }

    private record LensFrame(long frame, BedrockAttachmentModel model, ResourceLocation gun, ResourceLocation scope,
                             int viewIndex, int divisionIndex, VrScopeLensView view) {
    }
}
