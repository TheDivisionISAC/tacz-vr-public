package com.thedivisionisac.taczvr.client.vr;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thedivisionisac.taczvr.client.render.VrScopeImageShader;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.List;

/**
 * Draws a scope texture only inside TACZ's CURRENT inverted ocular stencil, immediately before its division. The
 * same hook executes in ordinary attachment rendering and late replay. It uses a private builder, never rebinds a
 * framebuffer, and restores texture/program/buffer state in addition to the common audited GL/matrix scope.
 */
final class VrScopeMagnificationCompositor {
    private static final BufferBuilder QUAD = new BufferBuilder(256);

    private VrScopeMagnificationCompositor() {
    }

    static boolean draw(VrScopeLensView lens, double halfTan, int texture, int stencilReference, int sourceFramebuffer) {
        if (!GL11.glIsEnabled(GL11.GL_STENCIL_TEST) || GL11.glGetInteger(GL11.GL_STENCIL_FUNC) != GL11.GL_EQUAL
                || GL11.glGetInteger(GL11.GL_STENCIL_REF) != stencilReference
                || GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK) != 0xFF
                || GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == sourceFramebuffer
                || texture <= 0 || QUAD.building()) return false;
        List<Vec3> points = lens.corners(halfTan);
        if (points.size() != 4 || VrScopeImageShader.get() == null) return false;

        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int shaderTexture = RenderSystem.getShaderTexture(0);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.activeTexture(activeTexture);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            VrScopeLateDivisionRenderer.withRestoredRenderState(() -> {
                RenderSystem.getModelViewStack().setIdentity();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.disableCull();
                RenderSystem.disableBlend();
                RenderSystem.stencilMask(0);
                RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.setShader(VrScopeImageShader::get);
                RenderSystem.setShaderTexture(0, texture);
                QUAD.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                vertex(points.get(0), 0, 0);
                vertex(points.get(1), 1, 0);
                vertex(points.get(2), 1, 1);
                vertex(points.get(3), 0, 1);
                BufferUploader.drawWithShader(QUAD.end());
            });
        } finally {
            QUAD.discard();
            RenderSystem.setShaderTexture(0, shaderTexture);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(boundTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, boundTexture);
            RenderSystem.activeTexture(activeTexture);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vertexArray);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
            BufferUploader.invalidate();
        }
        return true;
    }

    private static void vertex(Vec3 point, float u, float v) {
        QUAD.vertex(point.x, point.y, point.z).uv(u, v).endVertex();
    }
}
