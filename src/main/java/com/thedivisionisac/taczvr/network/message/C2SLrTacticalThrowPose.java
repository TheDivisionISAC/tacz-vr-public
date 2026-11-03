package com.thedivisionisac.taczvr.network.message;

import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.config.TaczVrServerConfig;
import com.thedivisionisac.taczvr.server.lrtactical.ServerLrTacticalThrowPoseStore;
import com.thedivisionisac.taczvr.server.shot.ServerShotPoseValidator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One untrusted controller direction sent immediately before vanilla's release-use packet.
 */
public record C2SLrTacticalThrowPose(int protocolVersion, Vec3 direction) {
    public static final int PROTOCOL_VERSION = 1;

    public static C2SLrTacticalThrowPose pose(Vec3 direction) {
        return new C2SLrTacticalThrowPose(PROTOCOL_VERSION, direction);
    }

    public static void encode(C2SLrTacticalThrowPose message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.protocolVersion);
        buffer.writeFloat((float) message.direction.x);
        buffer.writeFloat((float) message.direction.y);
        buffer.writeFloat((float) message.direction.z);
    }

    public static C2SLrTacticalThrowPose decode(FriendlyByteBuf buffer) {
        int protocolVersion = buffer.readVarInt();
        Vec3 direction = new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        return new C2SLrTacticalThrowPose(protocolVersion, direction);
    }

    public static void handle(C2SLrTacticalThrowPose message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null
                    || message.protocolVersion != PROTOCOL_VERSION
                    || !TaczVrServerConfig.ENABLE_LRTACTICAL_VR_THROW_DIRECTION.get()
                    || !ModList.get().isLoaded("lrtactical")) {
                return;
            }
            ServerShotPoseValidator.validateDirection(sender, message.direction).ifPresentOrElse(direction -> {
                ServerLrTacticalThrowPoseStore.put(sender, direction);
                TaczVr.LOGGER.debug("Accepted LRTactical VR throw direction for {}.",
                        sender.getScoreboardName());
            }, () -> TaczVr.LOGGER.debug("Rejected LRTactical VR throw direction for {}.",
                    sender.getScoreboardName()));
        });
        context.setPacketHandled(true);
    }
}
