package com.thedivisionisac.taczvr.client.compat.lrtactical;

import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockPart;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;

/**
 * Infers which authored hand owns the main visible prop from reusable Bedrock model evidence. Exact logical-item
 * core bones are treated as strong evidence only when they own visible, non-hand geometry; otherwise the resolver
 * falls back to hand-scaffold-filtered geometry dominance around TACZ's functional hand anchors.
 */
public final class LrTacticalVrItemHandResolver {
    static final String LEFT_HAND_ANCHOR = "lefthand_pos";
    static final String RIGHT_HAND_ANCHOR = "righthand_pos";
    private static final double MIN_WEIGHT = 0.000001D;
    private static final double AMBIGUOUS_DIFFERENCE_RATIO = 0.1D;
    private static final Map<BedrockAnimatedModel, ModelAnalysis> CACHE = new WeakHashMap<>();

    private LrTacticalVrItemHandResolver() {
    }

    public static Resolution resolve(BedrockAnimatedModel model) {
        return resolve(model, null);
    }

    public static Resolution resolve(BedrockAnimatedModel model, @Nullable String logicalItemPath) {
        if (model == null) {
            return Resolution.unknown();
        }
        ModelAnalysis analysis = CACHE.computeIfAbsent(model, LrTacticalVrItemHandResolver::analyzeModel);
        BedrockPart leftAnchor = model.getNode(LEFT_HAND_ANCHOR);
        BedrockPart rightAnchor = model.getNode(RIGHT_HAND_ANCHOR);
        BedrockPart coreNode = findCoreNode(model, logicalItemPath);
        return preferNamedCore(
                analysis.geometryResolution,
                coreNode,
                leftAnchor,
                rightAnchor,
                analysis.structuralParents
        );
    }

    static Resolution resolveTree(BedrockPart root, BedrockPart leftAnchor, BedrockPart rightAnchor) {
        if (root == null || leftAnchor == null || rightAnchor == null) {
            return Resolution.unknown();
        }
        return resolveTree(root, leftAnchor, rightAnchor, buildStructuralParents(root));
    }

    private static Resolution resolveTree(BedrockPart root, BedrockPart leftAnchor, BedrockPart rightAnchor,
                                          Map<BedrockPart, BedrockPart> structuralParents) {
        double leftWeight = 0.0D;
        double rightWeight = 0.0D;
        Deque<BedrockPart> pending = new ArrayDeque<>();
        Set<BedrockPart> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(root);
        while (!pending.isEmpty()) {
            BedrockPart part = pending.removeFirst();
            if (!visited.add(part)) {
                continue;
            }
            for (BedrockPart child : part.children) {
                pending.addLast(child);
            }
            if (isHandScaffold(part, leftAnchor, rightAnchor, structuralParents)) {
                continue;
            }

            double geometryWeight = geometryWeight(part);
            if (geometryWeight <= MIN_WEIGHT) {
                continue;
            }
            int leftDistance = treeDistance(part, leftAnchor, structuralParents);
            int rightDistance = treeDistance(part, rightAnchor, structuralParents);
            if (leftDistance < rightDistance) {
                leftWeight += geometryWeight;
            } else if (rightDistance < leftDistance) {
                rightWeight += geometryWeight;
            }
        }

        double largestWeight = Math.max(leftWeight, rightWeight);
        if (largestWeight <= MIN_WEIGHT) {
            return Resolution.unknown();
        }
        boolean split = leftWeight > MIN_WEIGHT && rightWeight > MIN_WEIGHT;
        double confidence = geometryConfidence(leftWeight, rightWeight);
        if (Math.abs(leftWeight - rightWeight) <= largestWeight * AMBIGUOUS_DIFFERENCE_RATIO) {
            return new Resolution(
                    AuthoredHand.UNKNOWN, split, leftWeight, rightWeight,
                    ResolutionSource.INSUFFICIENT_EVIDENCE, confidence
            );
        }
        AuthoredHand primary = leftWeight > rightWeight ? AuthoredHand.LEFT : AuthoredHand.RIGHT;
        return new Resolution(
                primary, split, leftWeight, rightWeight,
                ResolutionSource.GEOMETRY_DOMINANCE, confidence
        );
    }

