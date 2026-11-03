package com.thedivisionisac.taczvr.client.vr;

/**
 * Canonical classification for the Vivecraft render-pass names consumed by the physical scope bridge.
 *
 * <p>The bridge API exposes pass names as strings because it must remain optional at runtime. Keeping the conversion
 * in one place prevents each scope component from making slightly different assumptions about unknown, mirror, or
 * desktop passes. Unknown values deliberately fail closed.</p>
 */
public enum VrScopeRenderPass {
    LEFT(0),
    RIGHT(1),
    OTHER(-1);

    private final int eyeIndex;

    VrScopeRenderPass(int eyeIndex) {
        this.eyeIndex = eyeIndex;
    }

    public static VrScopeRenderPass fromName(String passName) {
        if ("LEFT".equals(passName)) {
            return LEFT;
        }
        if ("RIGHT".equals(passName)) {
            return RIGHT;
        }
        return OTHER;
    }

    /**
     * Accepts the enum-like object supplied by optional Vivecraft mixins without loading Vivecraft classes.
     */
    public static VrScopeRenderPass fromObject(Object pass) {
        if (pass instanceof Enum<?> enumPass) {
            return fromName(enumPass.name());
        }
        return fromName(pass == null ? null : String.valueOf(pass));
    }

    public boolean isEye() {
        return this != OTHER;
    }

    public int eyeIndex() {
        return this.eyeIndex;
    }

    public VrScopeRenderPass opposite() {
        return switch (this) {
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
            case OTHER -> OTHER;
        };
    }
}
