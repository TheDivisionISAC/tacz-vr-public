package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Immutable hand-off from an eye-pass scope-model capture to the following Vivecraft render frame.
 * Coordinates and rotation are HMD-local; {@code apertureProgress} is the current physical opening, including its
 * acquisition preview. Zero denotes a bounded invisible prewarm request. This object deliberately owns no
 * framebuffer or render state. The unmodified angular field (halfFovTangent) is shared by the world projection and
 * the image plane; the CURRENT eye's stencil clips the image without changing the selected optical magnification.
 */
public record VrScopeRenderRequest(
        long sourceFrameIndex,
        ResourceLocation dimensionId,
        ResourceLocation gunId,
        ResourceLocation scopeId,
        int viewIndex,
        Vec3 scopeViewPositionInHead,
        VrRotation scopeViewRotationInHead,
        double magnification,
        double halfFovTangent,
        double apertureProgress
) {
    public static Optional<VrScopeRenderRequest> validated(
            long sourceFrameIndex,
            ResourceLocation dimensionId,
            ResourceLocation gunId,
            ResourceLocation scopeId,
            int viewIndex,
            Vec3 scopeViewPositionInHead,
            VrRotation scopeViewRotationInHead,
            double magnification,
            double apertureProgress
    ) {
        return validated(sourceFrameIndex, dimensionId, gunId, scopeId, viewIndex, scopeViewPositionInHead,
                scopeViewRotationInHead, magnification, Math.tan(Math.toRadians(35.0D)), apertureProgress);
    }

    public static Optional<VrScopeRenderRequest> validated(
            long sourceFrameIndex, ResourceLocation dimensionId, ResourceLocation gunId, ResourceLocation scopeId,
            int viewIndex, Vec3 scopeViewPositionInHead, VrRotation scopeViewRotationInHead,
            double magnification, double halfFovTangent, double apertureProgress
    ) {
        Optional<VrRotation> normalizedRotation = scopeViewRotationInHead == null
                ? Optional.empty()
                : VrRotation.validated(
                        scopeViewRotationInHead.x(), scopeViewRotationInHead.y(),
                        scopeViewRotationInHead.z(), scopeViewRotationInHead.w()
                );
        if (sourceFrameIndex < 0L || dimensionId == null || gunId == null || scopeId == null || viewIndex < 0
                || !isFinite(scopeViewPositionInHead) || normalizedRotation.isEmpty()
                || !Double.isFinite(magnification) || magnification <= 0.0D
                || !VrScopeWorldView.validHalfTan(halfFovTangent)
                || !Double.isFinite(apertureProgress) || apertureProgress < 0.0D || apertureProgress > 1.0D) {
            return Optional.empty();
        }
        return Optional.of(new VrScopeRenderRequest(
                sourceFrameIndex,
                dimensionId,
                gunId,
                scopeId,
                viewIndex,
                scopeViewPositionInHead,
                normalizedRotation.get(),
                magnification,
                halfFovTangent,
                apertureProgress
        ));
    }

    private static boolean isFinite(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }
}
