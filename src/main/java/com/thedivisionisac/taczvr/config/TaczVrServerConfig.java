package com.thedivisionisac.taczvr.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Authoritative acceptance limits for untrusted client shot poses.
 */
public final class TaczVrServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_SHOT_DIRECTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_MUZZLE_ORIGIN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VR_PHYSICAL_GRIP_ACCURACY;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_SERVER_VIVECRAFT_POSE;
    public static final ForgeConfigSpec.DoubleValue MAX_SERVER_POSE_ANGLE_DEGREES;
    public static final ForgeConfigSpec.DoubleValue MAX_FALLBACK_VIEW_ANGLE_DEGREES;
    public static final ForgeConfigSpec.DoubleValue MAX_MUZZLE_HAND_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue MAX_MUZZLE_EYE_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue CHECK_MUZZLE_OBSTRUCTION;
    public static final ForgeConfigSpec.IntValue SHOT_POSE_TTL_TICKS;
    public static final ForgeConfigSpec.IntValue MAX_PENDING_SHOT_POSES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LRTACTICAL_VR_THROW_DIRECTION;
    public static final ForgeConfigSpec.IntValue LRTACTICAL_THROW_POSE_TTL_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("shooting");
        ENABLE_VR_SHOT_DIRECTION = builder
                .comment("Accept validated TACZ-VR controller shot directions.")
                .define("enableVrShotDirection", true);
        ENABLE_VR_MUZZLE_ORIGIN = builder
                .comment("Move TACZ bullets to a validated VR muzzle position when one is supplied.")
                .define("enableVrMuzzleOrigin", true);
        ENABLE_VR_PHYSICAL_GRIP_ACCURACY = builder
                .comment(
                        "Use TACZ AIM inaccuracy for VR pistols and physically two-handed long guns.",
                        "Unsnapped long guns retain TACZ hip-fire inaccuracy; ADS only controls optical-sight behavior."
                )
                .define("enableVrPhysicalGripAccuracy", true);
        REQUIRE_SERVER_VIVECRAFT_POSE = builder
                .comment(
                        "Require Vivecraft's server-side hand pose before accepting a controller shot direction.",
                        "When unavailable, the shot safely falls back to TACZ's player-view direction."
                )
                .define("requireServerVivecraftPose", true);
        MAX_SERVER_POSE_ANGLE_DEGREES = builder
                .comment("Maximum angle between client and Vivecraft server main-hand directions.")
                .defineInRange("maxServerPoseAngleDegrees", 60.0, 1.0, 180.0);
        MAX_FALLBACK_VIEW_ANGLE_DEGREES = builder
                .comment(
                        "Opt-in fallback angle against player view when requireServerVivecraftPose is disabled.",
                        "This compatibility path cannot authenticate room-scale controller direction."
                )
                .defineInRange("maxFallbackViewAngleDegrees", 150.0, 1.0, 180.0);
        MAX_MUZZLE_HAND_DISTANCE = builder
                .comment("Maximum accepted distance in blocks between Vivecraft's server hand pose and the muzzle.")
                .defineInRange("maxMuzzleHandDistance", 2.5, 0.25, 8.0);
        MAX_MUZZLE_EYE_DISTANCE = builder
                .comment("Maximum accepted distance in blocks between the player eye and the muzzle.")
                .defineInRange("maxMuzzleEyeDistance", 3.0, 0.25, 8.0);
        CHECK_MUZZLE_OBSTRUCTION = builder
                .comment("Clamp an eye-obstructed muzzle to the near wall face; reject hand-only obstruction.")
                .define("checkMuzzleObstruction", true);
        SHOT_POSE_TTL_TICKS = builder
                .comment("Lifetime of an unconsumed VR shot direction in server ticks.")
                .defineInRange("shotPoseTtlTicks", 10, 1, 100);
        MAX_PENDING_SHOT_POSES = builder
                .comment("Maximum pending VR shot directions retained per player.")
                .defineInRange("maxPendingShotPoses", 16, 1, 128);
        builder.pop();
        builder.push("lrtactical");
        ENABLE_LRTACTICAL_VR_THROW_DIRECTION = builder
                .comment(
                        "Accept validated controller directions for optional LRTactical throwable items.",
                        "LRTactical still owns entity creation, initial speed, cooldowns, fuses and item consumption."
                )
                .define("enableVrThrowDirection", true);
        LRTACTICAL_THROW_POSE_TTL_TICKS = builder
                .comment("Lifetime of the single pending LRTactical throw direction in server ticks.")
                .defineInRange("throwPoseTtlTicks", 4, 1, 20);
        builder.pop();
        SPEC = builder.build();
    }

    private TaczVrServerConfig() {
    }
}
