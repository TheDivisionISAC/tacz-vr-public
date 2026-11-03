package com.thedivisionisac.taczvr.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client presentation, feedback, and radial-control options.
 */
public final class TaczVrClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_SHOT_DIRECTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_MUZZLE_ORIGIN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_AIM_STABILIZATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_MAGNIFICATION_SCALED_STABILIZATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_VIEW_RELATIVE_STABILIZATION;
    public static final ForgeConfigSpec.IntValue VR_STABILIZATION_LEVEL;
    public static final ForgeConfigSpec.DoubleValue VR_HIP_STABILIZATION_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue VR_ADS_STABILIZATION_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue VR_STABILIZATION_RELEASE_SPEED;
    public static final ForgeConfigSpec.DoubleValue VR_HIP_STABILIZATION_MAX_POSITION_OFFSET;
    public static final ForgeConfigSpec.DoubleValue VR_ADS_STABILIZATION_MAX_POSITION_OFFSET;
    public static final ForgeConfigSpec.DoubleValue VR_STABILIZATION_POSITION_RELEASE_SPEED;
    public static final ForgeConfigSpec.DoubleValue VR_HIP_VIEW_ROTATION_MAX_OFFSET;
    public static final ForgeConfigSpec.DoubleValue VR_ADS_VIEW_ROTATION_MAX_OFFSET;
    public static final ForgeConfigSpec.DoubleValue VR_HIP_VIEW_POSITION_MAX_OFFSET;
    public static final ForgeConfigSpec.DoubleValue VR_ADS_VIEW_POSITION_MAX_OFFSET;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_GUN_RENDERING;
    public static final ForgeConfigSpec.DoubleValue VR_GUN_SCALE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue VR_LRTACTICAL_THROWABLE_SCALE;
    public static final ForgeConfigSpec.DoubleValue VR_GUN_GRIP_OFFSET_X;
    public static final ForgeConfigSpec.DoubleValue VR_GUN_GRIP_OFFSET_Y;
    public static final ForgeConfigSpec.DoubleValue VR_GUN_GRIP_OFFSET_Z;
    public static final ForgeConfigSpec.DoubleValue VR_GUN_AIM_ROTATION_X;
    public static final ForgeConfigSpec.DoubleValue VR_GUN_AIM_ROTATION_Y;
    public static final ForgeConfigSpec.DoubleValue VR_GUN_AIM_ROTATION_Z;
    public static final ForgeConfigSpec.EnumValue<VrHandDisplayMode> VR_HAND_DISPLAY_MODE;
    public static final ForgeConfigSpec.BooleanValue HIDE_TACZ_MODEL_HANDS;
    public static final ForgeConfigSpec.BooleanValue POST_RENDER_VIVECRAFT_GUN_HAND;
    public static final ForgeConfigSpec.BooleanValue USE_TACZ_HANDS_DURING_MANIPULATION;
    public static final ForgeConfigSpec.BooleanValue USE_TACZ_HANDS_DURING_SPRINT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_ANIMATION_HAND_SNAP;
    public static final ForgeConfigSpec.DoubleValue VR_ANIMATION_HAND_SNAP_SMOOTHING;
    public static final ForgeConfigSpec.DoubleValue VR_ANIMATION_MAIN_HAND_SNAP_SMOOTHING;
    public static final ForgeConfigSpec.DoubleValue VR_ANIMATION_HAND_RELEASE_SMOOTHING;
    public static final ForgeConfigSpec.IntValue VR_ANIMATION_HAND_RELEASE_GRACE_MS;
    public static final ForgeConfigSpec.DoubleValue VR_ANIMATION_HAND_MAX_TRANSLATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_SUPPORT_HAND_SNAP;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_NEAR_MAIN_HAND_SUPPORT_SNAP;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_SHIELD_HAND_SNAP;
    public static final ForgeConfigSpec.DoubleValue VR_SUPPORT_HAND_SNAP_ENTER_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue VR_SUPPORT_HAND_SNAP_EXIT_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue VR_SUPPORT_HAND_SNAP_SMOOTHING;
    public static final ForgeConfigSpec.DoubleValue VR_SUPPORT_HAND_RELEASE_SMOOTHING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_SHOOT_HAND_ANIMATION;
    public static final ForgeConfigSpec.DoubleValue VR_SHOOT_HAND_SNAP_SMOOTHING;
    public static final ForgeConfigSpec.DoubleValue VR_SHOOT_HAND_MAX_TRANSLATION;
    public static final ForgeConfigSpec.BooleanValue DECOUPLE_VR_TRACER_OFFSET;
    public static final ForgeConfigSpec.DoubleValue VR_TRACER_HORIZONTAL_CORRECTION;
    public static final ForgeConfigSpec.DoubleValue VR_TRACER_VERTICAL_CORRECTION;
    public static final ForgeConfigSpec.BooleanValue CORRECT_VR_MUZZLE_FLASH_PER_EYE;
    public static final ForgeConfigSpec.BooleanValue HIDE_VIVECRAFT_CROSSHAIR_WITH_GUN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_PHYSICAL_SCOPE_GATE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_SCOPE_MAGNIFICATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_SCOPE_OCULUS_COMPAT;
    public static final ForgeConfigSpec.BooleanValue DISABLE_TACZ_WORLD_SCOPE_FOV;
    public static final ForgeConfigSpec.BooleanValue DISABLE_TACZ_CAMERA_RECOIL;
    public static final ForgeConfigSpec.BooleanValue DISABLE_TACZ_LEVEL_CAMERA_ANIMATION;
    public static final ForgeConfigSpec.BooleanValue DISABLE_TACZ_SPRINT_ANIMATION_IN_VR;
    public static final ForgeConfigSpec.BooleanValue ENABLE_HAPTICS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_GUN_RADIAL_MENU;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_SCOPE_ZOOM_CHORD;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_GUN_HOTBAR_RETREAT;
    public static final ForgeConfigSpec.DoubleValue VR_GUN_HOTBAR_RETREAT_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LRTACTICAL_THROWABLE_VR_COMPAT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LRTACTICAL_CONSUMABLE_VR_RENDERING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LRTACTICAL_MELEE_VR_RENDERING;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("shooting");
        ENABLE_VR_SHOT_DIRECTION = builder
                .comment("Use Vivecraft main-hand pose as the candidate shot direction when VR is active.")
                .define("enableVrShotDirection", true);
        ENABLE_VR_MUZZLE_ORIGIN = builder
                .comment("Send a model-derived forward muzzle origin for server validation.")
                .define("enableVrMuzzleOrigin", true);
        ENABLE_VR_AIM_STABILIZATION = builder
                .comment("Apply speed-adaptive direction and position stabilization to TACZ guns in VR.")
                .define("enableVrAimStabilization", true);
        ENABLE_VR_MAGNIFICATION_SCALED_STABILIZATION = builder
                .comment(
                        "Increase the current hip/ADS stabilization strength with the equipped optical scope's selected zoom.",
                        "Enabled by default; gain is capped at 25x. Zero strength and motion-release thresholds are preserved.",
                        "Does not increase configured position or view-relative offset limits."
                )
                .define("enableVrMagnificationScaledStabilization", true);
        ENABLE_VR_VIEW_RELATIVE_STABILIZATION = builder
                .comment("Add bounded render-only HMD-relative full-pose stabilization, including controller roll.")
                .define("enableVrViewRelativeStabilization", true);
        VR_STABILIZATION_LEVEL = builder
                .comment("User-facing overall stabilization level; 50 preserves the validated default tuning.")
                .defineInRange(
                        "vrStabilizationLevel",
                        VrStabilizationLevel.DEFAULT,
                        VrStabilizationLevel.MINIMUM,
                        VrStabilizationLevel.MAXIMUM
                );
        VR_HIP_STABILIZATION_STRENGTH = builder
                .comment("Weak stabilization while holding a gun without aiming; 0 disables filtering.")
                .defineInRange("vrHipStabilizationStrength", 0.55D, 0.0D, 1.0D);
        VR_ADS_STABILIZATION_STRENGTH = builder
                .comment("Slightly stronger stabilization while TACZ's aim state is active; 0 disables filtering.")
                .defineInRange("vrAdsStabilizationStrength", 0.58D, 0.0D, 1.0D);
        VR_STABILIZATION_RELEASE_SPEED = builder
                .comment("Angular speed in degrees per second at which stabilization fully releases for fast turns.")
                .defineInRange("vrStabilizationReleaseSpeed", 120.0D, 30.0D, 720.0D);
        VR_HIP_STABILIZATION_MAX_POSITION_OFFSET = builder
                .comment("Maximum positional lag from the tracked hand while hip firing, in blocks.")
                .defineInRange("vrHipStabilizationMaxPositionOffset", 0.015D, 0.0D, 0.10D);
        VR_ADS_STABILIZATION_MAX_POSITION_OFFSET = builder
                .comment("Maximum positional lag from the tracked hand while aiming, in blocks.")
                .defineInRange("vrAdsStabilizationMaxPositionOffset", 0.017D, 0.0D, 0.15D);
        VR_STABILIZATION_POSITION_RELEASE_SPEED = builder
                .comment("Hand speed in blocks per second at which positional stabilization fully releases.")
                .defineInRange("vrStabilizationPositionReleaseSpeed", 0.80D, 0.20D, 3.0D);
        VR_HIP_VIEW_ROTATION_MAX_OFFSET = builder
                .comment("Maximum render-only HMD-relative angular correction while hip firing, in degrees.")
                .defineInRange("vrHipViewRotationMaxOffset", 0.55D, 0.0D, 3.0D);
        VR_ADS_VIEW_ROTATION_MAX_OFFSET = builder
                .comment("Maximum render-only HMD-relative angular correction while aiming, in degrees.")
                .defineInRange("vrAdsViewRotationMaxOffset", 0.60D, 0.0D, 3.0D);
        VR_HIP_VIEW_POSITION_MAX_OFFSET = builder
                .comment("Maximum render-only HMD-relative positional correction while hip firing, in blocks.")
                .defineInRange("vrHipViewPositionMaxOffset", 0.004D, 0.0D, 0.03D);
        VR_ADS_VIEW_POSITION_MAX_OFFSET = builder
                .comment("Maximum render-only HMD-relative positional correction while aiming, in blocks.")
                .defineInRange("vrAdsViewPositionMaxOffset", 0.0045D, 0.0D, 0.03D);
        builder.pop();

        builder.push("rendering");
        ENABLE_VR_GUN_RENDERING = builder
                .comment("Render TACZ first-person guns through Vivecraft's controller item path while VR is active.")
                .define("enableVrGunRendering", true);
        VR_GUN_SCALE_MULTIPLIER = builder
                .comment("Multiplier applied after Vivecraft's held-item scale for TACZ VR guns.")
                .defineInRange("vrGunScaleMultiplier", 1.0D, 0.25D, 2.5D);
        VR_GUN_GRIP_OFFSET_X = builder
                .comment("Controller-local X offset applied to the TACZ right-hand grip anchor.")
                .defineInRange("vrGunGripOffsetX", 0.49D, -1.0D, 1.0D);
        VR_GUN_GRIP_OFFSET_Y = builder
                .comment("Controller-local Y offset applied to the TACZ right-hand grip anchor.")
                .defineInRange("vrGunGripOffsetY", 0.52D, -1.0D, 1.0D);
        VR_GUN_GRIP_OFFSET_Z = builder
                .comment("Controller-local Z offset applied to the TACZ right-hand grip anchor.")
                .defineInRange("vrGunGripOffsetZ", 0.52D, -1.0D, 1.0D);
        VR_GUN_AIM_ROTATION_X = builder
                .comment("Additional X rotation for aligning the TACZ weapon axis to Vivecraft forward.")
                .defineInRange("vrGunAimRotationX", 0.0D, -360.0D, 360.0D);
        VR_GUN_AIM_ROTATION_Y = builder
                .comment("Additional Y rotation for aligning the TACZ weapon axis to Vivecraft forward.")
                .defineInRange("vrGunAimRotationY", 0.0D, -360.0D, 360.0D);
        VR_GUN_AIM_ROTATION_Z = builder
                .comment("Bedrock model basis correction around the weapon axis.")
                .defineInRange("vrGunAimRotationZ", 180.0D, -360.0D, 360.0D);
        VR_HAND_DISPLAY_MODE = builder
                .comment("Hand presentation: Vivecraft snap, all hidden, or Vivecraft idle with both TACZ hands during manipulation.")
                .defineEnum("vrHandDisplayMode", VrHandDisplayMode.VIVECRAFT_SNAP);
        HIDE_TACZ_MODEL_HANDS = builder
                .comment("In VIVECRAFT_SNAP mode, hide TACZ model hands while Vivecraft renders tracked hands.")
                .define("hideTaczModelHands", true);
        POST_RENDER_VIVECRAFT_GUN_HAND = builder
                .comment("Render the Vivecraft main hand after a TACZ gun so deep gun rigs cannot occlude it.")
                .define("postRenderVivecraftGunHand", true);
        USE_TACZ_HANDS_DURING_MANIPULATION = builder
                .comment("Use authored hand motion during reload and inspect animations.")
                .define("useTaczHandsDuringManipulation", true);
        USE_TACZ_HANDS_DURING_SPRINT = builder
                .comment("Use authored hand motion during run and sprint animations.")
                .define("useTaczHandsDuringSprint", true);
        ENABLE_VR_ANIMATION_HAND_SNAP = builder
                .comment("Force both Vivecraft palms onto TACZ hand anchors for the full authored animation.")
                .define("enableVrAnimationHandSnap", true);
        VR_ANIMATION_HAND_SNAP_SMOOTHING = builder
                .comment("Per-frame interpolation of the support hand into authored action anchors.")
                .defineInRange("vrAnimationHandSnapSmoothing", 1.0D, 0.05D, 1.0D);
        VR_ANIMATION_MAIN_HAND_SNAP_SMOOTHING = builder
                .comment("Faster main-hand tracking for reload, inspect, sprint, and other authored actions.")
                .defineInRange("vrAnimationMainHandSnapSmoothing", 1.0D, 0.05D, 1.0D);
        VR_ANIMATION_HAND_RELEASE_SMOOTHING = builder
                .comment("Per-frame interpolation back to tracked controllers after an authored animation ends.")
                .defineInRange("vrAnimationHandReleaseSmoothing", 0.55D, 0.05D, 1.0D);
        VR_ANIMATION_HAND_RELEASE_GRACE_MS = builder
                .comment("Short latch that bridges TACZ animation-track transitions before releasing VR hands.")
                .defineInRange("vrAnimationHandReleaseGraceMs", 35, 0, 250);
        VR_ANIMATION_HAND_MAX_TRANSLATION = builder
                .comment("Safety clamp for forced animation-hand displacement from each tracked controller, in blocks.")
                .defineInRange("vrAnimationHandMaxTranslation", 1.25D, 0.1D, 2.5D);
        ENABLE_VR_SUPPORT_HAND_SNAP = builder
                .comment("When Vivecraft hands are visible, snap an empty support hand to a nearby TACZ left-hand anchor.")
                .define("enableVrSupportHandSnap", true);
        ENABLE_VR_NEAR_MAIN_HAND_SUPPORT_SNAP = builder
                .comment(
                        "For non-pistol TACZ guns, let an empty support hand near the main hand enter the normal left-hand anchor.",
                        "This supports a compact pistol-style gesture without a physical gunstock; the hand still renders at the authored foregrip."
                )
                .define("enableVrNearMainHandSupportSnap", false);
        ENABLE_VR_SHIELD_HAND_SNAP = builder
                .comment("When Vivecraft hands are visible, let shield-capable offhand items use the TACZ left-hand anchor.")
                .define("enableVrShieldHandSnap", true);
        VR_SUPPORT_HAND_SNAP_ENTER_DISTANCE = builder
                .comment("Distance in blocks at which the support hand attaches to the TACZ left-hand anchor.")
                .defineInRange("vrSupportHandSnapEnterDistance", 0.12D, 0.02D, 0.75D);
        VR_SUPPORT_HAND_SNAP_EXIT_DISTANCE = builder
                .comment("Larger release distance used to prevent support-hand attachment flicker.")
                .defineInRange("vrSupportHandSnapExitDistance", 0.19D, 0.02D, 1.0D);
        VR_SUPPORT_HAND_SNAP_SMOOTHING = builder
                .comment("Per-frame interpolation from the tracked support hand into a nearby grip anchor.")
                .defineInRange("vrSupportHandSnapSmoothing", 0.40D, 0.05D, 1.0D);
        VR_SUPPORT_HAND_RELEASE_SMOOTHING = builder
                .comment("Per-frame interpolation back to the tracked support hand after leaving the grip.")
                .defineInRange("vrSupportHandReleaseSmoothing", 0.60D, 0.05D, 1.0D);
        ENABLE_VR_SHOOT_HAND_ANIMATION = builder
                .comment("Attach the Vivecraft main-hand palm to TACZ's animated right-hand anchor during SHOOT.")
                .define("enableVrShootHandAnimation", true);
        VR_SHOOT_HAND_SNAP_SMOOTHING = builder
                .comment("Per-frame interpolation toward the animated SHOOT palm anchor.")
                .defineInRange("vrShootHandSnapSmoothing", 0.85D, 0.05D, 1.0D);
        VR_SHOOT_HAND_MAX_TRANSLATION = builder
                .comment("Safety clamp for SHOOT main-hand displacement from the tracked controller, in blocks.")
                .defineInRange("vrShootHandMaxTranslation", 0.08D, 0.01D, 0.75D);
        DECOUPLE_VR_TRACER_OFFSET = builder
                .comment("Ignore TACZ's camera-relative muzzleRenderOffset when rendering VR tracers.")
                .define("decoupleVrTracerOffset", true);
        VR_TRACER_HORIZONTAL_CORRECTION = builder
                .comment("Independent camera-local horizontal correction for the decoupled VR tracer, in blocks.")
                .defineInRange("vrTracerHorizontalCorrection", -0.04D, -0.25D, 0.25D);
        VR_TRACER_VERTICAL_CORRECTION = builder
                .comment("Independent camera-local upward correction for the decoupled VR tracer, in blocks.")
                .defineInRange("vrTracerVerticalCorrection", 0.12D, -0.25D, 0.25D);
        CORRECT_VR_MUZZLE_FLASH_PER_EYE = builder
                .comment("Refresh TACZ's deferred muzzle-flash pose for each Vivecraft first-person eye pass.")
                .define("correctVrMuzzleFlashPerEye", true);
        HIDE_VIVECRAFT_CROSSHAIR_WITH_GUN = builder
                .comment("Hide Vivecraft's world-space crosshair while holding a TACZ gun.")
                .define("hideVivecraftCrosshairWithGun", true);
        ENABLE_VR_PHYSICAL_SCOPE_GATE = builder
                .comment(
                        "Experimental: let each render eye's eye-to-ocular alignment independently own its TACZ scope aperture.",
                        "TACZ aim remains available to stabilization but no longer scales this aperture; the other eye stays closed.",
                        "Red dots are unchanged. Enabled by default; invalid or unsupported scope facts still fail closed."
                )
                .define("enableVrPhysicalScopeGate", true);
        ENABLE_VR_SCOPE_MAGNIFICATION = builder
                .comment(
                        "Experimental Forge scope world texture; requires enableVrPhysicalScopeGate.",
                        "Enabled by default. Adds at most one shared scope pass in the bounded eye-box prewarm region.",
                        "Unsupported renderers fall back; Oculus requires its separate compatibility checks."
                )
                .define("enableVrScopeMagnification", true);
        ENABLE_VR_SCOPE_OCULUS_COMPAT = builder
                .comment(
                        "Experimental Oculus 1.8.0 scope support. Requires the physical gate and magnification.",
                        "With shaders, requires Vivecraft disableShaderOptimization and a shader reload for independent pipelines.",
                        "Enabled by default. Uses final-eye composition only; unsupported pipelines keep magnification disabled."
                )
                .define("enableVrScopeOculusCompat", true);
        DISABLE_TACZ_WORLD_SCOPE_FOV = builder
                .comment("Prevent TACZ scope magnification from changing the VR world FOV.")
                .define("disableTaczWorldScopeFov", true);
        DISABLE_TACZ_CAMERA_RECOIL = builder
                .comment("Prevent TACZ camera recoil from rotating the player view while VR is active.")
                .define("disableTaczCameraRecoil", true);
        DISABLE_TACZ_LEVEL_CAMERA_ANIMATION = builder
                .comment("Keep TACZ camera animations on the held gun instead of rotating the VR world camera.")
                .define("disableTaczLevelCameraAnimation", true);
        DISABLE_TACZ_SPRINT_ANIMATION_IN_VR = builder
                .comment("Replace TACZ's run animation input with walk while a gun is held in VR.")
                .define("disableTaczSprintAnimationInVr", true);
        builder.pop();

        builder.push("feedback");
        ENABLE_HAPTICS = builder
                .comment("Allow short Vivecraft controller haptics for TACZ VR interactions.")
                .define("enableHaptics", true);
        builder.pop();

        builder.push("controls");
        ENABLE_VR_GUN_RADIAL_MENU = builder
                .comment("Replace Vivecraft's right-hand radial menu with TACZ actions while holding a gun.")
                .define("enableVrGunRadialMenu", true);
        ENABLE_VR_SCOPE_ZOOM_CHORD = builder
                .comment(
                        "Hold Use/A and press the right radial/B button to cycle an optical scope's native TACZ zoom.",
                        "Consumes that radial press to avoid opening the menu or reloading on release. VR only."
                )
                .define("enableVrScopeZoomChord", true);
        ENABLE_VR_GUN_HOTBAR_RETREAT = builder
                .comment("Move Vivecraft's hand/wrist HUD and touch hotbar backward while a TACZ gun is held.")
                .define("enableVrGunHotbarRetreat", true);
        VR_GUN_HOTBAR_RETREAT_DISTANCE = builder
                .comment("Fixed room-space backward offset for the gun HUD and touch hotbar, in blocks.")
                .defineInRange("vrGunHotbarRetreatDistance", 0.10D, 0.0D, 0.50D);
        builder.pop();

        builder.push("compatibility");
        ENABLE_LRTACTICAL_THROWABLE_VR_COMPAT = builder
                .comment("Render LRTactical throwables at the tracked hand and bridge the main trigger to their native hold-and-release use flow.")
                .define("enableLrTacticalThrowableVrCompat", true);
        ENABLE_LRTACTICAL_CONSUMABLE_VR_RENDERING = builder
                .comment("Render LRTactical food and medicine with the validated throwable hand alignment.")
                .define("enableLrTacticalConsumableVrRendering", true);
        ENABLE_LRTACTICAL_MELEE_VR_RENDERING = builder
                .comment("Render LRTactical melee weapons with the validated throwable hand alignment; combat remains native.")
                .define("enableLrTacticalMeleeVrRendering", true);
        VR_LRTACTICAL_THROWABLE_SCALE = builder
                .comment("Shared first-person scale for supported LRTactical throwables, consumables and melee items.")
                .defineInRange("vrLrTacticalThrowableScale", 0.60D, 0.25D, 2.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private TaczVrClientConfig() {
    }
}
