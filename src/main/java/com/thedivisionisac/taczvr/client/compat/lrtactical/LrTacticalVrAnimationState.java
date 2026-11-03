package com.thedivisionisac.taczvr.client.compat.lrtactical;

import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Reads LRTactical's live TACZ animation tracks without linking against any LRTactical class.
 */
public final class LrTacticalVrAnimationState {
    private static final String[] MANIPULATION_TOKENS = {
            "draw",
            "put_away",
            "inspect",
            "use",
            "unlock",
            "throw",
            "melee",
            "attack"
    };

    private LrTacticalVrAnimationState() {
    }

    public static boolean hasManipulationAnimation(@Nullable LuaAnimationStateMachine<?> stateMachine) {
        return stateMachine != null && hasManipulationAnimation(stateMachine.getAnimationController());
    }

    private static boolean hasManipulationAnimation(AnimationController controller) {
        Iterable<Integer> allocatedTracks = controller.getUpdatingTrackArray();
        if (allocatedTracks == null) {
            return false;
        }
        for (int track : allocatedTracks) {
            if (isManipulationRunner(controller.getAnimation(track))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isManipulationRunner(@Nullable ObjectAnimationRunner runner) {
        if (runner == null
                || !(runner.isRunning() || runner.isHolding() || runner.isPausing() || runner.isTransitioning())) {
            return false;
        }
        if (isManipulationAnimationName(runner.getAnimation().name)) {
            return true;
        }
        ObjectAnimationRunner transitionTarget = runner.getTransitionTo();
        return transitionTarget != null && isManipulationAnimationName(transitionTarget.getAnimation().name);
    }

    static boolean isManipulationAnimationName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        for (String token : MANIPULATION_TOKENS) {
            if (containsAnimationToken(normalized, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAnimationToken(String name, String token) {
        int index = -1;
        while ((index = name.indexOf(token, index + 1)) >= 0) {
            int end = index + token.length();
            boolean startsAtBoundary = index == 0 || isAnimationNameBoundary(name.charAt(index - 1));
            boolean endsAtBoundary = end == name.length() || isAnimationNameBoundary(name.charAt(end));
            if (startsAtBoundary && endsAtBoundary) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnimationNameBoundary(char character) {
        return character == '_' || character == '.' || character == ':' || character == '/' || character == '-';
    }
}
