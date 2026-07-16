package com.thedivisionisac.taczvr.client.compat;

import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.client.vr.VrShotPose;
import net.minecraftforge.fml.ModList;

import java.util.Optional;

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

    void triggerMainHandHaptic(float durationSeconds);
}
