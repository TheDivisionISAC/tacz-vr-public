package com.thedivisionisac.taczvr.client.vr;

/**
 * Render-thread proof that a particular Iris pipeline began and finished in the current VR frame. Opaque pipeline
 * references cannot be substituted across passes or reloads. This owns no native shader types or GL resources.
 */
public final class VrScopeShaderPassState {
    public enum Pass { SCOPE, LEFT, RIGHT }
    private long frame = Long.MIN_VALUE;
    private final Object[] pipelines = new Object[3];
    private final boolean[] begun = new boolean[3];
    private final boolean[] completed = new boolean[3];
    private final boolean[] invalid = new boolean[3];

    public void reset(long frame, Object scope, Object left, Object right) {
        this.frame = frame;
        pipelines[0] = scope;
        pipelines[1] = left;
        pipelines[2] = right;
        java.util.Arrays.fill(begun, false);
        java.util.Arrays.fill(completed, false);
        java.util.Arrays.fill(invalid, false);
    }

    public boolean begin(long frame, Pass pass, Object pipeline) {
        if (!matches(frame, pass, pipeline) || begun[pass.ordinal()]) {
            invalidateCurrent(frame, pass);
            return false;
        }
        begun[pass.ordinal()] = true;
        return true;
    }

    public boolean finish(long frame, Pass pass, Object pipeline, boolean valid) {
        if (!matches(frame, pass, pipeline)) {
            invalidateCurrent(frame, pass);
            return false;
        }
        completed[pass.ordinal()] = valid && begun[pass.ordinal()];
        return completed[pass.ordinal()];
    }

    public boolean complete(long frame, Pass pass, Object pipeline) {
        return matches(frame, pass, pipeline) && completed[pass.ordinal()];
    }

    private boolean matches(long frame, Pass pass, Object pipeline) {
        return pass != null && frame == this.frame && !invalid[pass.ordinal()]
                && pipeline != null && pipeline == pipelines[pass.ordinal()];
    }

    private void invalidateCurrent(long frame, Pass pass) {
        if (frame == this.frame && pass != null) invalid[pass.ordinal()] = true;
    }
}
