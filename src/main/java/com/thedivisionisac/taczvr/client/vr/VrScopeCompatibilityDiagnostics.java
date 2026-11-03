package com.thedivisionisac.taczvr.client.vr;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Render-thread, session-bounded compatibility notices. Normal tracking/menu/request gaps stay silent.
 * Pending hook/pipeline notices require five continuous seconds; each actionable reason is emitted at most once
 * until the owner's disconnect/resource reset, even when ordinary inactive frames interrupt repeated rejection.
 */
final class VrScopeCompatibilityDiagnostics {
    private static final long PENDING_DELAY = TimeUnit.SECONDS.toNanos(5);
    private final Set<String> reported = new HashSet<>();
    private String pendingReason;
    private long pendingSince;

    String notice(String reason, long now) {
        String detail = detail(reason);
        if (detail == null) {
            pendingReason = null;
            return null;
        }
        boolean pending = reason.endsWith("_pending") || reason.endsWith("_not_ready");
        if (pending) {
            if (!reason.equals(pendingReason) || now < pendingSince) {
                pendingReason = reason;
                pendingSince = now;
                return null;
            }
            if (now - pendingSince < PENDING_DELAY) return null;
        } else {
            pendingReason = null;
        }
        return reported.add(reason) ? detail : null;
    }

    private static String detail(String reason) {
        if (reason == null) return null;
        if (reason.equals("unsupported_renderer")) {
            return "The active renderer is outside scope magnification support; test the minimal renderer setup.";
        }
        if (reason.endsWith("_compat_disabled")) {
            return "Active shaders require enableVrScopeOculusCompat (Forge) or enableVrScopeIrisCompat (NeoForge); "
                    + "disable the shader pack to use ordinary scope magnification.";
        }
        if (reason.endsWith("_version_unsupported")) {
            return "Active shaders require the documented Oculus/Iris version; disable the shader pack "
                    + "for ordinary magnification or install the matching loader.";
        }
        if (reason.endsWith("_requires_per_pass_shader_mode") || reason.endsWith("_reload_shaders")) {
            return "Enable Vivecraft disableShaderOptimization and reload shaders to build independent pipelines.";
        }
        if (reason.equals("hooks_pending") || reason.endsWith("_lifecycle_hooks_pending")
                || reason.endsWith("_pipelines_not_ready") || reason.endsWith("_vr_pipeline_api_missing")
                || reason.endsWith("_scope_target_not_square") || reason.endsWith("_scope_shadow_missing")) {
            return "Required scope hooks, targets or pipelines are unavailable; check pinned mod versions and "
                    + "try reloading resources/shaders. Diagnostic option: -Dtaczvr.debugScopeMagnification=true";
        }
        return null;
    }

    void reset() {
        reported.clear();
        pendingReason = null;
        pendingSince = 0;
    }
}
