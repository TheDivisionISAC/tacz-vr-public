/**
 * Server-authoritative VR shot validation and single-use context transport. Mutable stores are confined to the server
 * thread, keyed by player and TACZ timestamp, and cleared on lifecycle boundaries.
 */
package com.thedivisionisac.taczvr.server.shot;
