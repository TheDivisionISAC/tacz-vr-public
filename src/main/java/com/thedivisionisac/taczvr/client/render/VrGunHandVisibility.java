package com.thedivisionisac.taczvr.client.render;

import com.thedivisionisac.taczvr.client.TaczVrClient;
import com.thedivisionisac.taczvr.client.compat.lrtactical.LrTacticalVrHandPoseCache;
import com.thedivisionisac.taczvr.config.TaczVrClientConfig;
import com.thedivisionisac.taczvr.config.VrHandDisplayMode;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;

import java.util.Locale;

/**
 * Classifies reload, inspect, sprint, and non-loop action frames that need authored hand motion. Compatible guns use
 * functional hand anchors to drive Vivecraft palms; guns without both anchors retain TACZ's model-hand fallback.
 */
public final class VrGunHandVisibility {
    private static final String RELOAD_ANIMATION_TOKEN = "reload";
    private static final String INSPECT_ANIMATION_TOKEN = "inspect";
    private static final String RUN_ANIMATION_TOKEN = "run";
    private static final String SPRINT_ANIMATION_TOKEN = "sprint";
    private static final String SHOOT_ANIMATION_TOKEN = "shoot";
    private static final String STATIC_ANIMATION_TOKEN = "static";
    private static final String IDLE_ANIMATION_TOKEN = "idle";
    private static final String AIM_ANIMATION_TOKEN = "aim";
    private static final String ADS_ANIMATION_TOKEN = "ads";
    private static ResourceLocation latchedGunId;
    private static long lastAuthoredAnimationNs;
    private static ResourceLocation latchedManipulationGunId;
    private static long lastManipulationAnimationNs;

    private VrGunHandVisibility() {
    }

    public static boolean shouldUseTaczHands(LocalPlayer player, ItemStack stack) {
        return usesVivecraftAnimationSnapMode()
                && isAuthoredHandAnimationActive(player, stack)
                && (!TaczVrClientConfig.ENABLE_VR_ANIMATION_HAND_SNAP.get()
                    || !VrGunHandPoseCache.hasKnownAnimationAnchors(stack));
    }

    public static boolean shouldRenderTaczModelHands(LocalPlayer player, ItemStack stack) {
        if (!isValidVrGun(player, stack)) {
            return true;
        }
        return switch (TaczVrClientConfig.VR_HAND_DISPLAY_MODE.get()) {
            case HIDDEN -> false;
            case TACZ_DURING_MANIPULATION -> isManipulationAnimationActive(player, stack);
            case VIVECRAFT_SNAP -> !TaczVrClientConfig.HIDE_TACZ_MODEL_HANDS.get()
                    || shouldUseTaczHands(player, stack);
        };
    }

    public static boolean shouldHideVivecraftHands(LocalPlayer player, ItemStack stack) {
        if (!isValidVrGun(player, stack)) {
            return false;
        }
        return switch (TaczVrClientConfig.VR_HAND_DISPLAY_MODE.get()) {
            case HIDDEN -> true;
            case TACZ_DURING_MANIPULATION -> isManipulationAnimationActive(player, stack);
            case VIVECRAFT_SNAP -> shouldUseTaczHands(player, stack);
        };
    }

    public static boolean allowsVivecraftHands() {
        return TaczVrClientConfig.VR_HAND_DISPLAY_MODE.get() != VrHandDisplayMode.HIDDEN;
    }

    public static boolean allowsSupportHandSnap() {
        return TaczVrClientConfig.VR_HAND_DISPLAY_MODE.get() != VrHandDisplayMode.HIDDEN;
    }

    public static boolean keepsMainHandOnGrip() {
        return TaczVrClientConfig.VR_HAND_DISPLAY_MODE.get() == VrHandDisplayMode.VIVECRAFT_SNAP;
    }

    public static boolean isSupportHandSnapEligible(ItemStack supportStack) {
        if (!allowsSupportHandSnap()) {
            return false;
        }
        if (supportStack.isEmpty()) {
            return TaczVrClientConfig.ENABLE_VR_SUPPORT_HAND_SNAP.get();
        }
        return TaczVrClientConfig.ENABLE_VR_SHIELD_HAND_SNAP.get()
                && supportStack.canPerformAction(ToolActions.SHIELD_BLOCK);
    }

    private static boolean usesVivecraftAnimationSnapMode() {
        return TaczVrClientConfig.VR_HAND_DISPLAY_MODE.get() == VrHandDisplayMode.VIVECRAFT_SNAP;
    }

    public static void resetTransientState() {
        clearAnimationLatch();
        clearManipulationLatch();
        VrGunHandPoseCache.resetTransientState();
        LrTacticalVrHandPoseCache.resetTransientState();
    }

    public static boolean isForcedAnimationSnapActive(LocalPlayer player, ItemStack stack) {
        return usesVivecraftAnimationSnapMode()
                && TaczVrClientConfig.ENABLE_VR_ANIMATION_HAND_SNAP.get()
                && VrGunHandPoseCache.hasKnownAnimationAnchors(stack)
                && isAuthoredHandAnimationActive(player, stack);
    }

