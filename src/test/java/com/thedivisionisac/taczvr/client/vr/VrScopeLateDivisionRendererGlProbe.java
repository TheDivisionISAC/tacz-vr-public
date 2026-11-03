package com.thedivisionisac.taczvr.client.vr;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Explicit desktop GL smoke probe, run by scopeGlSmokeTest rather than headless check. A hidden 32px context verifies
 * real driver state and preserves an existing attachment's pixels. It does not initialize Minecraft or SteamVR,
 * apply Mixins, draw TACZ models, or replace HMD integration testing. The companion magnification probe loads a
 * vanilla shader and exercises the production texture compositor in this same native context.
 */
public final class VrScopeLateDivisionRendererGlProbe {
    private VrScopeLateDivisionRendererGlProbe() {
    }

    public static void main(String[] args) throws Exception {
        GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err);
        GLFW.glfwSetErrorCallback(errors);
        long window = 0L;
        int framebuffer = 0;
        int color = 0;
        int stencil = 0;
        try {
            if (!GLFW.glfwInit()) {
                throw new AssertionError("GLFW initialization failed");
            }
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            window = GLFW.glfwCreateWindow(32, 32, "TACZ VR GL probe", 0L, 0L);
            if (window == 0L) {
                throw new AssertionError("Hidden OpenGL context unavailable");
            }
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            RenderSystem.initRenderThread();
            framebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            color = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 32, 32, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, color, 0);
            stencil = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, stencil);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, 32, 32);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, stencil);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new AssertionError("Probe framebuffer incomplete");
            }
            RenderSystem.viewport(0, 0, 32, 32);
            RenderSystem.stencilMask(0xFF);
            GL11.glStencilMask(0xFF);
            RenderSystem.clearStencil(7);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);

            Class<?> renderer = VrScopeLateDivisionRenderer.class;
            Class<?> lateFrame = Class.forName(renderer.getName() + "$LateFrame");
            Method render = accessible(renderer.getDeclaredMethod("render", lateFrame));
            Object result = render.invoke(null, new Object[]{null});
            Method status = accessible(result.getClass().getDeclaredMethod("status"));
            if (!"existing_stencil".equals(status.invoke(result))) {
                throw new AssertionError("Existing stencil target must be rejected before reading a model");
            }
            ByteBuffer pixels = BufferUtils.createByteBuffer(32 * 32);
            GL11.glReadPixels(0, 0, 32, 32, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, pixels);
            for (int i = 0; i < pixels.capacity(); i++) {
                if (pixels.get(i) != 7) {
                    throw new AssertionError("Borrowed another owner's stencil pixels at " + i);
                }
            }
            int actualAttachment = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER,
                    GL30.GL_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if (actualAttachment != stencil) {
                throw new AssertionError("Existing stencil attachment was replaced");
            }

            Class<?> glState = Class.forName(renderer.getName() + "$GlState");
            Method capture = accessible(glState.getDeclaredMethod("capture"));
            Method restore = accessible(glState.getDeclaredMethod("restore"));
            for (boolean fail : new boolean[]{false, true}) {
                GL20.glStencilMaskSeparate(GL11.GL_FRONT, 0x3F);
                GL20.glStencilMaskSeparate(GL11.GL_BACK, 0x7A);
                GL20.glStencilFuncSeparate(GL11.GL_FRONT, GL11.GL_LESS, 2, 0x5F);
                GL20.glStencilFuncSeparate(GL11.GL_BACK, GL11.GL_GREATER, 3, 0xC7);
                GL20.glStencilOpSeparate(GL11.GL_FRONT, GL11.GL_KEEP, GL11.GL_INCR, GL11.GL_REPLACE);
                GL20.glStencilOpSeparate(GL11.GL_BACK, GL11.GL_ZERO, GL11.GL_DECR, GL11.GL_INVERT);
                GL20.glBlendEquationSeparate(GL14.GL_FUNC_REVERSE_SUBTRACT, GL14.GL_FUNC_ADD);
                int[] before = state();
                Object snapshot = capture.invoke(null);
                try {
                    RenderSystem.stencilMask(0xFF);
                    RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
                    RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                    RenderSystem.blendEquation(GL14.GL_FUNC_SUBTRACT);
                    if (fail) {
                        throw new ProbeFailure();
                    }
                } catch (ProbeFailure expected) {
                    // The restoration primitive must also work from an exceptional caller's finally block.
                } finally {
                    restore.invoke(snapshot);
                }
                if (!Arrays.equals(before, state())) {
                    throw new AssertionError("Independent GL states changed after restore; exceptional=" + fail);
                }
            }
            VrScopeMagnificationGlProbe.run(framebuffer);
            VrScopePostProcessTargetGlProbe.run(framebuffer);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                throw new AssertionError("OpenGL error: 0x" + Integer.toHexString(error));
            }
            System.out.println("SCOPE_GL_SMOKE_PASS existing_stencil_pixels=1024 front_back=preserved "
                    + "blend_rgb_alpha=preserved normal_and_exceptional_restore=pass");
        } finally {
            if (window != 0L) {
                if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
                if (color != 0) GL11.glDeleteTextures(color);
                if (stencil != 0) GL30.glDeleteRenderbuffers(stencil);
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
            GLFW.glfwSetErrorCallback(null);
            errors.free();
        }
    }

    private static Method accessible(Method method) {
        method.setAccessible(true);
        return method;
    }

    private static int[] state() {
        return Arrays.stream(new int[]{
                GL11.GL_STENCIL_WRITEMASK, GL20.GL_STENCIL_BACK_WRITEMASK,
                GL11.GL_STENCIL_FUNC, GL20.GL_STENCIL_BACK_FUNC,
                GL11.GL_STENCIL_REF, GL20.GL_STENCIL_BACK_REF,
                GL11.GL_STENCIL_VALUE_MASK, GL20.GL_STENCIL_BACK_VALUE_MASK,
                GL11.GL_STENCIL_FAIL, GL20.GL_STENCIL_BACK_FAIL,
                GL11.GL_STENCIL_PASS_DEPTH_FAIL, GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL,
                GL11.GL_STENCIL_PASS_DEPTH_PASS, GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS,
                GL20.GL_BLEND_EQUATION_RGB, GL20.GL_BLEND_EQUATION_ALPHA,
                GL30.GL_DRAW_FRAMEBUFFER_BINDING, GL30.GL_READ_FRAMEBUFFER_BINDING,
                GL30.GL_RENDERBUFFER_BINDING
        }).map(GL11::glGetInteger).toArray();
    }

    private static final class ProbeFailure extends RuntimeException {
    }
}
