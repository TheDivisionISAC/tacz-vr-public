package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies first-eye ownership, exact one-frame TTL, single-use consumption, and dimension fail-closed behavior. */
class VrScopeRenderRequestCacheTest {
    private static final Object WORLD = new Object();
    private static final Object HELD_ITEM = new Object();
    private static final ResourceLocation OVERWORLD = ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation NETHER = ResourceLocation.tryParse("minecraft:the_nether");
    private static final ResourceLocation GUN_ID = ResourceLocation.tryParse("tacz:test_gun");
    private static final ResourceLocation SCOPE_ID = ResourceLocation.tryParse("tacz:test_scope");

    @Test
    void firstStereoCaptureWinsAndNextFrameCanReplaceIt() {
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();
        VrScopeRenderRequest left = request(40L, 0.01D);
        VrScopeRenderRequest right = request(40L, -0.01D);
        VrScopeRenderRequest next = request(41L, 0.0D);

        assertTrue(offer(cache, left));
        assertFalse(offer(cache, right));
        assertTrue(offer(cache, next));
        assertEquals(next, claim(cache, 42L, OVERWORLD).orElseThrow());
    }

    @Test
    void claimRequiresExactlyFollowingFrameAndIsSingleUse() {
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();
        VrScopeRenderRequest request = request(90L, 0.0D);

        assertTrue(offer(cache, request));
        assertEquals(request, claim(cache, 91L, OVERWORLD).orElseThrow());
        assertTrue(claim(cache, 91L, OVERWORLD).isEmpty());

        assertTrue(offer(cache, request(100L, 0.0D)));
        assertTrue(claim(cache, 102L, OVERWORLD).isEmpty());
    }

    @Test
    void dimensionMismatchAndFrameRestartFailClosed() {
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();

        assertTrue(offer(cache, request(12L, 0.0D)));
        assertTrue(claim(cache, 13L, NETHER).isEmpty());
        assertTrue(claim(cache, 13L, OVERWORLD).isEmpty());

        assertTrue(offer(cache, request(250L, 0.0D)));
        assertTrue(claim(cache, 1L, OVERWORLD).isEmpty());
    }

    @Test
    void gunScopeAndViewMismatchFailClosedAndConsumeCandidate() {
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();

        assertTrue(offer(cache, request(50L, 0.0D)));
        assertTrue(cache.claim(
                51L, WORLD, HELD_ITEM, OVERWORLD, ResourceLocation.tryParse("tacz:other_gun"), SCOPE_ID, 0, 4.0D
        ).isEmpty());
        assertTrue(claim(cache, 51L, OVERWORLD).isEmpty());

        assertTrue(offer(cache, request(60L, 0.0D)));
        assertTrue(cache.claim(
                61L, WORLD, HELD_ITEM, OVERWORLD, GUN_ID, ResourceLocation.tryParse("tacz:other_scope"), 0, 4.0D
        ).isEmpty());

        assertTrue(offer(cache, request(70L, 0.0D)));
        assertTrue(cache.claim(71L, WORLD, HELD_ITEM, OVERWORLD, GUN_ID, SCOPE_ID, 1, 4.0D).isEmpty());
    }

    @Test
    void regressedOfferDropsPendingRequestAndDoesNotReplaceIt() {
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();

        assertTrue(offer(cache, request(80L, 0.0D)));
        assertFalse(offer(cache, request(79L, 0.0D)));
        assertTrue(claim(cache, 81L, OVERWORLD).isEmpty());
        assertTrue(offer(cache, request(82L, 0.0D)));
    }

    @Test
    void consumptionAndClearDoNotAllowTheSameSourceFrameToRearm() {
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();
        VrScopeRenderRequest request = request(90L, 0.0D);
        assertTrue(offer(cache, request));
        assertTrue(claim(cache, 91L, OVERWORLD).isPresent());
        assertFalse(offer(cache, request));
        assertTrue(claim(cache, 91L, OVERWORLD).isEmpty());

        assertTrue(offer(cache, request(91L, 0.0D)));
        cache.clear();
        assertFalse(offer(cache, request(91L, 0.0D)));
        assertTrue(claim(cache, 92L, OVERWORLD).isEmpty());
    }

    @Test
    void duplicateClaimDoesNotConsumeTheNextFramesCapture() {
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();
        assertTrue(offer(cache, request(90L, 0.0D)));
        assertTrue(claim(cache, 91L, OVERWORLD).isPresent());
        assertTrue(offer(cache, request(91L, 0.0D)));
        assertTrue(claim(cache, 91L, OVERWORLD).isEmpty());
        assertTrue(claim(cache, 92L, OVERWORLD).isPresent());
    }

