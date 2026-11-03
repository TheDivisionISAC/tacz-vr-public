package com.thedivisionisac.taczvr.client.vr;

import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrScopeModelApertureTest {
    private static final double TOLERANCE = 1.0E-6D;

    @Test
    void readsOcularCubeBoundsInModelUnits() {
        BedrockPart ocular = new BedrockPart("ocular");
        ocular.cubes.add(new BedrockCubeBox(
                0.0F, 0.0F,
                -1.0F, -2.0F, 0.5F,
                2.0F, 4.0F, 0.0F,
                0.0F, false, 16.0F, 16.0F
        ));

        VrScopeModelAperture aperture = VrScopeModelAperture.fromOcularPath(List.of(ocular)).orElseThrow();

        assertEquals(0.0D, aperture.localCenter().x, TOLERANCE);
        assertEquals(0.0D, aperture.localCenter().y, TOLERANCE);
        assertEquals(0.03125D, aperture.localCenter().z, TOLERANCE);
        assertEquals(0.0625D, aperture.halfWidth(), TOLERANCE);
        assertEquals(0.125D, aperture.halfHeight(), TOLERANCE);
    }

    @Test
    void appliesAnimatedOcularScaleToWorldRadii() {
        VrScopeModelAperture aperture = new VrScopeModelAperture(
                net.minecraft.world.phys.Vec3.ZERO,
                0.05D,
                0.04D
        );

        VrScopeModelAperture.Transformed transformed = aperture.transform(
                new Matrix4f().scale(2.0F, 0.5F, 1.0F)
        ).orElseThrow();

        assertEquals(0.10D, transformed.radiusX(), TOLERANCE);
        assertEquals(0.02D, transformed.radiusY(), TOLERANCE);
    }

    @Test
    void rejectsOcularsWithoutUsableGeometry() {
        assertTrue(VrScopeModelAperture.fromOcularPath(List.of()).isEmpty());
        assertTrue(VrScopeModelAperture.fromOcularPath(List.of(new BedrockPart("ocular"))).isEmpty());
    }
}
