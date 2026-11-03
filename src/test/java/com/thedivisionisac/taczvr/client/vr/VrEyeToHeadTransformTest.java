package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrEyeToHeadTransformTest {
    private static final double EPSILON = 1.0E-6D;

    @Test
    void convertsCantedEyePositionAndDirectionIntoHeadBasis() {
        VrEyeToHeadTransform transform = VrEyeToHeadTransform.validated(
                new Vec3(0.03D, 0.0D, 0.0D),
                new Quaternionf().rotationY((float) (Math.PI / 2.0D))
        ).orElseThrow();

        assertVecEquals(new Vec3(-0.97D, 0.0D, 0.0D),
                transform.transformPosition(new Vec3(0.0D, 0.0D, -1.0D)));
        assertVecEquals(new Vec3(-1.0D, 0.0D, 0.0D),
                transform.transformDirection(new Vec3(0.0D, 0.0D, -1.0D)));
    }

    @Test
    void composesEyeLocalRotationIntoHeadBasis() {
        VrEyeToHeadTransform transform = VrEyeToHeadTransform.validated(
                Vec3.ZERO,
                new Quaternionf().rotationY((float) (Math.PI / 2.0D))
        ).orElseThrow();
        VrRotation result = transform.transformRotation(
                new Quaternionf().rotationX((float) (Math.PI / 2.0D))
        ).orElseThrow();

        Vec3 forward = rotate(result, new Vec3(0.0D, 0.0D, -1.0D));
        assertVecEquals(new Vec3(0.0D, 1.0D, 0.0D), forward);
    }

    @Test
    void rejectsNonFiniteTranslationAndDegenerateRotation() {
        assertTrue(VrEyeToHeadTransform.validated(
                new Vec3(Double.NaN, 0.0D, 0.0D),
                new Quaternionf()
        ).isEmpty());
        assertTrue(VrEyeToHeadTransform.validated(
                Vec3.ZERO,
                new Quaternionf(0.0F, 0.0F, 0.0F, 0.0F)
        ).isEmpty());
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }

    private static Vec3 rotate(VrRotation rotation, Vec3 vector) {
        Quaternionf quaternion = rotation.toQuaternion();
        org.joml.Vector3f result = quaternion.transform(new org.joml.Vector3f(
                (float) vector.x,
                (float) vector.y,
                (float) vector.z
        ));
        return new Vec3(result.x, result.y, result.z);
    }
}
