package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the current-eye projection used to replace TACZ 1.1.8's desktop ocular-circle center and radius mapping.
 */
class VrScopeTaczCircleProjectionTest {
    private static final double TOLERANCE = 1.0E-6D;

    @Test
    void centeredProjectionMapsIntersectingDivisionBoundaryToTaczModifier() {
        VrScopeTaczCircleProjection projection = measured(Vec3.ZERO, Vec3.ZERO);

        assertEquals(0.0D, projection.planeCenterX(), TOLERANCE);
        assertEquals(0.0D, projection.planeCenterY(), TOLERANCE);
        assertEquals(0.09D, projection.safeRadiusModifier(), TOLERANCE);
    }

    @Test
    void movingTheWholeOpticCloserExpandsTheSafeScreenCircle() {
        VrScopeTaczCircleProjection ordinary = measuredAtRelief(0.50D, 0.50D);
        VrScopeTaczCircleProjection close = measuredAtRelief(0.25D, 0.50D);

        assertEquals(ordinary.safeRadiusModifier() * 4.0D / 3.0D, close.safeRadiusModifier(), TOLERANCE);
        assertEquals(0.0D, close.planeCenterY(), TOLERANCE);
    }

    @Test
    void divisionCoveringTheWholeOcularPreservesFullOpening() {
        VrScopeTaczCircleProjection projection = VrScopeTaczCircleProjection.measured(
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, -0.5D),
                plane(new Vec3(0.0D, 0.0D, -0.5D), 0.06D, 0.05D),
                plane(new Vec3(0.0D, 0.0D, -1.0D), 0.14D, 0.12D)
        ).orElseThrow();

        assertEquals(1.0D, projection.safeRadiusModifier(), TOLERANCE);
    }

    @Test
    void mirroredVerticalEyeOffsetsRemainSymmetric() {
        VrScopeTaczCircleProjection above = measured(new Vec3(0.0D, 0.02D, 0.0D), Vec3.ZERO);
        VrScopeTaczCircleProjection below = measured(new Vec3(0.0D, -0.02D, 0.0D), Vec3.ZERO);

        assertEquals(above.safeRadiusModifier(), below.safeRadiusModifier(), TOLERANCE);
        assertEquals(-above.planeCenterY(), below.planeCenterY(), TOLERANCE);
    }

    @Test
    void modelViewInverseSuppliesCameraPositionInCircleCoordinates() {
        Matrix4f modelView = new Matrix4f().translation(0.0F, -0.02F, 0.0F);
        VrScopeTaczCircleProjection fromMatrix = VrScopeTaczCircleProjection.measured(
                modelView,
                new Vec3(0.0D, 0.0D, -0.5D),
                plane(new Vec3(0.0D, 0.0D, -0.5D), 0.06D, 0.05D),
                plane(new Vec3(0.0D, 0.0D, -1.0D), 0.10D, 0.08D)
        ).orElseThrow();
        VrScopeTaczCircleProjection explicit = measured(new Vec3(0.0D, 0.02D, 0.0D), Vec3.ZERO);

        assertEquals(explicit.planeCenterX(), fromMatrix.planeCenterX(), TOLERANCE);
        assertEquals(explicit.planeCenterY(), fromMatrix.planeCenterY(), TOLERANCE);
        assertEquals(explicit.safeRadiusModifier(), fromMatrix.safeRadiusModifier(), TOLERANCE);
    }

    @Test
    void ocularPivotOutsideDivisionProjectionCloses() {
        VrScopeTaczCircleProjection projection = measured(Vec3.ZERO, new Vec3(0.2D, 0.0D, 0.0D));

        assertEquals(0.0D, projection.safeRadiusModifier(), TOLERANCE);
        assertTrue(Math.abs(projection.planeCenterX()) > 0.0D);
    }

    private static VrScopeTaczCircleProjection measured(Vec3 camera, Vec3 ocularOffset) {
        return VrScopeTaczCircleProjection.measured(
                camera,
                new Vec3(ocularOffset.x, ocularOffset.y, -0.5D),
                plane(new Vec3(ocularOffset.x, ocularOffset.y, -0.5D), 0.06D, 0.05D),
                plane(new Vec3(0.0D, 0.0D, -1.0D), 0.10D, 0.08D)
        ).orElseThrow();
    }

    private static VrScopeTaczCircleProjection measuredAtRelief(double relief, double ringToDivision) {
        return VrScopeTaczCircleProjection.measured(
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, -relief),
                plane(new Vec3(0.0D, 0.0D, -relief), 0.06D, 0.05D),
                plane(new Vec3(0.0D, 0.0D, -(relief + ringToDivision)), 0.10D, 0.08D)
        ).orElseThrow();
    }

    private static VrScopeModelAperture.Transformed plane(Vec3 center, double radiusX, double radiusY) {
        return new VrScopeModelAperture.Transformed(
                center,
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(0.0D, 0.0D, -1.0D),
                radiusX,
                radiusY
        );
    }
}