    public static boolean isForcedSupportHandSnapActive(LocalPlayer player, ItemStack stack) {
        return usesVivecraftAnimationSnapMode()
                && TaczVrClientConfig.ENABLE_VR_ANIMATION_HAND_SNAP.get()
                && TaczVrClientConfig.USE_TACZ_HANDS_DURING_MANIPULATION.get()
                && VrGunHandPoseCache.hasKnownAnimationAnchors(stack)
                && isManipulationAnimationActive(player, stack);
    }

    public static boolean isAuthoredHandAnimationActive(LocalPlayer player, ItemStack stack) {
        if (!isValidVrGun(player, stack)) {
            clearAnimationLatch();
            return false;
        }
        boolean useManipulationHands = TaczVrClientConfig.USE_TACZ_HANDS_DURING_MANIPULATION.get();
        boolean useSprintHands = TaczVrClientConfig.USE_TACZ_HANDS_DURING_SPRINT.get()
                && !TaczVrClientConfig.DISABLE_TACZ_SPRINT_ANIMATION_IN_VR.get();
        boolean stateSignal = useManipulationHands
                && IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().isReloading();
        if (useSprintHands && !player.isMovingSlowly() && player.isSprinting()) {
            stateSignal = true;
        }
        boolean trackSignal = TimelessAPI.getGunDisplay(stack)
                .map(display -> display.getAnimationStateMachine())
                .map(stateMachine -> hasActiveTaczHandAnimation(
                        stateMachine.getAnimationController(), useManipulationHands, useSprintHands
                ))
                .orElse(false);
        return updateAnimationLatch(stack, stateSignal || trackSignal);
    }

    public static boolean isManipulationAnimationActive(LocalPlayer player, ItemStack stack) {
        if (!isValidVrGun(player, stack)) {
            clearManipulationLatch();
            return false;
        }
        boolean stateSignal = IGunOperator.fromLivingEntity(player)
                .getSynReloadState().getStateType().isReloading();
        boolean trackSignal = TimelessAPI.getGunDisplay(stack)
                .map(display -> display.getAnimationStateMachine())
                .map(stateMachine -> hasActiveManipulationAnimation(stateMachine.getAnimationController()))
                .orElse(false);
        return updateManipulationLatch(stack, stateSignal || trackSignal);
    }

    public static boolean isShootAnimationActive(ItemStack stack) {
        return TimelessAPI.getGunDisplay(stack)
                .map(display -> display.getAnimationStateMachine())
                .map(stateMachine -> hasActiveAnimationToken(
                        stateMachine.getAnimationController(), SHOOT_ANIMATION_TOKEN
                ))
                .orElse(false);
    }

