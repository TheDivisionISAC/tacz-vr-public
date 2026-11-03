package com.thedivisionisac.taczvr.client.vr;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Preserves TACZ's exact attachment render type while making a stencil-clipped division independent of world
 * depth. Changing GL state around vertex submission is insufficient because {@link RenderType#setupRenderState()}
 * runs again when TACZ flushes the buffer. This proxy applies the depth override inside that real draw setup and
 * restores the caller's prior state after the draw.
 */
public final class VrScopeDivisionRenderType {
    private static final Map<RenderType, RenderType> CACHE = new IdentityHashMap<>();
    private static final ThreadLocal<Deque<DepthState>> DEPTH_STATES =
            ThreadLocal.withInitial(ArrayDeque::new);

    private VrScopeDivisionRenderType() {
    }

    public static RenderType overlay(RenderType original) {
        Objects.requireNonNull(original, "original");
        return CACHE.computeIfAbsent(original, VrScopeDivisionRenderType::create);
    }

    public static void reset() {
        CACHE.clear();
        DEPTH_STATES.remove();
    }

    private static RenderType create(RenderType original) {
        return new RenderType(
                "taczvr_scope_division_overlay[" + original + "]",
                original.format(),
                original.mode(),
                original.bufferSize(),
                original.affectsCrumbling(),
                false,
                () -> setup(original),
                () -> clear(original)
        ) {
        };
    }

    private static void setup(RenderType original) {
        Deque<DepthState> states = DEPTH_STATES.get();
        DepthState previous = DepthState.capture();
        states.push(previous);
        try {
            original.setupRenderState();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        } catch (RuntimeException | Error failure) {
            states.pop();
            try {
                // RenderType setup is not transactional. If a custom/third-party layer fails halfway through,
                // execute its normal clear hook before restoring the depth state owned by this proxy.
                original.clearRenderState();
            } catch (RuntimeException | Error cleanupFailure) {
                // Preserve the setup failure as the primary diagnostic while still retaining evidence of cleanup
                // failure for the caller/logging layer.
                failure.addSuppressed(cleanupFailure);
            } finally {
                restoreAndRelease(previous, states);
            }
            throw failure;
        }
    }

    private static void clear(RenderType original) {
        Deque<DepthState> states = DEPTH_STATES.get();
        DepthState previous = states.isEmpty() ? null : states.pop();
        try {
            original.clearRenderState();
        } finally {
            if (previous != null) {
                restoreAndRelease(previous, states);
            } else if (states.isEmpty()) {
                DEPTH_STATES.remove();
            }
        }
    }

    private static void restoreAndRelease(DepthState state, Deque<DepthState> states) {
        RenderSystem.depthFunc(state.function());
        RenderSystem.depthMask(state.writeEnabled());
        if (state.testEnabled()) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        if (states.isEmpty()) {
            DEPTH_STATES.remove();
        }
    }

    private record DepthState(boolean testEnabled, boolean writeEnabled, int function) {
        private static DepthState capture() {
            return new DepthState(
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
            );
        }
    }
}
