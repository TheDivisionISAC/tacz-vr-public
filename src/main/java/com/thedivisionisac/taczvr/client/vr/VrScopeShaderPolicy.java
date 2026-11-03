package com.thedivisionisac.taczvr.client.vr;

/**
 * Shared Forge/NeoForge policy for choosing ordinary scope rendering versus the guarded shader adapter.
 * Merely installing a shader loader must not make its optional active-shader settings gate ordinary rendering.
 * This pure policy owns no loader classes or lifecycle state; active shader pipeline validation remains separate.
 */
public final class VrScopeShaderPolicy {
    public enum Decision { ORDINARY, SHADER, COMPAT_DISABLED, VERSION_UNSUPPORTED }

    private VrScopeShaderPolicy() {
    }

    public static Decision decide(boolean shaderActive, boolean compatEnabled, boolean supportedVersion) {
        if (!shaderActive) return Decision.ORDINARY;
        if (!compatEnabled) return Decision.COMPAT_DISABLED;
        return supportedVersion ? Decision.SHADER : Decision.VERSION_UNSUPPORTED;
    }
}
