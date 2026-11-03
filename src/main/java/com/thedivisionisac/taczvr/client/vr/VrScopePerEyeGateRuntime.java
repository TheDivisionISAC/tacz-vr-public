package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Render-thread owner for independent LEFT and RIGHT, author-limited scope apertures. A render pass can only read
 * progress produced by its own eye, so monocular alignment cannot open the other eye's TACZ ocular stencil. Non-eye
 * passes fail closed.
 */
final class VrScopePerEyeGateRuntime {
    private final VrScopeAlignmentGateRuntime left = new VrScopeAlignmentGateRuntime();
    private final VrScopeAlignmentGateRuntime right = new VrScopeAlignmentGateRuntime();

    void record(
            String renderPass,
            VrScopeAlignmentSample sample,
            @Nullable VrScopeAuthorEyeBoxSample authorEyeBox,
            @Nullable VrScopeDivisionProjectionSample divisionProjection,
            Object scopeModel,
            VrScopeAlignmentGateProfile profile,
            long nowNanos
    ) {
        VrScopeAlignmentGateRuntime runtime = this.runtimeFor(renderPass);
        if (runtime != null) {
            runtime.record(sample, authorEyeBox, divisionProjection, scopeModel, profile, nowNanos);
        }
    }

    double progressFor(
            String renderPass,
            Object scopeModel,
            ResourceLocation gunId,
            long frameIndex
    ) {
        Objects.requireNonNull(scopeModel, "scopeModel");
        Objects.requireNonNull(gunId, "gunId");
        VrScopeAlignmentGateRuntime runtime = this.runtimeFor(renderPass);
        return runtime == null ? 0.0D : runtime.progressFor(scopeModel, gunId, frameIndex);
    }

    double renderActivationProgressFor(
            String renderPass,
            Object scopeModel,
            ResourceLocation gunId,
            long frameIndex
    ) {
        Objects.requireNonNull(scopeModel, "scopeModel");
        Objects.requireNonNull(gunId, "gunId");
        VrScopeAlignmentGateRuntime runtime = this.runtimeFor(renderPass);
        return runtime == null ? 0.0D : runtime.renderActivationProgressFor(scopeModel, gunId, frameIndex);
    }

    OptionalCenter circleCenterFor(
            String renderPass,
            Object scopeModel,
            ResourceLocation gunId,
            long frameIndex
    ) {
        Objects.requireNonNull(scopeModel, "scopeModel");
        Objects.requireNonNull(gunId, "gunId");
        VrScopeAlignmentGateRuntime runtime = this.runtimeFor(renderPass);
        return runtime == null
                ? OptionalCenter.empty()
                : runtime.circleCenterFor(scopeModel, gunId, frameIndex);
    }

    void reset() {
        this.left.reset();
        this.right.reset();
    }

    private VrScopeAlignmentGateRuntime runtimeFor(String renderPass) {
        return this.runtimeFor(VrScopeRenderPass.fromName(renderPass));
    }

    private VrScopeAlignmentGateRuntime runtimeFor(VrScopeRenderPass renderPass) {
        return switch (renderPass) {
            case LEFT -> this.left;
            case RIGHT -> this.right;
            case OTHER -> null;
        };
    }

    record OptionalCenter(boolean present, double x, double y) {
        static OptionalCenter empty() {
            return new OptionalCenter(false, 0.0D, 0.0D);
        }

        static OptionalCenter of(double x, double y) {
            return Double.isFinite(x) && Double.isFinite(y)
                    ? new OptionalCenter(true, x, y)
                    : empty();
        }
    }
}
