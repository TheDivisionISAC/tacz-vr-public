package com.thedivisionisac.taczvr.client.vr;

import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Keeps TACZ camera view aliases separate from the physical ocular/division stencil layer. */
class VrScopeAlignmentCaptureTest {
    @Test
    void secondCameraViewCanUseTheOnlyStencilLayer() {
        // Default ELCAN/Contender declare views=[2,...] but only one ocular and one division bone.
        List<BedrockPart> division = List.of(new BedrockPart("division"));
        List<List<BedrockPart>> paths = List.of(division);
        assertEquals(0, VrScopeAlignmentCapture.selectOcularPathIndex(paths, 1));
        assertSame(division, paths.get(VrScopeAlignmentCapture.selectOcularPathIndex(paths, 1)));
        assertEquals(254, (~(VrScopeAlignmentCapture.selectOcularPathIndex(paths, 1) + 1)) & 255);
    }

    @Test
    void selectsOcularMatchingTheActiveView() {
        List<BedrockPart> first = List.of(new BedrockPart("ocular"));
        List<BedrockPart> second = List.of(new BedrockPart("ocular_2"));

        assertSame(second, VrScopeAlignmentCapture.selectOcularPath(List.of(first, second), 1));
    }

    @Test
    void fallsBackToFirstUsableOcularForOutOfRangeOrEmptyView() {
        List<BedrockPart> first = List.of(new BedrockPart("ocular"));
        List<BedrockPart> third = List.of(new BedrockPart("ocular_3"));
        List<List<BedrockPart>> paths = List.of(first, Collections.emptyList(), third);

        assertSame(first, VrScopeAlignmentCapture.selectOcularPath(paths, 1));
        assertSame(first, VrScopeAlignmentCapture.selectOcularPath(paths, 99));
    }

    @Test
    void rejectsModelsWithoutAnOcularPath() {
        assertNull(VrScopeAlignmentCapture.selectOcularPath(null, 0));
        assertNull(VrScopeAlignmentCapture.selectOcularPath(List.of(), 0));
        assertNull(VrScopeAlignmentCapture.selectOcularPath(List.of(Collections.emptyList()), 0));
    }
}
