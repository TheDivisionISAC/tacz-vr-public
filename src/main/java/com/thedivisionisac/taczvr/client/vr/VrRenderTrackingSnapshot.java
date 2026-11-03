package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;

import java.util.Optional;

/**
 * Full render-frame tracking facts used by bounded view-relative stabilization and opt-in diagnostics. Shooting
 * continues to consume the narrower {@link VrShotPose}; this snapshot does not escape into gameplay state.
 */
public record VrRenderTrackingSnapshot(VrShotPose handPose, VrRotation handRotation,
                                       VrShotPose headPose, VrRotation headRotation) {
    public static Optional<VrRenderTrackingSnapshot> validated(
            Vec3 handPosition,
            Vec3 handDirection,
            Quaternionfc handRotation,
            Vec3 headPosition,
            Vec3 headDirection,
            Quaternionfc headRotation
    ) {
        Optional<VrShotPose> validatedHandPose = VrShotPose.validated(handPosition, handDirection);
        Optional<VrRotation> validatedHandRotation = VrRotation.validated(handRotation);
        Optional<VrShotPose> validatedHeadPose = VrShotPose.validated(headPosition, headDirection);
        Optional<VrRotation> validatedHeadRotation = VrRotation.validated(headRotation);
        if (validatedHandPose.isEmpty() || validatedHandRotation.isEmpty()
                || validatedHeadPose.isEmpty() || validatedHeadRotation.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new VrRenderTrackingSnapshot(
                validatedHandPose.get(),
                validatedHandRotation.get(),
                validatedHeadPose.get(),
                validatedHeadRotation.get()
        ));
    }
}
