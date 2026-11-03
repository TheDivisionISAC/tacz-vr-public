package com.thedivisionisac.taczvr.client.vr;

import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;

import static org.junit.jupiter.api.Assertions.*;

/** Preparation starts outside the old opening, without widening it or preparing arbitrary held-gun poses. */
class VrScopePrewarmGateTest {
    private static VrScopeAlignmentSample alignment(long frame, double distance) {
        var source = VrScopeAlignmentGateProfileTest.sample(frame, distance, 0.035D, 20.0D, 5.0D, 20.0D);
        return VrScopeAlignmentSample.measured(frame, source.gunId(), source.scopeId(), 0,
                new Vec3(0, 0, -distance), new Vec3(0, 0, -1)).orElseThrow();
    }

    @Test
    void prewarmsBeforeAcquisitionButDoesNotOpenTheAperture() {
        var pose = alignment(1, 0.2D);
        var eye = VrScopeAuthorEyeBoxGateTest.eyeBox(1, 1.8D);
        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.progressFor(pose, eye));
        assertEquals(0.0D, VrScopeAuthorEyeBoxGate.acquisitionProgressFor(pose, eye, 1.0D));
        assertTrue(VrScopePrewarmGate.permits(pose, eye));
    }

    @Test
    void retainsPreparationThroughPreviewAndUsableOpening() {
        for (double pupil : new double[]{1.8D, 1.2D, 0.75D, 0.2D}) {
            assertTrue(VrScopePrewarmGate.permits(alignment(1, 0.2D),
                    VrScopeAuthorEyeBoxGateTest.eyeBox(1, pupil)));
        }
    }

    @Test
    void rejectsFarOutOfEnvelopeAndInvalidOrStaleFacts() {
        assertFalse(VrScopePrewarmGate.permits(alignment(1, 0.9D), VrScopeAuthorEyeBoxGateTest.eyeBox(1, 0.2D)));
        assertFalse(VrScopePrewarmGate.permits(alignment(1, 0.2D), VrScopeAuthorEyeBoxGateTest.eyeBox(1, 3.1D)));
        assertFalse(VrScopePrewarmGate.permits(alignment(1, 0.2D), VrScopeAuthorEyeBoxGateTest.eyeBox(2, 0.2D)));
        assertFalse(VrScopePrewarmGate.permits(alignment(1, 0.2D), VrScopeAuthorEyeBoxGateTest.eyeBox(1, Double.NaN)));
        assertFalse(VrScopePrewarmGate.permits(null, VrScopeAuthorEyeBoxGateTest.eyeBox(1, 0.2D)));
    }
}
