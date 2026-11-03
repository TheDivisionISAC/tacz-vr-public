package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that stereo passes cannot overwrite one another inside a frame while a restarted frame stream can recover.
 */
class VrScopeAlignmentFrameCacheTest {
    private static final ResourceLocation GUN_ID = ResourceLocation.tryParse("tacz:test_gun");
    private static final ResourceLocation SCOPE_ID = ResourceLocation.tryParse("tacz:test_scope");

    @Test
    void firstEyeWinsUntilFrameAdvancesOrRegresses() {
        VrScopeAlignmentFrameCache cache = new VrScopeAlignmentFrameCache();
        VrScopeAlignmentSample left = sample(40L, 0.01D);
        VrScopeAlignmentSample right = sample(40L, -0.01D);
        VrScopeAlignmentSample next = sample(41L, 0.0D);
        VrScopeAlignmentSample restarted = sample(2L, 0.0D);

        assertTrue(cache.accept(left));
        assertTrue(cache.contains(40L));
        assertFalse(cache.accept(right));
        assertEquals(left, cache.latest().orElseThrow());
        assertTrue(cache.forFrame(39L).isEmpty());
        assertEquals(left, cache.forFrame(40L).orElseThrow());
        assertTrue(cache.accept(next));
        assertEquals(next, cache.latest().orElseThrow());
        assertTrue(cache.accept(restarted));
        assertEquals(restarted, cache.latest().orElseThrow());
    }

    @Test
    void addingEyeOffsetMakesStereoLensPositionInvariant() {
        Vec3 headCenteredLens = new Vec3(0.0D, -0.01D, -0.24D);
        Vec3 leftEyeOffset = new Vec3(-0.032D, 0.0D, 0.0D);
        Vec3 rightEyeOffset = new Vec3(0.032D, 0.0D, 0.0D);
        Vec3 leftViewLens = headCenteredLens.subtract(leftEyeOffset);
        Vec3 rightViewLens = headCenteredLens.subtract(rightEyeOffset);

        VrEyeToHeadTransform leftToHead = VrEyeToHeadTransform.validated(
                leftEyeOffset, new Quaternionf()
        ).orElseThrow();
        VrEyeToHeadTransform rightToHead = VrEyeToHeadTransform.validated(
                rightEyeOffset, new Quaternionf()
        ).orElseThrow();

        assertEquals(headCenteredLens, leftToHead.transformPosition(leftViewLens));
        assertEquals(headCenteredLens, rightToHead.transformPosition(rightViewLens));
    }

    private static VrScopeAlignmentSample sample(long frameIndex, double x) {
        return VrScopeAlignmentSample.measured(
                frameIndex,
                GUN_ID,
                SCOPE_ID,
                0,
                new Vec3(x, 0.0D, -0.25D),
                new Vec3(0.0D, 0.0D, -1.0D)
        ).orElseThrow();
    }
}
