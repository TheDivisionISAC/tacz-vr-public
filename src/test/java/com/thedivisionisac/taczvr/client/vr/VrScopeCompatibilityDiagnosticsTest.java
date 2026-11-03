package com.thedivisionisac.taczvr.client.vr;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VrScopeCompatibilityDiagnosticsTest {
    @Test
    void persistentFailuresDoNotSpamAcrossOrdinaryFrameGaps() {
        var notices = new VrScopeCompatibilityDiagnostics();
        assertNotNull(notices.notice("iris_compat_disabled", 0));
        assertNull(notices.notice("inactive", 1));
        assertNull(notices.notice("iris_compat_disabled", 2));
        assertNotNull(notices.notice("iris_version_unsupported", 3));
        notices.reset();
        assertNotNull(notices.notice("iris_compat_disabled", 4));
    }

    @Test
    void pendingHooksNeedContinuousTimeAndResetTheirTimerOnGaps() {
        var notices = new VrScopeCompatibilityDiagnostics();
        assertNull(notices.notice("hooks_pending", 0));
        assertNull(notices.notice("hooks_pending", 4_999_999_999L));
        assertNull(notices.notice("inactive", 5_000_000_000L));
        assertNull(notices.notice("hooks_pending", 6_000_000_000L));
        assertNotNull(notices.notice("hooks_pending", 11_000_000_000L));
        assertNull(notices.notice("hooks_pending", 12_000_000_000L));
    }

    @Test
    void ordinaryReasonsAreSilentAndBothLoaderHintsAreActionable() {
        var notices = new VrScopeCompatibilityDiagnostics();
        for (String reason : new String[]{"inactive", "scheduled", "no_magnified_scope",
                "no_previous_frame_request", "pass_or_tracking_unavailable", "invalid_view_or_target"}) {
            assertNull(notices.notice(reason, 0));
        }
        assertTrue(notices.notice("oculus_shared_buffers_reload_shaders", 1).contains("disableShaderOptimization"));
        assertTrue(notices.notice("iris_requires_per_pass_shader_mode", 2).contains("reload"));
    }
}
