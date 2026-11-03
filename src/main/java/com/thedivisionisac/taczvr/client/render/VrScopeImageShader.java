package com.thedivisionisac.taczvr.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.thedivisionisac.taczvr.TaczVr;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/**
 * Client render-thread shader slot for opaque RGB scope composition. The game resource lifecycle registers,
 * replaces and closes shader instances; this slot owns no independent GL lifetime and is null before registration.
 * World-target alpha is not coverage: only the current ocular stencil may expose the ordinary eye image.
 * Resource loading failure clears the slot and logs once per reload; composition then safely skips the image.
 */
public final class VrScopeImageShader {
    private static ShaderInstance shader;

    private VrScopeImageShader() {
    }

    public static void register(RegisterShadersEvent event) {
        shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(TaczVr.MOD_ID, "scope_image"),
                    DefaultVertexFormat.POSITION_TEX), loaded -> shader = loaded);
        } catch (IOException error) {
            TaczVr.LOGGER.warn("Scope image shader unavailable; skipping magnification composition until resource reload", error);
        }
    }

    public static ShaderInstance get() {
        return shader;
    }
}