    static Resolution preferNamedCore(Resolution resolution, @Nullable BedrockPart coreNode,
                                      BedrockPart leftAnchor, BedrockPart rightAnchor) {
        return preferNamedCore(resolution, coreNode, leftAnchor, rightAnchor, Collections.emptyMap());
    }

    private static Resolution preferNamedCore(Resolution resolution, @Nullable BedrockPart coreNode,
                                              BedrockPart leftAnchor, BedrockPart rightAnchor,
                                              Map<BedrockPart, BedrockPart> structuralParents) {
        if (coreNode == null || leftAnchor == null || rightAnchor == null) {
            return resolution;
        }
        if (isHandScaffold(coreNode, leftAnchor, rightAnchor, structuralParents)
                || visibleSubtreeWeight(coreNode, leftAnchor, rightAnchor, structuralParents) <= MIN_WEIGHT) {
            return resolution;
        }
        int leftDistance = treeDistance(coreNode, leftAnchor, structuralParents);
        int rightDistance = treeDistance(coreNode, rightAnchor, structuralParents);
        if (leftDistance == Integer.MAX_VALUE
                || rightDistance == Integer.MAX_VALUE
                || leftDistance == rightDistance) {
            return resolution;
        }
        AuthoredHand namedPrimary = leftDistance < rightDistance ? AuthoredHand.LEFT : AuthoredHand.RIGHT;
        return new Resolution(
                namedPrimary,
                resolution.split(),
                resolution.leftWeight(),
                resolution.rightWeight(),
                ResolutionSource.NAMED_VISIBLE_CORE,
                1.0D
        );
    }

    private static ModelAnalysis analyzeModel(BedrockAnimatedModel model) {
        BedrockPart root = model.getRootNode();
        BedrockPart leftAnchor = model.getNode(LEFT_HAND_ANCHOR);
        BedrockPart rightAnchor = model.getNode(RIGHT_HAND_ANCHOR);
        if (root == null || leftAnchor == null || rightAnchor == null) {
            return new ModelAnalysis(Resolution.unknown(), Collections.emptyMap());
        }
        Map<BedrockPart, BedrockPart> structuralParents = buildStructuralParents(root);
        return new ModelAnalysis(
                resolveTree(root, leftAnchor, rightAnchor, structuralParents),
                structuralParents
        );
    }

    private static Map<BedrockPart, BedrockPart> buildStructuralParents(BedrockPart root) {
        Map<BedrockPart, BedrockPart> structuralParents = new IdentityHashMap<>();
        Deque<BedrockPart> pending = new ArrayDeque<>();
        Set<BedrockPart> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(root);
        while (!pending.isEmpty()) {
            BedrockPart part = pending.removeFirst();
            if (!visited.add(part)) {
                continue;
            }
            for (BedrockPart child : part.children) {
                structuralParents.putIfAbsent(child, part);
                pending.addLast(child);
            }
        }
        return structuralParents;
    }

    @Nullable
    private static BedrockPart findCoreNode(BedrockAnimatedModel model, @Nullable String logicalItemPath) {
        if (logicalItemPath == null || logicalItemPath.isBlank()) {
            return null;
        }
        BedrockPart coreNode = model.getNode(logicalItemPath);
        int separator = logicalItemPath.lastIndexOf('/');
        if (coreNode == null && separator >= 0 && separator + 1 < logicalItemPath.length()) {
            coreNode = model.getNode(logicalItemPath.substring(separator + 1));
        }
        return coreNode;
    }

    public static void reset() {
        CACHE.clear();
    }

    private static boolean isHandScaffold(BedrockPart part, BedrockPart leftAnchor, BedrockPart rightAnchor,
                                          Map<BedrockPart, BedrockPart> structuralParents) {
        return isAnchorScaffold(part, leftAnchor, structuralParents)
                || isAnchorScaffold(part, rightAnchor, structuralParents);
    }

