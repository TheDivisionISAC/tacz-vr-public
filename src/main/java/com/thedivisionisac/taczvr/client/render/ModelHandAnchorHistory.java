package com.thedivisionisac.taczvr.client.render;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Render-thread observations of functional hand anchors, isolated by model identity.
 * Observations survive ordinary frames and are cleared on resource reload or disconnect.
 * This class has no Minecraft or optional Vivecraft dependency.
 */
final class ModelHandAnchorHistory<M> {
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private final Map<M, Integer> observed = new IdentityHashMap<>();

    void recordLeft(M model) {
        record(model, LEFT);
    }

    void recordRight(M model) {
        record(model, RIGHT);
    }

    boolean hasBoth(M model) {
        return model != null && observed.getOrDefault(model, 0) == (LEFT | RIGHT);
    }

    void reset() {
        observed.clear();
    }

    private void record(M model, int hand) {
        if (model != null) {
            observed.merge(model, hand, (previous, captured) -> previous | captured);
        }
    }
}
