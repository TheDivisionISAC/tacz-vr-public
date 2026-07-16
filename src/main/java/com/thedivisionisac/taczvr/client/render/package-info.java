/**
 * Controller-owned first-person gun transforms and Bedrock anchor resolution. Render-thread state may be cached only
 * for short-lived calibration data; no renderer object may escape into networking or server code.
 */
package com.thedivisionisac.taczvr.client.render;
