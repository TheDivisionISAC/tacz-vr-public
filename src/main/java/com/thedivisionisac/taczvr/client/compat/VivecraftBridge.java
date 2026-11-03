package com.thedivisionisac.taczvr.client.compat;

import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.client.vr.VrEyeToHeadTransform;
import com.thedivisionisac.taczvr.client.vr.VrRenderTrackingSnapshot;
import com.thedivisionisac.taczvr.client.vr.VrShotPose;
import net.minecraftforge.fml.ModList;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Minimal Vivecraft surface used by the addon. Empty results are normal fallback signals, not errors.
 */
public interface VivecraftBridge {
    static VivecraftBridge unavailable() {
        return NoopVivecraftBridge.INSTANCE;
    }

    static VivecraftBridge create() {
        if (!ModList.get().isLoaded(TaczVr.VIVECRAFT_MOD_ID)) {
            return unavailable();
        }
        try {
            return new SafeVivecraftBridge(new VivecraftApiBridge());
        } catch (LinkageError | RuntimeException error) {
            TaczVr.LOGGER.warn("Vivecraft is present, but its API bridge could not be initialized.", error);
            return unavailable();
        }
    }

    String bridgeName();

    boolean isVrActive();

    boolean isLeftHanded();

    Optional<VrShotPose> getMainHandShotPose();

    Optional<VrShotPose> getMainHandRenderPose();

    /** Full hand/HMD render tracking used by render-only stabilization and explicitly enabled diagnostics. */
    Optional<VrRenderTrackingSnapshot> getRenderTrackingSnapshot();

    /**
     * Rigid transform from the current render eye-view basis into the HMD-center basis.
     * Empty outside Vivecraft's LEFT/RIGHT/CENTER first-person passes.
     */
    Optional<VrEyeToHeadTransform> getCurrentEyeToHeadTransform();

    OptionalLong getRenderFrameIndex();

    /** Stable text label for the current Vivecraft render pass, or a fallback label when unavailable. */
    String getRenderPassName();

    void triggerMainHandHaptic(float durationSeconds);
}
