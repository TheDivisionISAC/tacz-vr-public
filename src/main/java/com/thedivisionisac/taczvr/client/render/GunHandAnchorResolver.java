package com.thedivisionisac.taczvr.client.render;

import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves standard TACZ hand anchors and a deliberately small alias set for nonstandard gunpacks.
 */
public final class GunHandAnchorResolver {
    private static final String RIGHT_HAND_ANCHOR = "righthand_pos";
    private static final String LEFT_HAND_ANCHOR = "lefthand_pos";
    private static final Set<String> RIGHT_HAND_ALIASES = Set.of(
            "right_hand_pos", "righthand_anchor", "right_hand_anchor", "right_grip", "rightgrip"
    );
    private static final Set<String> LEFT_HAND_ALIASES = Set.of(
            "left_hand_pos", "lefthand_anchor", "left_hand_anchor", "left_grip", "leftgrip"
    );

    private GunHandAnchorResolver() {
    }

    @Nullable
    public static BedrockPart findRightHand(BedrockGunModel model) {
        BedrockPart standard = model.getNode(RIGHT_HAND_ANCHOR);
        return standard != null ? standard : findAlias(model, RIGHT_HAND_ALIASES);
    }

    @Nullable
    public static BedrockPart findLeftHand(BedrockGunModel model) {
        BedrockPart standard = model.getNode(LEFT_HAND_ANCHOR);
        return standard != null ? standard : findAlias(model, LEFT_HAND_ALIASES);
    }

    @Nullable
    private static BedrockPart findAlias(BedrockGunModel model, Set<String> aliases) {
        Deque<BedrockPart> pending = new ArrayDeque<>(model.getShouldRender());
        while (!pending.isEmpty()) {
            BedrockPart part = pending.removeFirst();
            if (part.name != null && aliases.contains(part.name.toLowerCase(Locale.ROOT))) {
                return part;
            }
            pending.addAll(part.children);
        }
        return null;
    }
}
