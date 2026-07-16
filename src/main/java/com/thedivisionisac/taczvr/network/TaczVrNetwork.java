package com.thedivisionisac.taczvr.network;

import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.network.message.C2SVrShotPose;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Registers the optional addon channel. A missing remote channel is a supported TACZ fallback path.
 */
public final class TaczVrNetwork {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TaczVr.MOD_ID, "network"),
            () -> C2SVrShotPose.CHANNEL_PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(C2SVrShotPose.CHANNEL_PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(C2SVrShotPose.CHANNEL_PROTOCOL_VERSION)
    );

    private static boolean registered;

    private TaczVrNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(
                0,
                C2SVrShotPose.class,
                C2SVrShotPose::encode,
                C2SVrShotPose::decode,
                C2SVrShotPose::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }

    public static boolean isRemotePresent(Connection connection) {
        return CHANNEL.isRemotePresent(connection);
    }

    public static void sendToServer(C2SVrShotPose message) {
        CHANNEL.sendToServer(message);
    }
}
