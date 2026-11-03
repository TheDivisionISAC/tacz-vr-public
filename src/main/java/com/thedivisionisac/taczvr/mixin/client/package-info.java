/**
 * Client integration points for TACZ, Vivecraft, and Minecraft render/input classes. Targets are version-locked to
 * TACZ 1.1.8-hotfix and Vivecraft 1.3.13; every synthetic lambda or SRG selector must be retested in a production jar.
 * Scope-radius wrappers lend a frame-local value to TACZ, restrict their ADS-factor override to that attachment call,
 * and restore the authored model field in {@code finally}.
 */
package com.thedivisionisac.taczvr.mixin.client;
