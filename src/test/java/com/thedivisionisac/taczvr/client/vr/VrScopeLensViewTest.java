package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Checks image-plane scale, roll, and invalid geometry; opening animation must not feed back into zoom. */
class VrScopeLensViewTest {
    private static VrScopeModelAperture.Transformed aperture(double z) {
        return new VrScopeModelAperture.Transformed(new Vec3(0, 0, z), new Vec3(1, 0, 0),
                new Vec3(0, 1, 0), new Vec3(0, 0, -1), 0.02, 0.02);
    }

    @Test
    void fieldUsesUnshrunkOcularBoundsAndAccountsForModelViewScale() {
        VrScopeLensView normal = VrScopeLensView.measured(new Matrix4f(), new Matrix4f(),
                aperture(-0.2), aperture(-0.25)).orElseThrow();
        VrScopeLensView scaled = VrScopeLensView.measured(new Matrix4f().scale(2), new Matrix4f(),
                aperture(-0.2), aperture(-0.25)).orElseThrow();
        assertEquals(0.25, normal.halfTan(), 1.0E-6);
        assertEquals(normal.halfTan(), scaled.halfTan(), 1.0E-6);
        assertEquals(-0.5, scaled.center().z, 1.0E-6);
    }

    @Test
    void theSameProjectionAndPlaneFieldPreserveAngularMagnificationAtDifferentDistances() {
        double field = 0.25;
        double magnification = 4;
        double sourceSlope = 0.01;
        double imageU = 0.5 + sourceSlope * magnification / (2 * field);
        for (double distance : new double[]{0.1, 0.25, 0.6}) {
            VrScopeLensView lens = new VrScopeLensView(new Vec3(0, 0, -distance), new VrRotation(0, 0, 0, 1), field);
            var corners = lens.corners(field);
            double imageX = corners.get(0).x + (corners.get(1).x - corners.get(0).x) * imageU;
            assertEquals(sourceSlope * magnification, imageX / distance, 1.0E-6);
        }
    }

    @Test
    void imagePlaneFollowsScopeRollAndRejectsCornersBehindTheEye() {
        VrScopeLensView rolled = new VrScopeLensView(new Vec3(0, 0, -0.25),
                VrRotation.validated(new Quaternionf().rotationZ((float) Math.PI / 2)).orElseThrow(), 0.25);
        assertTrue(rolled.corners(0.25).get(1).y > rolled.corners(0.25).get(0).y);
        VrScopeLensView behind = new VrScopeLensView(new Vec3(0, 0, 0.25), rolled.rotation(), 0.25);
        assertTrue(behind.corners(0.25).isEmpty());
        assertTrue(VrScopeLensView.measured(new Matrix4f(), new Matrix4f(), aperture(0), aperture(-0.2)).isEmpty());
    }
}
