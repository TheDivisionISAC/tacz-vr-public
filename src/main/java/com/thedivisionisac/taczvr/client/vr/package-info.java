/**
 * Immutable client shot snapshots, adaptive aim stabilization, short-lived muzzle data, and render-frame optical
 * alignment diagnostics plus a parameterized physical-scope alignment score and gate state. Gameplay snapshots are
 * captured with muzzle and source identity before TACZ's asynchronous task; VR packet pairs return to the client
 * thread for session/held-item validation and ordered sending. Optical diagnostics and explicitly enabled bounded-rate
 * telemetry remain render-thread-only. The default-off physical ocular prototype exposes a current-frame radius
 * multiplier and a strictly scoped TACZ ADS-factor override to a thin render wrapper. Its optional late division
 * replay owns only a short-lived render-thread frame capture and restores the borrowed framebuffer, viewport,
 * matrix, shader, color/depth, blend, cull, and stencil state before returning to Vivecraft; it owns no global
 * aiming state and skips targets whose stencil is already owned. Request consumption retains frame watermarks and
 * checks world/held-item identity and live magnification; lifecycle reset explicitly starts a new stream. Resource
 * reloads and client disconnects clear all optical observations and pending replay frames. Experimental magnification
 * owns a single complete-frame camera/projection transaction and per-eye image planes; it composes only within the
 * current TACZ stencil and keeps Vivecraft types behind client.compat. The native world pass and composition remain
 * default-off, with hook readiness, renderer exclusions, stale-frame rejection and a latched failure fallback.
 */
package com.thedivisionisac.taczvr.client.vr;
