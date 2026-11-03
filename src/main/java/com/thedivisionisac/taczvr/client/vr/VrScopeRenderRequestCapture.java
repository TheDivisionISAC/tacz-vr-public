package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Pure coordinate adapter for the render-thread C3 hand-off. It extracts TACZ's animated {@code scope_view} pose from
 * the current eye-view matrix, converts it exactly once into the HMD-center basis, and owns no cache or render state.
 */
final class VrScopeRenderRequestCapture {
    private VrScopeRenderRequestCapture() {
    }

    static Optional<VrScopeRenderRequest> capture(
            long frameIndex,
            ResourceLocation dimensionId,
            ResourceLocation gunId,
            ResourceLocation scopeId,
            int viewIndex,
            Matrix4fc scopeViewMatrix,
            VrEyeToHeadTransform eyeToHead,
            double magnification,
            double apertureProgress
    ) {
        return capture(frameIndex, dimensionId, gunId, scopeId, viewIndex, scopeViewMatrix, eyeToHead, magnification,
                Math.tan(Math.toRadians(35.0D)), apertureProgress);
    }

    static Optional<VrScopeRenderRequest> capture(
            long frameIndex, ResourceLocation dimensionId, ResourceLocation gunId, ResourceLocation scopeId,
            int viewIndex, Matrix4fc scopeViewMatrix, VrEyeToHeadTransform eyeToHead,
            double magnification, double halfFovTangent, double apertureProgress
    ) {
        if (scopeViewMatrix == null || eyeToHead == null) {
            return Optional.empty();
        }
        Vector3f eyePosition = scopeViewMatrix.getTranslation(new Vector3f());
        Quaternionf eyeRotation = scopeViewMatrix.getUnnormalizedRotation(new Quaternionf());
        if (!eyePosition.isFinite() || !eyeRotation.isFinite()) {
            return Optional.empty();
        }
        Optional<VrRotation> headRotation = eyeToHead.transformRotation(eyeRotation);
        if (headRotation.isEmpty()) {
            return Optional.empty();
        }
        Vec3 headPosition = eyeToHead.transformPosition(new Vec3(
                eyePosition.x,
                eyePosition.y,
                eyePosition.z
        ));
        return VrScopeRenderRequest.validated(
                frameIndex,
                dimensionId,
                gunId,
                scopeId,
                viewIndex,
                headPosition,
                headRotation.get(),
                magnification,
                halfFovTangent,
                apertureProgress
        );
    }
}
