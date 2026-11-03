package com.thedivisionisac.taczvr.client.vr;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.thedivisionisac.taczvr.client.render.VrScopeImageShader;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

/**
 * Real opaque scope-image draw, including zero/fractional source alpha. Checks orientation, stencil clipping, feedback
 * rejection and the GL/matrix borrowing boundary. Classpath resources and a test-only reflected shader slot avoid
 * starting Minecraft; this does not verify Mixin application, Vivecraft world rendering or physical angular zoom.
 */
final class VrScopeMagnificationGlProbe {
    private VrScopeMagnificationGlProbe() {
    }

    static void run(int framebuffer) throws Exception {
        ResourceProvider resources = location -> {
            URL url = VrScopeMagnificationGlProbe.class.getResource(
                    "/assets/" + location.getNamespace() + "/" + location.getPath());
            return url == null ? Optional.empty() : Optional.of(new Resource(null, url::openStream) {
                @Override
                public String sourcePackId() { return "probe-classpath"; }
            });
        };
        Field shaderSlot = VrScopeImageShader.class.getDeclaredField("shader");
        shaderSlot.setAccessible(true);
        Object oldShader = shaderSlot.get(null);
        int texture = GlStateManager._genTexture();
        int oldTexture = GlStateManager._genTexture();
        int vao = GL30.glGenVertexArrays();
        int buffer = GL15.glGenBuffers();
        try (ShaderInstance shader = new ShaderInstance(resources, new ResourceLocation("taczvr", "scope_image"),
                DefaultVertexFormat.POSITION_TEX)) {
            shaderSlot.set(null, shader);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(texture);
            ByteBuffer texels = BufferUtils.createByteBuffer(16);
            // World targets may retain zero/fractional alpha in sky, fog and weather pixels. They are still
            // complete RGB images; neither discard nor blending may expose the unzoomed destination behind them.
            texels.put(new byte[]{-1, 0, 0, 0, 0, -1, 0, 64, -1, -1, 0, 127, -1, 0, -1, -1}).flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 2, 2, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
            GlStateManager._bindTexture(oldTexture);
            RenderSystem.setShaderTexture(0, oldTexture);
            RenderSystem.activeTexture(GL13.GL_TEXTURE3);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            RenderSystem.disableScissor();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.stencilMask(0xFF);
            GL11.glStencilMask(0xFF);
            RenderSystem.clearColor(0, 0, 1, 1);
            RenderSystem.clearStencil(0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
            RenderSystem.enableScissor(8, 8, 16, 16);
            RenderSystem.clearStencil(254);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            RenderSystem.disableScissor();
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            RenderSystem.stencilFunc(GL11.GL_EQUAL, 254, 0xFF);
            GL11.glStencilFunc(GL11.GL_EQUAL, 254, 0xFF);
            RenderSystem.stencilMask(0x5B);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_INCR, GL11.GL_DECR);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.colorMask(false, true, false, true);
            RenderSystem.setShaderColor(0.3F, 0.4F, 0.5F, 0.6F);
            RenderSystem.setShader(() -> shader);
            shader.apply();
            Matrix4f projection = new Matrix4f().setOrtho(-1, 1, -1, 1, 0.1F, 10);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewStack().setIdentity();
            RenderSystem.getModelViewStack().translate(2, 3, 4);
            RenderSystem.applyModelViewMatrix();
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            int[] before = state();
            float[] color = RenderSystem.getShaderColor().clone();
            VrScopeLensView lens = new VrScopeLensView(new Vec3(0, 0, -2),
                    VrRotation.validated(new Quaternionf()).orElseThrow(), 0.5D);

            if (VrScopeMagnificationCompositor.draw(lens, 0.5D, texture, 254, framebuffer)) {
                throw new AssertionError("Framebuffer feedback must be rejected");
            }
            if (VrScopeMagnificationCompositor.draw(lens, 0.5D, texture, 253, 0)) {
                throw new AssertionError("A different division stencil must be rejected");
            }
            if (!VrScopeMagnificationCompositor.draw(lens, 0.5D, texture, 254, 0)) {
                throw new AssertionError("Valid current stencil did not draw");
            }
            if (!Arrays.equals(before, state()) || !projection.equals(RenderSystem.getProjectionMatrix())
                    || !modelView.equals(RenderSystem.getModelViewMatrix())
                    || !Arrays.equals(color, RenderSystem.getShaderColor()) || RenderSystem.getShader() != shader
                    || RenderSystem.getShaderTexture(0) != oldTexture) {
                throw new AssertionError("Texture composition leaked GL or matrix state: before="
                        + Arrays.toString(before) + " after=" + Arrays.toString(state()));
            }
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            if (GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) != oldTexture) {
                throw new AssertionError("Texture slot zero binding was not restored");
            }
            RenderSystem.activeTexture(GL13.GL_TEXTURE3);
            ByteBuffer pixels = BufferUtils.createByteBuffer(32 * 32 * 4);
            GL11.glReadPixels(0, 0, 32, 32, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            ByteBuffer mask = BufferUtils.createByteBuffer(32 * 32);
            GL11.glReadPixels(0, 0, 32, 32, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, mask);
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    boolean inside = x >= 8 && x < 24 && y >= 8 && y < 24;
                    int expected = !inside ? 0x0000FFFF : y < 16
                            ? (x < 16 ? 0xFF0000FF : 0x00FF00FF) : (x < 16 ? 0xFFFF00FF : 0xFF00FFFF);
                    int offset = (y * 32 + x) * 4;
                    int actual = (pixels.get(offset) & 255) << 24 | (pixels.get(offset + 1) & 255) << 16
                            | (pixels.get(offset + 2) & 255) << 8 | (pixels.get(offset + 3) & 255);
                    if (actual != expected || (mask.get(y * 32 + x) & 255) != (inside ? 254 : 0)) {
                        throw new AssertionError("Incorrect pixel or changed stencil at " + x + "," + y
                                + " expected=" + Integer.toHexString(expected) + " actual=" + Integer.toHexString(actual));
                    }
                }
            }
            for (boolean fail : new boolean[]{false, true}) {
                try {
                    VrScopeLateDivisionRenderer.withRestoredRenderState(() -> {
                        RenderSystem.getModelViewStack().setIdentity();
                        RenderSystem.applyModelViewMatrix();
                        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.DISTANCE_TO_ORIGIN);
                        if (fail) throw new ProbeFailure();
                    });
                } catch (ProbeFailure expected) {
                    // The same boundary protects both a successful scope pass and an upstream render exception.
                }
                if (!projection.equals(RenderSystem.getProjectionMatrix())
                        || !modelView.equals(RenderSystem.getModelViewMatrix())) {
                    throw new AssertionError("Matrix restoration failed; exceptional=" + fail);
                }
            }
            shader.clear();
            System.out.println("SCOPE_MAGNIFICATION_GL_PASS clipped_pixels=256 untouched_pixels=768 "
                    + "uv_orientation=pass stencil_preserved=true feedback_rejected=true state_and_matrices=pass");
        } finally {
            shaderSlot.set(null, oldShader);
            RenderSystem.setShader(() -> null);
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glDeleteVertexArrays(vao);
            GL15.glDeleteBuffers(buffer);
            GlStateManager._deleteTexture(texture);
            GlStateManager._deleteTexture(oldTexture);
        }
    }

    private static int[] state() {
        return Arrays.stream(new int[]{GL13.GL_ACTIVE_TEXTURE, GL20.GL_CURRENT_PROGRAM,
                GL30.GL_VERTEX_ARRAY_BINDING, GL15.GL_ARRAY_BUFFER_BINDING,
                GL30.GL_DRAW_FRAMEBUFFER_BINDING, GL30.GL_READ_FRAMEBUFFER_BINDING,
                GL11.GL_STENCIL_WRITEMASK, GL11.GL_STENCIL_FUNC, GL11.GL_STENCIL_REF,
                GL11.GL_STENCIL_PASS_DEPTH_FAIL, GL11.GL_STENCIL_PASS_DEPTH_PASS,
                GL11.GL_DEPTH_TEST, GL11.GL_DEPTH_WRITEMASK, GL11.GL_CULL_FACE, GL11.GL_BLEND
        }).map(GL11::glGetInteger).toArray();
    }

    private static final class ProbeFailure extends RuntimeException {
    }
}
