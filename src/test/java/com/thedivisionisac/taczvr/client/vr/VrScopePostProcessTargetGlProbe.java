package com.thedivisionisac.taczvr.client.vr;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import java.nio.ByteBuffer;

/** Actual shared-color FBO test: input pixels change before effects while its owned stencil/bindings survive. */
final class VrScopePostProcessTargetGlProbe {
    private VrScopePostProcessTargetGlProbe() {
    }

    static void run(int destination) {
        int texture = GL11.glGenTextures();
        int source = GL30.glGenFramebuffers();
        int stencil = GL30.glGenRenderbuffers();
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 16, 16, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, source);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, stencil);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_STENCIL_INDEX8, 16, 16);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, stencil);
            RenderSystem.disableScissor();
            RenderSystem.colorMask(true, true, true, true);
            GL11.glStencilMask(255);
            GL11.glClearStencil(91);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            for (boolean fail : new boolean[]{false, true}) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, destination);
                RenderSystem.viewport(2, 3, 24, 25);
                try {
                    VrScopeLateDivisionRenderer.withRestoredRenderState(() ->
                        VrScopePostProcessTarget.draw(texture, 16, 16, () -> {
                            if (GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                                    GL30.GL_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11.GL_NONE) {
                                throw new AssertionError("Pre-effect alias borrowed source stencil");
                            }
                            GL11.glClearColor(1, 0, 0, 1);
                            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                            if (fail) throw new ProbeFailure();
                        })
                    );
                } catch (ProbeFailure expected) {
                    if (!fail) throw expected;
                }
                var viewport = BufferUtils.createIntBuffer(4);
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                if (GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != destination
                        || GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) != destination
                        || viewport.get(0) != 2 || viewport.get(1) != 3 || viewport.get(2) != 24 || viewport.get(3) != 25) {
                    throw new AssertionError("Pre-effect alias did not restore target/viewport");
                }
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
                ByteBuffer pixels = BufferUtils.createByteBuffer(16 * 16 * 4);
                ByteBuffer mask = BufferUtils.createByteBuffer(16 * 16);
                GL11.glReadPixels(0, 0, 16, 16, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                GL11.glReadPixels(0, 0, 16, 16, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, mask);
                for (int i = 0; i < 256; i++) {
                    if ((pixels.get(i * 4) & 255) != 255 || pixels.get(i * 4 + 1) != 0
                            || pixels.get(i * 4 + 2) != 0 || (mask.get(i) & 255) != 91) {
                        throw new AssertionError("Pre-effect input pixels/stencil incorrect at " + i);
                    }
                }
            }
            System.out.println("SCOPE_PRE_EFFECT_TARGET_GL_PASS pixels=256 stencil_preserved=true exceptional_restore=true");
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, destination);
            RenderSystem.viewport(0, 0, 32, 32);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glDeleteTextures(texture);
            GL30.glDeleteRenderbuffers(stencil);
            GL30.glDeleteFramebuffers(source);
            VrScopePostProcessTarget.reset();
        }
    }

    private static final class ProbeFailure extends RuntimeException { }
}