    private static boolean hasActiveAnimationToken(AnimationController controller, String token) {
        Iterable<Integer> allocatedTracks = controller.getUpdatingTrackArray();
        if (allocatedTracks == null) {
            return false;
        }
        for (int track : allocatedTracks) {
            ObjectAnimationRunner runner = controller.getAnimation(track);
            if (isActiveRunnerNamed(runner, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasActiveManipulationAnimation(AnimationController controller) {
        Iterable<Integer> allocatedTracks = controller.getUpdatingTrackArray();
        if (allocatedTracks == null) {
            return false;
        }
        for (int track : allocatedTracks) {
            ObjectAnimationRunner runner = controller.getAnimation(track);
            if (isActiveRunnerContaining(runner, RELOAD_ANIMATION_TOKEN)
                    || isActiveRunnerContaining(runner, INSPECT_ANIMATION_TOKEN)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isActiveRunnerContaining(ObjectAnimationRunner runner, String token) {
        if (runner == null
                || !(runner.isRunning() || runner.isHolding() || runner.isPausing() || runner.isTransitioning())) {
            return false;
        }
        if (runner.getAnimation().name.toLowerCase(Locale.ROOT).contains(token)) {
            return true;
        }
        ObjectAnimationRunner transitionTarget = runner.getTransitionTo();
        return transitionTarget != null
                && transitionTarget.getAnimation().name.toLowerCase(Locale.ROOT).contains(token);
    }

    private static boolean isActiveRunnerNamed(ObjectAnimationRunner runner, String token) {
        if (runner == null
                || !(runner.isRunning() || runner.isHolding() || runner.isPausing() || runner.isTransitioning())) {
            return false;
        }
        if (containsAnimationToken(runner.getAnimation().name.toLowerCase(Locale.ROOT), token)) {
            return true;
        }
        ObjectAnimationRunner transitionTarget = runner.getTransitionTo();
        return transitionTarget != null
                && containsAnimationToken(transitionTarget.getAnimation().name.toLowerCase(Locale.ROOT), token);
    }

    private static boolean hasActiveTaczHandAnimation(AnimationController controller, boolean useManipulationHands,
                                                       boolean useSprintHands) {
        Iterable<Integer> allocatedTracks = controller.getUpdatingTrackArray();
        if (allocatedTracks == null) {
            return false;
        }
        // TACZ exposes every allocated state-machine track through this public update-order API.
        for (int track : allocatedTracks) {
            ObjectAnimationRunner runner = controller.getAnimation(track);
            if (isActiveTaczHandRunner(runner, useManipulationHands, useSprintHands)
                    || isGenericActionRunner(runner)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isActiveTaczHandRunner(ObjectAnimationRunner runner, boolean useManipulationHands,
                                                   boolean useSprintHands) {
        if (runner == null
                || !(runner.isRunning() || runner.isHolding() || runner.isPausing() || runner.isTransitioning())) {
            return false;
        }
        if (usesTaczHands(runner.getAnimation().name, useManipulationHands, useSprintHands)) {
            return true;
        }
        ObjectAnimationRunner transitionTarget = runner.getTransitionTo();
        return transitionTarget != null
                && usesTaczHands(transitionTarget.getAnimation().name, useManipulationHands, useSprintHands);
    }

    private static boolean usesTaczHands(String name, boolean useManipulationHands, boolean useSprintHands) {
        String normalized = name.toLowerCase(Locale.ROOT);
        if (useManipulationHands
                && (normalized.contains(RELOAD_ANIMATION_TOKEN)
                    || normalized.contains(INSPECT_ANIMATION_TOKEN))) {
            return true;
        }
        if (useSprintHands
                && (containsAnimationToken(normalized, RUN_ANIMATION_TOKEN)
                    || containsAnimationToken(normalized, SPRINT_ANIMATION_TOKEN))) {
            return true;
        }
        return false;
    }

    private static boolean isGenericActionRunner(ObjectAnimationRunner runner) {
        if (runner == null
                || !(runner.isRunning() || runner.isHolding() || runner.isPausing() || runner.isTransitioning())) {
            return false;
        }
        if (isGenericActionAnimation(runner.getAnimation())) {
            return true;
        }
        ObjectAnimationRunner transitionTarget = runner.getTransitionTo();
        return transitionTarget != null && isGenericActionAnimation(transitionTarget.getAnimation());
    }

    private static boolean isGenericActionAnimation(ObjectAnimation animation) {
        if (animation.playType == ObjectAnimation.PlayType.LOOP) {
            return false;
        }
        String name = animation.name.toLowerCase(Locale.ROOT);
        // Stable posture/camera tracks are exclusions; every other one-shot is treated as an authored action.
        return !containsAnimationToken(name, SHOOT_ANIMATION_TOKEN)
                && !containsAnimationToken(name, STATIC_ANIMATION_TOKEN)
                && !containsAnimationToken(name, IDLE_ANIMATION_TOKEN)
                && !containsAnimationToken(name, AIM_ANIMATION_TOKEN)
                && !containsAnimationToken(name, ADS_ANIMATION_TOKEN);
    }

    private static boolean updateAnimationLatch(ItemStack stack, boolean activeNow) {
        IGun gun = IGun.getIGunOrNull(stack);
        ResourceLocation gunId = gun == null ? null : gun.getGunId(stack);
        if (gunId == null) {
            clearAnimationLatch();
            return false;
        }
        long now = System.nanoTime();
        if (!gunId.equals(latchedGunId)) {
            latchedGunId = gunId;
            lastAuthoredAnimationNs = 0L;
        }
        if (activeNow) {
            lastAuthoredAnimationNs = now;
            return true;
        }
        long graceNs = TaczVrClientConfig.VR_ANIMATION_HAND_RELEASE_GRACE_MS.get() * 1_000_000L;
        return lastAuthoredAnimationNs != 0L && now - lastAuthoredAnimationNs <= graceNs;
    }

    private static void clearAnimationLatch() {
        latchedGunId = null;
        lastAuthoredAnimationNs = 0L;
    }

    private static boolean updateManipulationLatch(ItemStack stack, boolean activeNow) {
        IGun gun = IGun.getIGunOrNull(stack);
        ResourceLocation gunId = gun == null ? null : gun.getGunId(stack);
        if (gunId == null) {
            clearManipulationLatch();
            return false;
        }
        long now = System.nanoTime();
        if (!gunId.equals(latchedManipulationGunId)) {
            latchedManipulationGunId = gunId;
            lastManipulationAnimationNs = 0L;
        }
        if (activeNow) {
            lastManipulationAnimationNs = now;
            return true;
        }
        long graceNs = TaczVrClientConfig.VR_ANIMATION_HAND_RELEASE_GRACE_MS.get() * 1_000_000L;
        return lastManipulationAnimationNs != 0L && now - lastManipulationAnimationNs <= graceNs;
    }

    private static void clearManipulationLatch() {
        latchedManipulationGunId = null;
        lastManipulationAnimationNs = 0L;
    }

    private static boolean isValidVrGun(LocalPlayer player, ItemStack stack) {
        return TaczVrClient.vivecraft().isVrActive()
                && ItemStack.matches(stack, player.getMainHandItem())
                && IGun.getIGunOrNull(stack) != null;
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
