package com.thedivisionisac.taczvr.client.render;

/**
 * Keeps support-hand proximity classification independent from render matrices so it can be tested directly.
 */
final class VrSupportHandSnapPolicy {
    private VrSupportHandSnapPolicy() {
    }

    static boolean shouldAttach(boolean supportSnapEnabled, boolean nearMainHandSnapEnabled,
                                boolean alternateSupportEligible,
                                float leftAnchorDistance, float mainHandDistance, float threshold) {
        if (!supportSnapEnabled) {
            return false;
        }
        if (leftAnchorDistance <= threshold) {
            return true;
        }
        return nearMainHandSnapEnabled && alternateSupportEligible && mainHandDistance <= threshold;
    }
}
