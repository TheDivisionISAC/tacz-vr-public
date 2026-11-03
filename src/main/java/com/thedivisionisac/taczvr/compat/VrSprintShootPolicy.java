package com.thedivisionisac.taczvr.compat;

/**
 * Narrows TACZ's sprint-fire compatibility change to players that are actively using VR. The caller remains
 * responsible for determining VR state; this policy changes only the sprint timer observed by TACZ's shoot
 * eligibility checks and never mutates player movement or sprint synchronization.
 */
public final class VrSprintShootPolicy {
    private VrSprintShootPolicy() {
    }

    public static float effectiveSprintTime(float originalSprintTime, boolean vrPlayer) {
        return vrPlayer ? 0.0F : originalSprintTime;
    }
}
