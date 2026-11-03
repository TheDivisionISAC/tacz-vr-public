package com.thedivisionisac.taczvr.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Combines the real visibility policy with per-hand draw ownership. The counter models native and post
 * callbacks only: it does not load transformed classes, execute Mixin hooks, or prove injection matching.
 */
class VrHandRenderPlanTest {
    @Test void mixedRealityReplacementDrawsOnlyThePostHand() {
        boolean visible = VrHandVisibilityPolicy.allows(
                false, true, false, false, true, true, false, false);
        assertTrue(visible);
        var plan = VrHandRenderPlan.select(visible, false, true);
        assertEquals(VrHandRenderPlan.REPLACE, plan);
        assertEquals(new DrawCount(0, 1), countDraws(plan, visible, true, true));
    }

    @Test void hidingWinsOverReplacementInFirstPersonAndMixedReality() {
        boolean[] visiblePasses = {visibleFirstPerson(),
                VrHandVisibilityPolicy.allows(false, true, false, false, true, true, false, false)};
        for (boolean visible : visiblePasses) {
            assertTrue(visible);
            var plan = VrHandRenderPlan.select(visible, true, true);
            assertEquals(VrHandRenderPlan.HIDE, plan);
            assertFalse(plan.allowsPostRender(true));
            assertEquals(new DrawCount(0, 0), countDraws(plan, visible, true, true));
        }
    }

    @Test void upstreamHiddenHandsCannotBeResurrected() {
        Scenario[] hidden = {
                new Scenario("first-person setting", VrHandVisibilityPolicy.allows(
                        true, false, false, false, true, false, false, false)),
                new Scenario("complete body arms", VrHandVisibilityPolicy.allows(
                        true, false, false, true, true, true, false, false)),
                new Scenario("invisible", VrHandVisibilityPolicy.allows(
                        true, false, false, true, true, false, true, false)),
                new Scenario("item suppression", VrHandVisibilityPolicy.allows(
                        true, false, false, true, true, false, false, true)),
                new Scenario("MR disabled", VrHandVisibilityPolicy.allows(
                        false, true, false, true, false, false, false, false)),
                new Scenario("camera", VrHandVisibilityPolicy.allows(
                        false, false, true, true, true, false, false, false))
        };
        for (Scenario scenario : hidden) {
            assertFalse(scenario.visible(), scenario.name());
            var plan = VrHandRenderPlan.select(scenario.visible(), false, true);
            assertEquals(VrHandRenderPlan.KEEP, plan, scenario.name());
            assertFalse(plan.allowsPostRender(true), scenario.name());
            assertEquals(new DrawCount(0, 0),
                    countDraws(plan, scenario.visible(), true, true), scenario.name());
        }
    }

    @Test void unmatchedWrapperKeepsNativeWithoutAddingPostDraw() {
        boolean visible = visibleFirstPerson();
        var plan = VrHandRenderPlan.select(visible, false, true);
        assertEquals(VrHandRenderPlan.REPLACE, plan);
        assertFalse(plan.allowsPostRender(false));
        assertEquals(new DrawCount(1, 0), countDraws(plan, visible, true, false));
    }

    @Test void unreachedNativeCallCannotAuthorizePostDraw() {
        boolean visible = visibleFirstPerson();
        var plan = VrHandRenderPlan.select(visible, false, true);
        assertEquals(new DrawCount(0, 0), countDraws(plan, visible, false, true));
    }

    @Test void keepPlanPreservesNativeAndNeverRequestsPostDraw() {
        boolean visible = visibleFirstPerson();
        var plan = VrHandRenderPlan.select(visible, false, false);
        assertEquals(VrHandRenderPlan.KEEP, plan);
        assertFalse(plan.suppressesOriginal());
        assertFalse(plan.allowsPostRender(false));
        assertFalse(plan.allowsPostRender(true));
        assertEquals(new DrawCount(1, 0), countDraws(plan, visible, true, true));
    }

    @Test void separateHandsCannotBorrowSuppressionEvidence() {
        boolean visible = visibleFirstPerson();
        var main = VrHandRenderPlan.select(visible, false, true);
        var support = VrHandRenderPlan.select(visible, false, true);
        // Independent callback simulations, not a test of @Share's implementation.
        assertEquals(new DrawCount(0, 1), countDraws(main, visible, true, true));
        assertEquals(new DrawCount(1, 0), countDraws(support, visible, true, false));
        assertEquals(new DrawCount(0, 0), countDraws(support, visible, false, true));
    }

    @Test void policyMatrixAllowsAtMostOneDrawPerInvocation() {
        boolean[] visibility = {visibleFirstPerson(),
                VrHandVisibilityPolicy.allows(false, true, false, false, true, false, false, false),
                VrHandVisibilityPolicy.allows(true, false, false, false, true, false, false, false)};
        boolean[] choices = {false, true};
        for (boolean visible : visibility) for (boolean hide : choices) for (boolean replace : choices)
            for (boolean reached : choices) for (boolean matched : choices) {
                var plan = VrHandRenderPlan.select(visible, hide, replace);
                DrawCount count = countDraws(plan, visible, reached, matched);
                String context = "visible=" + visible + ", hide=" + hide + ", replace=" + replace
                        + ", nativeReached=" + reached + ", wrapperMatched=" + matched;
                assertTrue(count.total() == 0 || count.total() == 1, context);
                assertFalse(count.nativeDraws() > 0 && count.postDraws() > 0, context);
                assertEquals(!visible || !reached || (matched && hide) ? 0 : 1, count.total(), context);
            }
    }

    private static boolean visibleFirstPerson() {
        return VrHandVisibilityPolicy.allows(true, false, false, true, false, false, false, false);
    }

    /** Models only ownership; a missing wrapper or unreached native call cannot record suppression. */
    private static DrawCount countDraws(VrHandRenderPlan plan, boolean visible, boolean reached, boolean matched) {
        int nativeDraws = 0;
        boolean suppressed = false;
        if (visible && reached) {
            if (matched && plan.suppressesOriginal()) suppressed = true;
            else nativeDraws++;
        }
        return new DrawCount(nativeDraws, plan.allowsPostRender(suppressed) ? 1 : 0);
    }

    private record Scenario(String name, boolean visible) {}
    private record DrawCount(int nativeDraws, int postDraws) {
        int total() { return nativeDraws + postDraws; }
    }
}
