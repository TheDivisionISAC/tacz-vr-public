package com.thedivisionisac.taczvr.client.vr;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.mixin.client.BedrockAttachmentModelAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.item.ItemDisplayContext;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.joml.Matrix4f;

import java.nio.IntBuffer;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * Composes the world image into the post-process input and replays only ocular/reticle geometry after eye effects.
 * The pre-effect alias owns a separate stencil so source and final-eye resolutions do not thrash one allocation.
 * Water distortion and
 * cloud/fog composition happen after ordinary hand rendering, so no world-depth state can keep a division above
 * them. The final eye target has no stencil attachment; this renderer lends it one reusable stencil renderbuffer for
 * the duration of the replay and detaches it immediately afterwards. It enables the already-attached stencil
 * directly instead of calling TACZ's main-target helper, which could resize or rebind a different framebuffer.
 * Targets with an existing stencil attachment are not owned by this renderer and are left untouched, including
 * their stencil pixels. Independent front/back stencil and RGB/alpha blend state survive an optional replay.
 */
public final class VrScopeLateDivisionRenderer {
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final LateFrame[] FRAMES = new LateFrame[2];
    private static final long[] LAST_CAPTURE_FRAME = {Long.MIN_VALUE, Long.MIN_VALUE};
    private static final long[] LAST_REPLAY_FRAME = {Long.MIN_VALUE, Long.MIN_VALUE};
    private static final long[] LAST_IMAGE_FRAME = {Long.MIN_VALUE, Long.MIN_VALUE};
    private static boolean imageReplay;
    private static int stencilRenderbuffer;
    private static int stencilWidth;
    private static int stencilHeight;
    private static int imageStencilRenderbuffer;
    private static int imageStencilWidth;
    private static int imageStencilHeight;
    private static boolean failureLogged;
    private static final boolean DEBUG = Boolean.getBoolean(VrScopeAlignmentGateDiagnostics.ENABLE_PROPERTY);
    private static final long DIAGNOSTIC_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1L);
    private static final long[] LAST_CAPTURE_DIAGNOSTIC_NANOS = {Long.MIN_VALUE, Long.MIN_VALUE};
    private static final long[] LAST_REPLAY_DIAGNOSTIC_NANOS = {Long.MIN_VALUE, Long.MIN_VALUE};
    /** OpenGL calls are render-thread-only; keep one direct four-value query buffer per calling thread. */
    private static final ThreadLocal<IntBuffer> INT4_BUFFER =
            ThreadLocal.withInitial(() -> BufferUtils.createIntBuffer(4));
    /** Prevent a replayed attachment draw from recursively replacing the frame currently being replayed. */
    private static final ThreadLocal<Boolean> REPLAY_ACTIVE = new ThreadLocal<>();

    private VrScopeLateDivisionRenderer() {
    }

    /** The active final-eye replay is the only allowed color-composition stage for the Oculus adapter. */
    public static boolean isReplaying() { return Boolean.TRUE.equals(REPLAY_ACTIVE.get()); }

    public static boolean isImageReplay() { return imageReplay; }

    /** Compose into the exact source that Vivecraft will post-process, then leave final replay to the reticle only. */
    public static void renderBeforePostProcess(VrScopeRenderPass pass, RenderTarget source) {
        int eye = pass == null ? -1 : pass.eyeIndex();
        if (eye < 0 || source == null || isReplaying() || !VrScopeMagnificationRuntime.enabled()
                || VrScopeMagnificationRuntime.worldView() == null
                || !VrScopeMagnificationRuntime.lateReplayReady()
                || VrScopeMagnificationRuntime.isSourceTexture(source.getColorTextureId())) return;
        LateFrame frame = FRAMES[eye];
        long current = TaczVrClient.vivecraft().getRenderFrameIndex().orElse(Long.MIN_VALUE);
        if (frame == null || frame.frameIndex() != current || LAST_IMAGE_FRAME[eye] == current) return;
        LAST_IMAGE_FRAME[eye] = current;
        REPLAY_ACTIVE.set(Boolean.TRUE);
        imageReplay = true;
        try {
            VrScopePostProcessTarget.draw(source.getColorTextureId(), source.viewWidth, source.viewHeight,
                    () -> render(frame));
        } catch (RuntimeException | LinkageError failure) {
            if (!failureLogged) {
                failureLogged = true;
                TaczVr.LOGGER.warn("Scope pre-effect composition failed; keeping ordinary eye rendering", failure);
            }
        } finally {
            imageReplay = false;
            REPLAY_ACTIVE.remove();
        }
    }

    /** Shares the audited GL/matrix borrowing boundary with the optional scope pass and its texture quad. */
    public static void withRestoredRenderState(Runnable action) {
        GlState state = GlState.capture();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        PoseStack stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        try {
            action.run();
        } finally {
            try {
                try {
                    stack.popPose();
                } finally {
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.setProjectionMatrix(projection, sorting);
                }
            } finally {
                state.restore();
            }
        }
    }

    public static void capture(
            BedrockAttachmentModel model,
            PoseStack poseStack,
            ItemDisplayContext transformType,
            RenderType renderType,
            int light,
            int overlay,
            float radiusModifier,
            boolean scopeOnly,
            VrScopeAlignmentCapture.VrScopeCircleCenter circleCenter
    ) {
        VrScopeRenderPass pass = VrScopeRenderPass.fromName(TaczVrClient.vivecraft().getRenderPassName());
        int eye = pass.eyeIndex();
        if (Boolean.TRUE.equals(REPLAY_ACTIVE.get())) {
            return;
        }
        OptionalLong frameIndex = TaczVrClient.vivecraft().getRenderFrameIndex();
        if (eye < 0 || frameIndex.isEmpty()) {
            return;
        }
        long currentFrame = frameIndex.getAsLong();
        if (currentFrame < LAST_CAPTURE_FRAME[eye] || currentFrame < LAST_REPLAY_FRAME[eye]) {
            // A restarted Vivecraft session must not inherit either eye's pending frame or replay watermark.
            restartFrameStream();
        }
        if (currentFrame == LAST_REPLAY_FRAME[eye]) {
            return;
        }
        if (currentFrame == LAST_CAPTURE_FRAME[eye] && FRAMES[eye] != null) {
            // Multiple attachment callbacks can occur in one eye pass. Keep the first complete capture so a later
            // callback cannot replace it with a different model or pose.
            return;
        }
        LAST_CAPTURE_FRAME[eye] = currentFrame;
        if (!Float.isFinite(radiusModifier) || radiusModifier <= 0.0F) {
            FRAMES[eye] = null;
            return;
        }
        FRAMES[eye] = new LateFrame(
                currentFrame,
                model,
                copyPose(poseStack),
                transformType,
                renderType,
                light,
                overlay,
                radiusModifier,
                scopeOnly,
                circleCenter,
                new Matrix4f(RenderSystem.getProjectionMatrix()),
                new Matrix4f(RenderSystem.getModelViewMatrix()),
                RenderSystem.getVertexSorting()
        );
        recordCaptureDiagnostic(eye, currentFrame, radiusModifier);
    }

    public static void renderAfterPostProcess(String renderPass) {
        renderAfterPostProcess(VrScopeRenderPass.fromName(renderPass));
    }

    public static void renderAfterPostProcess(VrScopeRenderPass pass) {
        if (pass == null) {
            return;
        }
        int eye = pass.eyeIndex();
        if (Boolean.TRUE.equals(REPLAY_ACTIVE.get())) {
            return;
        }
        if (eye < 0) {
            return;
        }
        if (!VrScopeMagnificationRuntime.lateReplayReady()) {
            FRAMES[eye] = null;
            return;
        }
        if (!VrScopeAlignmentCapture.ownsScopeOcularOpening()) {
            FRAMES[eye] = null;
            LAST_REPLAY_FRAME[eye] = LAST_CAPTURE_FRAME[eye];
            return;
        }
        LateFrame frame = FRAMES[eye];
        FRAMES[eye] = null;
        OptionalLong currentFrame = TaczVrClient.vivecraft().getRenderFrameIndex();
        if (frame == null) {
            LAST_REPLAY_FRAME[eye] = Math.max(LAST_REPLAY_FRAME[eye], LAST_CAPTURE_FRAME[eye]);
            recordReplayDiagnostic(eye, -1L, ReplayResult.skipped("no_capture"));
            return;
        }
        LAST_REPLAY_FRAME[eye] = frame.frameIndex();
        if (currentFrame.isEmpty()) {
            recordReplayDiagnostic(eye, frame.frameIndex(), ReplayResult.skipped("no_current_frame"));
            return;
        }
        if (frame.frameIndex() != currentFrame.getAsLong()) {
            recordReplayDiagnostic(
                    eye,
                    frame.frameIndex(),
                    ReplayResult.skipped("frame_mismatch_current_" + currentFrame.getAsLong())
            );
            return;
        }
        REPLAY_ACTIVE.set(Boolean.TRUE);
        try {
            recordReplayDiagnostic(eye, frame.frameIndex(), render(frame));
        } catch (RuntimeException | LinkageError failure) {
            if (!failureLogged) {
                failureLogged = true;
                TaczVr.LOGGER.warn("TACZ VR late scope division replay failed; disabling this frame", failure);
            }
        } finally {
            REPLAY_ACTIVE.remove();
        }
    }

    public static void reset() {
        VrScopePostProcessTarget.reset();
        restartFrameStream();
        failureLogged = false;
        LAST_CAPTURE_DIAGNOSTIC_NANOS[LEFT] = Long.MIN_VALUE;
        LAST_CAPTURE_DIAGNOSTIC_NANOS[RIGHT] = Long.MIN_VALUE;
        LAST_REPLAY_DIAGNOSTIC_NANOS[LEFT] = Long.MIN_VALUE;
        LAST_REPLAY_DIAGNOSTIC_NANOS[RIGHT] = Long.MIN_VALUE;
        REPLAY_ACTIVE.remove();
        INT4_BUFFER.remove();
    }

    /** Discards only pending eye captures when the optional late replay is disabled mid-frame. */
    private static void clearPendingFrames() {
        FRAMES[LEFT] = null;
        FRAMES[RIGHT] = null;
    }

    private static void restartFrameStream() {
        LAST_IMAGE_FRAME[LEFT] = LAST_IMAGE_FRAME[RIGHT] = Long.MIN_VALUE;
        imageReplay = false;
        clearPendingFrames();
        LAST_CAPTURE_FRAME[LEFT] = Long.MIN_VALUE;
        LAST_CAPTURE_FRAME[RIGHT] = Long.MIN_VALUE;
        LAST_REPLAY_FRAME[LEFT] = Long.MIN_VALUE;
        LAST_REPLAY_FRAME[RIGHT] = Long.MIN_VALUE;
    }

    private static ReplayResult render(LateFrame frame) {
        // Query this binding before taking the broader snapshot. A post-process callback can legally run while the
        // default framebuffer is bound; in that case no stencil attachment or RenderSystem shader state is touched.
        int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (framebuffer == 0) {
            return ReplayResult.skipped("framebuffer_zero");
        }
        int existingStencilType = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_STENCIL_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
        );
        if (existingStencilType != GL11.GL_NONE) {
            // Restoring stencil functions cannot restore stencil pixels. Without an ownership protocol we must
            // leave another compositor's attachment intact and keep only the ordinary TACZ attachment render.
            return ReplayResult.skipped("existing_stencil", framebuffer, 0, 0);
        }
        GlState state = GlState.capture();
        if (state.drawFramebuffer() != framebuffer) {
            // Keep the target identity captured atomically from the render thread. A third-party render call must not
            // make us attach a stencil to a different target than the one inspected above.
            return ReplayResult.skipped(
                    "framebuffer_changed",
                    framebuffer,
                    state.viewportWidth(),
                    state.viewportHeight()
            );
        }
        int width = state.viewportWidth();
        int height = state.viewportHeight();
        if (width <= 0 || height <= 0) {
            return new ReplayResult("invalid_viewport", framebuffer, width, height, GL11.GL_NONE, false, 0, 0);
        }

        boolean lentStencil = existingStencilType == GL11.GL_NONE;
        int framebufferStatus = GL30.GL_FRAMEBUFFER_COMPLETE;
        MatrixState matrixState = null;
        BedrockAttachmentModelAccessor accessor = (BedrockAttachmentModelAccessor) frame.model();
        float originalRadius = accessor.taczvr$getScopeViewRadiusModifier();
        try {
            if (lentStencil) {
                int borrowedStencil = ensureStencilRenderbuffer(width, height);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
                // Mark the attachment as borrowed before issuing GL calls so every partial setup is cleaned up.
                GL30.glFramebufferRenderbuffer(
                        GL30.GL_DRAW_FRAMEBUFFER,
                        GL30.GL_STENCIL_ATTACHMENT,
                        GL30.GL_RENDERBUFFER,
                        borrowedStencil
                );
            }
            framebufferStatus = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
            if (framebufferStatus != GL30.GL_FRAMEBUFFER_COMPLETE) {
                return new ReplayResult(
                        "incomplete_framebuffer",
                        framebuffer,
                        width,
                        height,
                        existingStencilType,
                        lentStencil,
                        framebufferStatus,
                        0
                );
            }
            matrixState = MatrixState.captureAndApply(frame);
            accessor.taczvr$setScopeViewRadiusModifier(frame.radiusModifier());
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            // glClear is clipped by scissor. The replay owns the whole target, so avoid inheriting a post-process
            // scissor rectangle and restore the caller's setting in GlState's finally path.
            RenderSystem.disableScissor();
            RenderSystem.stencilMask(0xFF);
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            // Minecraft caches only one stencil face. A third-party separate-face call may have left the back
            // face different even when a cached setter becomes a no-op; establish both faces before TACZ draws.
            GL11.glStencilMask(0xFF);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.clearStencil(0);
            RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);
            try (VrScopeOcularRenderContext.Scope ignored =
                         VrScopeOcularRenderContext.enterPhysicalOpening();
                 VrScopeTaczCircleRenderContext.Scope ignoredCircle =
                         VrScopeTaczCircleRenderContext.enter(frame.circleCenter())) {
                if (frame.scopeOnly()) {
                    accessor.taczvr$invokeRenderOcularStencil(
                            frame.poseStack(), frame.transformType(), frame.renderType(),
                            frame.light(), frame.overlay(), true
                    );
                }
                accessor.taczvr$invokeRenderOcularStencil(
                        frame.poseStack(), frame.transformType(), frame.renderType(),
                        frame.light(), frame.overlay(), false
                );
                accessor.taczvr$invokeRenderOcularAndDivision(
                        frame.poseStack(), frame.transformType(), frame.renderType(),
                        frame.light(), frame.overlay(), frame.scopeOnly()
                );
            }
        } finally {
            try {
                accessor.taczvr$setScopeViewRadiusModifier(originalRadius);
            } finally {
                try {
                    GL11.glDisable(GL11.GL_STENCIL_TEST);
                } finally {
                    try {
                        if (matrixState != null) {
                            matrixState.restore();
                        }
                    } finally {
                        try {
                            state.restore();
                        } finally {
                            try {
                                if (lentStencil) {
                                    // A model or a custom RenderType may have rebound another FBO. Rebind the
                                    // original draw target before detaching so the borrowed attachment cannot leak
                                    // to an unrelated target.
                                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer());
                                    GL30.glFramebufferRenderbuffer(
                                            GL30.GL_DRAW_FRAMEBUFFER,
                                            GL30.GL_STENCIL_ATTACHMENT,
                                            GL30.GL_RENDERBUFFER,
                                            0
                                    );
                                }
                            } finally {
                                // Restore framebuffer/read-buffer/renderbuffer bindings even when a model cleanup hook
                                // failed.
                                state.restoreFramebuffers();
                            }
                        }
                    }
                }
            }
        }
        int glError = DEBUG ? GL11.glGetError() : GL11.GL_NO_ERROR;
        return new ReplayResult(
                "replayed",
                framebuffer,
                width,
                height,
                existingStencilType,
                lentStencil,
                framebufferStatus,
                glError
        );
    }

    private static void recordCaptureDiagnostic(int eye, long frameIndex, float radiusModifier) {
        if (!DEBUG || !intervalElapsed(LAST_CAPTURE_DIAGNOSTIC_NANOS[eye])) {
            return;
        }
        LAST_CAPTURE_DIAGNOSTIC_NANOS[eye] = System.nanoTime();
        TaczVr.LOGGER.info(String.format(
                Locale.ROOT,
                "TACZVR_SCOPE_LATE_DIVISION_DIAG stage=capture pass=%s frame=%d radius=%.6f",
                eyeName(eye),
                frameIndex,
                radiusModifier
        ));
    }

    private static void recordReplayDiagnostic(int eye, long frameIndex, ReplayResult result) {
        if (!DEBUG || !intervalElapsed(LAST_REPLAY_DIAGNOSTIC_NANOS[eye])) {
            return;
        }
        LAST_REPLAY_DIAGNOSTIC_NANOS[eye] = System.nanoTime();
        TaczVr.LOGGER.info(String.format(
                Locale.ROOT,
                "TACZVR_SCOPE_LATE_DIVISION_DIAG stage=postprocess pass=%s frame=%d result=%s "
                        + "fbo=%d viewport=%dx%d stencil_type=%d lent_stencil=%s "
                        + "fbo_status=0x%04X gl_error=0x%04X",
                eyeName(eye),
                frameIndex,
                result.status(),
                result.framebuffer(),
                result.width(),
                result.height(),
                result.existingStencilType(),
                result.lentStencil(),
                result.framebufferStatus(),
                result.glError()
        ));
    }

    private static boolean intervalElapsed(long previousNanos) {
        long now = System.nanoTime();
        return previousNanos == Long.MIN_VALUE
                || now < previousNanos
                || now - previousNanos >= DIAGNOSTIC_INTERVAL_NANOS;
    }

    private static String eyeName(int eye) {
        return eye == LEFT ? VrScopeRenderPass.LEFT.name() : VrScopeRenderPass.RIGHT.name();
    }

    private static int ensureStencilRenderbuffer(int width, int height) {
        if (imageReplay) {
            if (imageStencilRenderbuffer == 0) imageStencilRenderbuffer = GL30.glGenRenderbuffers();
            if (width != imageStencilWidth || height != imageStencilHeight) {
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, imageStencilRenderbuffer);
                GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_STENCIL_INDEX8, width, height);
                imageStencilWidth = width;
                imageStencilHeight = height;
            }
            return imageStencilRenderbuffer;
        }
        if (stencilRenderbuffer == 0) {
            stencilRenderbuffer = GL30.glGenRenderbuffers();
        }
        if (width == stencilWidth && height == stencilHeight) {
            return stencilRenderbuffer;
        }
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, stencilRenderbuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_STENCIL_INDEX8, width, height);
        stencilWidth = width;
        stencilHeight = height;
        return stencilRenderbuffer;
    }

    private static int[] readInt4(int pname) {
        IntBuffer values = INT4_BUFFER.get();
        values.clear();
        GL11.glGetIntegerv(pname, values);
        return new int[]{values.get(0), values.get(1), values.get(2), values.get(3)};
    }

    private static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    private record LateFrame(
            long frameIndex,
            BedrockAttachmentModel model,
            PoseStack poseStack,
            ItemDisplayContext transformType,
            RenderType renderType,
            int light,
            int overlay,
            float radiusModifier,
            boolean scopeOnly,
            VrScopeAlignmentCapture.VrScopeCircleCenter circleCenter,
            Matrix4f projectionMatrix,
            Matrix4f modelViewMatrix,
            VertexSorting vertexSorting
    ) {
    }

    private record MatrixState(
            Matrix4f projectionMatrix,
            VertexSorting vertexSorting,
            PoseStack modelViewStack
    ) {
        private static MatrixState captureAndApply(LateFrame frame) {
            Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            VertexSorting previousSorting = RenderSystem.getVertexSorting();
            PoseStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushPose();
            try {
                modelViewStack.last().pose().set(frame.modelViewMatrix());
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(frame.projectionMatrix(), frame.vertexSorting());
                return new MatrixState(previousProjection, previousSorting, modelViewStack);
            } catch (RuntimeException | Error failure) {
                try {
                    modelViewStack.popPose();
                } finally {
                    try {
                        RenderSystem.applyModelViewMatrix();
                    } finally {
                        RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
                    }
                }
                throw failure;
            }
        }

        private void restore() {
            try {
                RenderSystem.setProjectionMatrix(this.projectionMatrix, this.vertexSorting);
            } finally {
                try {
                    this.modelViewStack.popPose();
                } finally {
                    RenderSystem.applyModelViewMatrix();
                }
            }
        }
    }

    private record ReplayResult(
            String status,
            int framebuffer,
            int width,
            int height,
            int existingStencilType,
            boolean lentStencil,
            int framebufferStatus,
            int glError
    ) {
        private static ReplayResult skipped(String status) {
            return new ReplayResult(status, 0, 0, 0, GL11.GL_NONE, false, 0, 0);
        }

        private static ReplayResult skipped(String status, int framebuffer, int width, int height) {
            return new ReplayResult(status, framebuffer, width, height, GL11.GL_NONE, false, 0, 0);
        }
    }

    private record StencilFaceState(
            int writeMask, int function, int reference, int valueMask, int fail, int depthFail, int depthPass
    ) {
        private static StencilFaceState capture(boolean back) {
            return new StencilFaceState(
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_WRITEMASK : GL11.GL_STENCIL_WRITEMASK),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_FUNC : GL11.GL_STENCIL_FUNC),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_REF : GL11.GL_STENCIL_REF),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_VALUE_MASK : GL11.GL_STENCIL_VALUE_MASK),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_FAIL : GL11.GL_STENCIL_FAIL),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL : GL11.GL_STENCIL_PASS_DEPTH_FAIL),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS : GL11.GL_STENCIL_PASS_DEPTH_PASS)
            );
        }

        private void restore(int face) {
            GL20.glStencilMaskSeparate(face, this.writeMask);
            GL20.glStencilFuncSeparate(face, this.function, this.reference, this.valueMask);
            GL20.glStencilOpSeparate(face, this.fail, this.depthFail, this.depthPass);
        }
    }

    private record GlState(
            int drawFramebuffer,
            int readFramebuffer,
            int renderbuffer,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            boolean colorRed,
            boolean colorGreen,
            boolean colorBlue,
            boolean colorAlpha,
            boolean depthTest,
            boolean depthWrite,
            int depthFunction,
            boolean stencilTest,
            StencilFaceState frontStencil,
            StencilFaceState backStencil,
            int stencilClearValue,
            boolean blendEnabled,
            int blendSourceRgb,
            int blendDestinationRgb,
            int blendSourceAlpha,
            int blendDestinationAlpha,
            int blendEquation,
            int blendEquationAlpha,
            boolean cullEnabled,
            boolean scissorEnabled,
            int scissorX,
            int scissorY,
            int scissorWidth,
            int scissorHeight,
            boolean polygonOffsetFillEnabled,
            float polygonOffsetFactor,
            float polygonOffsetUnits,
            float lineWidth,
            float[] shaderColor,
            ShaderInstance shader
    ) {
        private static GlState capture() {
            int[] colorMask = readInt4(GL11.GL_COLOR_WRITEMASK);
            int[] scissorBox = readInt4(GL11.GL_SCISSOR_BOX);
            int[] viewport = readInt4(GL11.GL_VIEWPORT);
            return new GlState(
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING),
                    viewport[0],
                    viewport[1],
                    viewport[2],
                    viewport[3],
                    colorMask[0] != 0,
                    colorMask[1] != 0,
                    colorMask[2] != 0,
                    colorMask[3] != 0,
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glIsEnabled(GL11.GL_STENCIL_TEST),
                    StencilFaceState.capture(false),
                    StencilFaceState.capture(true),
                    GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    scissorBox[0],
                    scissorBox[1],
                    scissorBox[2],
                    scissorBox[3],
                    GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL),
                    GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR),
                    GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS),
                    RenderSystem.getShaderLineWidth(),
                    RenderSystem.getShaderColor().clone(),
                    RenderSystem.getShader()
            );
        }

        private void restore() {
            this.restoreFramebuffers();
            RenderSystem.viewport(this.viewportX, this.viewportY, this.viewportWidth, this.viewportHeight);
            RenderSystem.colorMask(this.colorRed, this.colorGreen, this.colorBlue, this.colorAlpha);
            RenderSystem.depthFunc(this.depthFunction);
            RenderSystem.depthMask(this.depthWrite);
            if (this.depthTest) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            // Keep Minecraft's single-face cache synchronized to the restored front face, then restore the actual
            // separate states even if a cached setter was skipped. Back-face calls do not overwrite that cache.
            RenderSystem.stencilMask(this.frontStencil.writeMask());
            RenderSystem.stencilFunc(this.frontStencil.function(), this.frontStencil.reference(),
                    this.frontStencil.valueMask());
            RenderSystem.stencilOp(this.frontStencil.fail(), this.frontStencil.depthFail(),
                    this.frontStencil.depthPass());
            this.frontStencil.restore(GL11.GL_FRONT);
            this.backStencil.restore(GL11.GL_BACK);
            if (this.stencilTest) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            } else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
            RenderSystem.clearStencil(this.stencilClearValue);
            RenderSystem.blendFuncSeparate(
                    this.blendSourceRgb,
                    this.blendDestinationRgb,
                    this.blendSourceAlpha,
                    this.blendDestinationAlpha
            );
            // RenderSystem's blendEquation has no cached equation and sets both channels; use the separate API.
            GL20.glBlendEquationSeparate(this.blendEquation, this.blendEquationAlpha);
            if (this.blendEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (this.cullEnabled) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            if (this.scissorEnabled) {
                RenderSystem.enableScissor(this.scissorX, this.scissorY, this.scissorWidth, this.scissorHeight);
            } else {
                RenderSystem.disableScissor();
            }
            if (this.polygonOffsetFillEnabled) {
                RenderSystem.enablePolygonOffset();
            } else {
                RenderSystem.disablePolygonOffset();
            }
            RenderSystem.polygonOffset(this.polygonOffsetFactor, this.polygonOffsetUnits);
            RenderSystem.lineWidth(this.lineWidth);
            RenderSystem.setShaderColor(
                    this.shaderColor[0], this.shaderColor[1], this.shaderColor[2], this.shaderColor[3]
            );
            RenderSystem.setShader(() -> this.shader);
        }

        private void restoreFramebuffers() {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.readFramebuffer);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this.renderbuffer);
        }
    }
}
