package com.thedivisionisac.taczvr.client.vr;

import java.util.Objects;

/**
 * Pure, bounded preparation envelope outside the visible eye box. It spends no GL work itself and never changes
 * the physical aperture. A near, forward-facing scope may prepare next frame's texture before acquisition opens;
 * invalid identities, rear views and distant or badly misaligned scopes cannot keep a world pass alive.
 */
final class VrScopePrewarmGate {
    private VrScopePrewarmGate() {
    }

    static boolean permits(VrScopeAlignmentSample alignment, VrScopeAuthorEyeBoxSample eyeBox) {
        if (alignment == null || eyeBox == null || !eyeBox.forwardIntersection()
                || alignment.frameIndex() != eyeBox.frameIndex() || alignment.viewIndex() != eyeBox.viewIndex()
                || alignment.gunId() == null || alignment.scopeId() == null
                || !Objects.equals(alignment.gunId(), eyeBox.gunId())
                || !Objects.equals(alignment.scopeId(), eyeBox.scopeId())) return false;
        double axial = VrScopeAuthorEyeBoxGate.scopeViewAxialDistanceRatio(eyeBox);
        double lateral = VrScopeAuthorEyeBoxGate.scopeViewLateralDistanceRatio(eyeBox);
        return alignment.viewForwardDistance() > 0.0D && alignment.lensDistance() > 0.0D
                && alignment.lensDistance() < 0.8D
                && alignment.opticalAxisAngleDegrees() >= 0.0D && alignment.opticalAxisAngleDegrees() < 45.0D
                && alignment.coaxialAngleDegrees() >= 0.0D && alignment.coaxialAngleDegrees() < 40.0D
                && eyeBox.pupilRadius() >= 0.0D && eyeBox.pupilRadius() < 3.0D
                && axial >= 0.0D && axial < 1.5D && lateral >= 0.0D && lateral < 0.7D;
    }
}
