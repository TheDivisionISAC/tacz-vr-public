package com.thedivisionisac.taczvr.client.vr;

/**
 * Owns the temporary C2 physical-scope profile shared by diagnostics and the default-off ocular-gate prototype.
 * These bounds bracket one validated AWP/LPVO HMD observation and are deliberately not exposed as production
 * configuration until representative scopes and gun packs complete the same gate.
 */
final class VrScopeAlignmentGateTuning {
    static final String PROFILE_NAME = "c2b-c1-observation-v1";
    private static final VrScopeAlignmentGateProfile OBSERVATION_PROFILE = new VrScopeAlignmentGateProfile(
            new VrScopeAlignmentGateProfile.EyeReliefBand(0.03D, 0.07D, 0.18D, 0.30D),
            new VrScopeAlignmentGateProfile.UpperFalloff(0.05D, 0.12D),
            new VrScopeAlignmentGateProfile.UpperFalloff(25.0D, 45.0D),
            new VrScopeAlignmentGateProfile.UpperFalloff(12.0D, 30.0D),
            new VrScopeAlignmentGateProfile.UpperFalloff(25.0D, 45.0D),
            0.75D,
            0.35D,
            0.08D,
            0.12D,
            0.25D
    );

    private VrScopeAlignmentGateTuning() {
    }

    static VrScopeAlignmentGateProfile observationProfile() {
        return OBSERVATION_PROFILE;
    }
}
