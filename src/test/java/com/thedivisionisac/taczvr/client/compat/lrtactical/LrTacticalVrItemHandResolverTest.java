package com.thedivisionisac.taczvr.client.compat.lrtactical;

import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrTacticalVrItemHandResolverTest {
    @Test
    void resolvesLeftPrimaryWhilePreservingSplitOwnership() {
        ModelTree tree = createTwoHandTree();
        addGeometry(child(tree.leftGroup, "bottle"), 8);
        addGeometry(child(tree.rightGroup, "pill"), 1);

        LrTacticalVrItemHandResolver.Resolution resolution =
                LrTacticalVrItemHandResolver.resolveTree(tree.root, tree.leftAnchor, tree.rightAnchor);

        assertEquals(LrTacticalVrItemHandResolver.AuthoredHand.LEFT, resolution.primary());
        assertTrue(resolution.split());
        assertEquals(8.0D, resolution.leftWeight());
        assertEquals(1.0D, resolution.rightWeight());
        assertEquals(LrTacticalVrItemHandResolver.ResolutionSource.GEOMETRY_DOMINANCE, resolution.source());
        assertEquals(0.875D, resolution.confidence());
    }

    @Test
    void resolvesRightPrimaryFromTopologyWithoutBoneNameRules() {
        ModelTree tree = createTwoHandTree();
        BedrockPart arbitraryContainer = child(tree.rightGroup, "staff");
        addGeometry(child(arbitraryContainer, "visible_prop"), 4);

        LrTacticalVrItemHandResolver.Resolution resolution =
                LrTacticalVrItemHandResolver.resolveTree(tree.root, tree.leftAnchor, tree.rightAnchor);

        assertEquals(LrTacticalVrItemHandResolver.AuthoredHand.RIGHT, resolution.primary());
        assertFalse(resolution.split());
        assertEquals(0.0D, resolution.leftWeight());
        assertEquals(4.0D, resolution.rightWeight());
    }

    @Test
    void resolvesSyntheticCubeChildrenWithoutParentPointers() {
        ModelTree tree = createTwoHandTree();
        BedrockPart leftProp = child(tree.leftGroup, "left_prop");
        BedrockPart syntheticCubePart = new TestPart(null);
        addGeometry(syntheticCubePart, 8);
        leftProp.children.add(syntheticCubePart);
        addGeometry(child(tree.rightGroup, "right_prop"), 1);

        LrTacticalVrItemHandResolver.Resolution resolution =
                LrTacticalVrItemHandResolver.resolveTree(tree.root, tree.leftAnchor, tree.rightAnchor);

        assertEquals(LrTacticalVrItemHandResolver.AuthoredHand.LEFT, resolution.primary());
        assertTrue(resolution.split());
        assertEquals(8.0D, resolution.leftWeight());
        assertEquals(1.0D, resolution.rightWeight());
    }

    @Test
    void namedCoreBoneOverridesSplitGeometryWeight() {
        ModelTree tree = createTwoHandTree();
        BedrockPart namedCore = child(tree.leftGroup, "logical_item");
        addGeometry(namedCore, 2);
        addGeometry(child(tree.rightGroup, "manipulated_part"), 4);
        LrTacticalVrItemHandResolver.Resolution weighted =
                LrTacticalVrItemHandResolver.resolveTree(tree.root, tree.leftAnchor, tree.rightAnchor);

        LrTacticalVrItemHandResolver.Resolution resolved =
                LrTacticalVrItemHandResolver.preferNamedCore(
                        weighted, namedCore, tree.leftAnchor, tree.rightAnchor
                );

        assertEquals(LrTacticalVrItemHandResolver.AuthoredHand.LEFT, resolved.primary());
        assertTrue(resolved.split());
        assertEquals(2.0D, resolved.leftWeight());
        assertEquals(4.0D, resolved.rightWeight());
        assertEquals(LrTacticalVrItemHandResolver.ResolutionSource.NAMED_VISIBLE_CORE, resolved.source());
        assertEquals(1.0D, resolved.confidence());
    }

    @Test
    void ignoresEmptyNamedAnimationNodeAsPrimaryEvidence() {
        ModelTree tree = createTwoHandTree();
        BedrockPart emptyNamedNode = child(tree.leftGroup, "logical_item");
        addGeometry(child(tree.rightGroup, "visible_prop"), 4);
        LrTacticalVrItemHandResolver.Resolution weighted =
                LrTacticalVrItemHandResolver.resolveTree(tree.root, tree.leftAnchor, tree.rightAnchor);

        LrTacticalVrItemHandResolver.Resolution resolved =
                LrTacticalVrItemHandResolver.preferNamedCore(
                        weighted, emptyNamedNode, tree.leftAnchor, tree.rightAnchor
                );

        assertEquals(LrTacticalVrItemHandResolver.AuthoredHand.RIGHT, resolved.primary());
        assertEquals(LrTacticalVrItemHandResolver.ResolutionSource.GEOMETRY_DOMINANCE, resolved.source());
    }

    @Test
    void leavesBalancedSplitModelAmbiguousForSafeFallback() {
        ModelTree tree = createTwoHandTree();
        addGeometry(child(tree.leftGroup, "left_prop"), 3);
        addGeometry(child(tree.rightGroup, "right_prop"), 3);

        LrTacticalVrItemHandResolver.Resolution resolution =
                LrTacticalVrItemHandResolver.resolveTree(tree.root, tree.leftAnchor, tree.rightAnchor);

        assertEquals(LrTacticalVrItemHandResolver.AuthoredHand.UNKNOWN, resolution.primary());
        assertTrue(resolution.split());
        assertEquals(LrTacticalVrItemHandResolver.ResolutionSource.INSUFFICIENT_EVIDENCE, resolution.source());
        assertEquals(0.0D, resolution.confidence());
    }

    @Test
    void ignoresFunctionalHandScaffoldGeometry() {
        ModelTree tree = createTwoHandTree();
        addGeometry(tree.leftAnchor, 20);
        addGeometry(tree.rightAnchor, 20);
        addGeometry(child(tree.rightGroup, "item"), 1);

        LrTacticalVrItemHandResolver.Resolution resolution =
                LrTacticalVrItemHandResolver.resolveTree(tree.root, tree.leftAnchor, tree.rightAnchor);

        assertEquals(LrTacticalVrItemHandResolver.AuthoredHand.RIGHT, resolution.primary());
        assertFalse(resolution.split());
        assertEquals(1.0D, resolution.rightWeight());
    }

    @Test
    void ignoresSyntheticHandScaffoldChildrenWithoutParentPointers() {
        ModelTree tree = createTwoHandTree();
        BedrockPart syntheticLeftArmCube = new TestPart(null);
        addGeometry(syntheticLeftArmCube, 20);
        tree.leftAnchor.children.add(syntheticLeftArmCube);
        addGeometry(child(tree.rightGroup, "item"), 1);

        LrTacticalVrItemHandResolver.Resolution resolution =
                LrTacticalVrItemHandResolver.resolveTree(tree.root, tree.leftAnchor, tree.rightAnchor);

        assertEquals(LrTacticalVrItemHandResolver.AuthoredHand.RIGHT, resolution.primary());
        assertFalse(resolution.split());
        assertEquals(0.0D, resolution.leftWeight());
        assertEquals(1.0D, resolution.rightWeight());
    }

    @Test
    void returnsUnknownWhenAFunctionalAnchorIsMissing() {
        ModelTree tree = createTwoHandTree();
        addGeometry(child(tree.leftGroup, "item"), 2);

        LrTacticalVrItemHandResolver.Resolution resolution =
                LrTacticalVrItemHandResolver.resolveTree(tree.root, tree.leftAnchor, null);

        assertEquals(LrTacticalVrItemHandResolver.AuthoredHand.UNKNOWN, resolution.primary());
        assertFalse(resolution.split());
    }

    private static ModelTree createTwoHandTree() {
        BedrockPart root = new BedrockPart("root");
        BedrockPart leftGroup = child(root, "author_left_group");
        BedrockPart leftHand = child(leftGroup, "any_left_hand");
        BedrockPart leftAnchor = child(leftHand, LrTacticalVrItemHandResolver.LEFT_HAND_ANCHOR);
        BedrockPart rightGroup = child(root, "author_right_group");
        BedrockPart rightHand = child(rightGroup, "any_right_hand");
        BedrockPart rightAnchor = child(rightHand, LrTacticalVrItemHandResolver.RIGHT_HAND_ANCHOR);
        return new ModelTree(root, leftGroup, rightGroup, leftAnchor, rightAnchor);
    }

    private static BedrockPart child(BedrockPart parent, String name) {
        TestPart child = new TestPart(name);
        child.attachTo(parent);
        return child;
    }

    private static void addGeometry(BedrockPart part, int cubeCount) {
        for (int index = 0; index < cubeCount; index++) {
            part.cubes.add((pose, consumer, light, overlay, red, green, blue, alpha) -> {
            });
        }
    }

    private record ModelTree(BedrockPart root, BedrockPart leftGroup, BedrockPart rightGroup,
                             BedrockPart leftAnchor, BedrockPart rightAnchor) {
    }

    private static final class TestPart extends BedrockPart {
        private TestPart(String name) {
            super(name);
        }

        private void attachTo(BedrockPart parent) {
            this.parent = parent;
            parent.addChild(this);
        }
    }
}
