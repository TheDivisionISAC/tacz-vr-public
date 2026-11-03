package com.thedivisionisac.taczvr.client.control;

/**
 * Pure optical-setting policy for the VR zoom chord. An increment is meaningful only if the authored cycle has
 * a different magnification or view. Duplicate settings stay fixed; equal magnifications with different sight
 * views still cycle. This neither mutates item data nor weakens the render cache's item-identity checks.
 */
final class VrScopeZoomPolicy {
    private VrScopeZoomPolicy() {
    }

    static boolean hasAlternatives(float[] zooms, int[] views) {
        return differentZooms(zooms) || differentViews(views);
    }

    private static boolean differentZooms(float[] values) {
        if (values == null || values.length < 2) return false;
        boolean different = false;
        for (float value : values) {
            if (!Float.isFinite(value) || value <= 0) return false;
            different |= Float.compare(value, values[0]) != 0;
        }
        return different;
    }

    private static boolean differentViews(int[] values) {
        if (values == null || values.length < 2) return false;
        boolean different = false;
        for (int value : values) {
            if (value < 1) return false;
            different |= value != values[0];
        }
        return different;
    }
}