    @Test
    void rejectedClaimCannotBeRearmedByAnotherEye() {
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();
        assertTrue(offer(cache, request(90L, 0.0D)));
        assertTrue(claim(cache, 91L, NETHER).isEmpty());
        assertFalse(offer(cache, request(90L, 0.01D)));
        assertTrue(claim(cache, 91L, OVERWORLD).isEmpty());
    }

    @Test
    void regressedClaimDiscardsPendingWithoutRebasingWatermarks() {
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();
        assertTrue(offer(cache, request(90L, 0.0D)));
        assertTrue(claim(cache, 91L, OVERWORLD).isPresent());
        assertTrue(offer(cache, request(91L, 0.0D)));
        assertTrue(claim(cache, 1L, OVERWORLD).isEmpty());
        assertTrue(claim(cache, 92L, OVERWORLD).isEmpty());
        assertFalse(offer(cache, request(1L, 0.0D)));
    }

    @Test
    void sameViewWithDifferentLiveMagnificationIsRejected() {
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();
        assertTrue(offer(cache, request(90L, 0.0D)));
        assertTrue(cache.claim(91L, WORLD, HELD_ITEM, OVERWORLD, GUN_ID, SCOPE_ID, 0, 6.0D).isEmpty());
        assertFalse(offer(cache, request(90L, 0.0D)));
        assertTrue(offer(cache, request(91L, 0.0D)));
        assertTrue(claim(cache, 92L, OVERWORLD).isPresent());
    }

    @Test
    void replacementHeldItemWithIdenticalIdsIsRejected() {
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();
        assertTrue(offer(cache, request(90L, 0.0D)));
        assertTrue(cache.claim(91L, WORLD, new Object(), OVERWORLD, GUN_ID, SCOPE_ID, 0, 4.0D).isEmpty());
    }

    @Test
    void aNewWorldWithTheSameDimensionCannotUseOldCameraFacts() {
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();
        Object nextWorld = new Object();
        assertTrue(offer(cache, request(90L, 0.0D)));
        assertTrue(cache.claim(91L, nextWorld, HELD_ITEM, OVERWORLD, GUN_ID, SCOPE_ID, 0, 4.0D).isEmpty());
        assertTrue(cache.offer(request(1L, 0.0D), nextWorld, HELD_ITEM));
        assertTrue(cache.claim(2L, nextWorld, HELD_ITEM, OVERWORLD, GUN_ID, SCOPE_ID, 0, 4.0D).isPresent());
    }

    @Test
    void explicitLifecycleResetStartsANewFrameStream() {
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();
        assertTrue(offer(cache, request(90L, 0.0D)));
        assertTrue(claim(cache, 91L, OVERWORLD).isPresent());
        cache.reset();
        assertTrue(offer(cache, request(1L, 0.0D)));
        assertTrue(claim(cache, 2L, OVERWORLD).isPresent());
    }

    @Test
    void invalidIdentityAndMagnificationFailClosed() {
        for (double zoom : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0.0D, -1.0D}) {
            VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();
            assertTrue(offer(cache, request(90L, 0.0D)));
            assertTrue(cache.claim(91L, WORLD, HELD_ITEM, OVERWORLD, GUN_ID, SCOPE_ID, 0, zoom).isEmpty());
        }
        VrScopeRenderRequestCache cache = new VrScopeRenderRequestCache();
        assertFalse(cache.offer(request(90L, 0.0D), null, HELD_ITEM));
        assertFalse(cache.offer(request(90L, 0.0D), WORLD, null));
        assertFalse(cache.offer(null, WORLD, HELD_ITEM));
    }

    private static boolean offer(VrScopeRenderRequestCache cache, VrScopeRenderRequest request) {
        return cache.offer(request, WORLD, HELD_ITEM);
    }

    private static VrScopeRenderRequest request(long frameIndex, double x) {
        return VrScopeRenderRequest.validated(
                frameIndex, OVERWORLD, GUN_ID, SCOPE_ID, 0,
                new Vec3(x, 0.0D, -0.25D),
                VrRotation.validated(0.0D, 0.0D, 0.0D, 1.0D).orElseThrow(),
                4.0D, 1.0D
        ).orElseThrow();
    }

    private static java.util.Optional<VrScopeRenderRequest> claim(
            VrScopeRenderRequestCache cache,
            long frameIndex,
            ResourceLocation dimensionId
    ) {
        return cache.claim(frameIndex, WORLD, HELD_ITEM, dimensionId, GUN_ID, SCOPE_ID, 0, 4.0D);
    }
}
