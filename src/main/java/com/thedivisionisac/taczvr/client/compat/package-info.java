/**
 * Fail-closed wrappers around Vivecraft's public client API. All optional Vivecraft linkage is contained here; callers
 * consume the bridge interface and must preserve vanilla TACZ behavior when it returns an empty result. The optional
 * scope adapter owns native telescope scheduling and temporary eye-pose conversion; its pure transaction and optical
 * geometry live in client.vr. It never modifies stored tracking poses and rejects competing renderer integrations.
 * The opt-in Oculus adapter verifies the locked reflective pipeline API, independent buffers and completed final
 * color stages; its lifecycle callbacks discard per-frame references on resource/session reset.
 */
package com.thedivisionisac.taczvr.client.compat;
