/**
 * Immutable client shot snapshots, adaptive aim stabilization, and the short-lived muzzle-length cache. Snapshot
 * capture happens before TACZ's asynchronous send task so that worker code never reads live VR hardware state.
 */
package com.thedivisionisac.taczvr.client.vr;