    private static boolean isAnchorScaffold(BedrockPart part, BedrockPart anchor,
                                            Map<BedrockPart, BedrockPart> structuralParents) {
        if (part == anchor || part == effectiveParent(anchor, structuralParents)) {
            return true;
        }
        Set<BedrockPart> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BedrockPart parent = effectiveParent(part, structuralParents);
             parent != null && visited.add(parent);
             parent = effectiveParent(parent, structuralParents)) {
            if (parent == anchor) {
                return true;
            }
        }
        return false;
    }

    private static double geometryWeight(BedrockPart part) {
        double weight = 0.0D;
        for (BedrockCube cube : part.cubes) {
            double cubeVolume = cubeVolume(cube);
            weight += cubeVolume > MIN_WEIGHT ? cubeVolume : 1.0D;
        }
        return weight;
    }

    private static double visibleSubtreeWeight(BedrockPart root, BedrockPart leftAnchor, BedrockPart rightAnchor,
                                               Map<BedrockPart, BedrockPart> structuralParents) {
        double weight = 0.0D;
        Deque<BedrockPart> pending = new ArrayDeque<>();
        Set<BedrockPart> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(root);
        while (!pending.isEmpty()) {
            BedrockPart part = pending.removeFirst();
            if (!visited.add(part)) {
                continue;
            }
            if (!isHandScaffold(part, leftAnchor, rightAnchor, structuralParents)) {
                weight += geometryWeight(part);
            }
            pending.addAll(part.children);
        }
        return weight;
    }

    private static double cubeVolume(BedrockCube cube) {
        if (cube instanceof BedrockCubeBox box) {
            return volume(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }
        if (cube instanceof BedrockCubePerFace box) {
            return volume(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }
        return 0.0D;
    }

    private static double volume(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        double volume = Math.abs((maxX - minX) * (maxY - minY) * (maxZ - minZ));
        return Double.isFinite(volume) ? volume : 0.0D;
    }

    private static int treeDistance(BedrockPart first, BedrockPart second,
                                    Map<BedrockPart, BedrockPart> structuralParents) {
        Map<BedrockPart, Integer> firstDistances = new IdentityHashMap<>();
        Set<BedrockPart> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int distance = 0;
        for (BedrockPart part = first; part != null && visited.add(part);
             part = effectiveParent(part, structuralParents)) {
            firstDistances.put(part, distance++);
        }

        visited.clear();
        distance = 0;
        for (BedrockPart part = second; part != null && visited.add(part);
             part = effectiveParent(part, structuralParents)) {
            Integer firstDistance = firstDistances.get(part);
            if (firstDistance != null) {
                return firstDistance + distance;
            }
            distance++;
        }
        return Integer.MAX_VALUE;
    }

    private static BedrockPart effectiveParent(BedrockPart part,
                                               Map<BedrockPart, BedrockPart> structuralParents) {
        BedrockPart declaredParent = part.getParent();
        return declaredParent == null ? structuralParents.get(part) : declaredParent;
    }

    private static double geometryConfidence(double leftWeight, double rightWeight) {
        double largestWeight = Math.max(leftWeight, rightWeight);
        if (largestWeight <= MIN_WEIGHT) {
            return 0.0D;
        }
        return Math.min(1.0D, Math.abs(leftWeight - rightWeight) / largestWeight);
    }

    public enum AuthoredHand {
        LEFT,
        RIGHT,
        UNKNOWN
    }

    public enum ResolutionSource {
        NAMED_VISIBLE_CORE,
        GEOMETRY_DOMINANCE,
        INSUFFICIENT_EVIDENCE
    }

    public record Resolution(AuthoredHand primary, boolean split, double leftWeight, double rightWeight,
                             ResolutionSource source, double confidence) {
        public Resolution(AuthoredHand primary, boolean split, double leftWeight, double rightWeight) {
            this(
                    primary,
                    split,
                    leftWeight,
                    rightWeight,
                    primary == AuthoredHand.UNKNOWN
                            ? ResolutionSource.INSUFFICIENT_EVIDENCE
                            : ResolutionSource.GEOMETRY_DOMINANCE,
                    geometryConfidence(leftWeight, rightWeight)
            );
        }

        static Resolution unknown() {
            return new Resolution(
                    AuthoredHand.UNKNOWN, false, 0.0D, 0.0D,
                    ResolutionSource.INSUFFICIENT_EVIDENCE, 0.0D
            );
        }
    }

    private record ModelAnalysis(Resolution geometryResolution,
                                 Map<BedrockPart, BedrockPart> structuralParents) {
    }
}
