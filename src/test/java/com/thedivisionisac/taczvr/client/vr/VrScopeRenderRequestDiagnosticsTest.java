package com.thedivisionisac.taczvr.client.vr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that C3 telemetry is default-inert, bounded, and reports aggregate stereo de-duplication evidence. */
class VrScopeRenderRequestDiagnosticsTest {
    private static final ResourceLocation DIMENSION_ID = ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation GUN_ID = ResourceLocation.tryParse("tacz:test_gun");
    private static final ResourceLocation SCOPE_ID = ResourceLocation.tryParse("tacz:test_scope");

    @Test
    void disabledDiagnosticsEmitNothing() {
        List<String> messages = new ArrayList<>();
        VrScopeRenderRequestDiagnostics diagnostics = new VrScopeRenderRequestDiagnostics(false, 10L, messages::add);

        diagnostics.record(request(1L), "LEFT", true, 0L);

        assertTrue(messages.isEmpty());
    }

    @Test
    void rateLimitedMessageAggregatesAcceptedAndDuplicateOffers() {
        List<String> messages = new ArrayList<>();
        VrScopeRenderRequestDiagnostics diagnostics = new VrScopeRenderRequestDiagnostics(true, 10L, messages::add);

        diagnostics.record(request(1L), "LEFT", true, 0L);
        diagnostics.record(request(1L), "RIGHT", false, 1L);
        diagnostics.record(request(2L), "LEFT", true, 10L);

        assertEquals(2, messages.size());
        assertTrue(messages.get(1).contains("offered_total=3"));
        assertTrue(messages.get(1).contains("accepted_total=2"));
        assertTrue(messages.get(1).contains("duplicate_total=1"));
        assertTrue(messages.get(1).contains("magnification=4.0000"));
    }

    private static VrScopeRenderRequest request(long frameIndex) {
        return VrScopeRenderRequest.validated(
                frameIndex, DIMENSION_ID, GUN_ID, SCOPE_ID, 0,
                new Vec3(0.0D, 0.0D, -0.25D),
                VrRotation.validated(0.0D, 0.0D, 0.0D, 1.0D).orElseThrow(),
                4.0D, 1.0D
        ).orElseThrow();
    }
}
