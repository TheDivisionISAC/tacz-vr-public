package com.thedivisionisac.taczvr.network.message;

import com.thedivisionisac.taczvr.TaczVr;
import com.thedivisionisac.taczvr.config.TaczVrServerConfig;
import com.thedivisionisac.taczvr.server.shot.ServerShotPoseStore;
import com.thedivisionisac.taczvr.server.shot.ServerShotPoseValidator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Untrusted client shot facts correlated with TACZ's own shoot packet by timestamp.
 */
public record C2SVrShotPose(int protocolVersion, long taczTimestamp, byte flags, @Nullable Vec3 direction,
    @Nullable Vec3 muzzlePosition) {
    public static final int PROTOCOL_VERSION = 3;
    public static final byte FLAG_DIRECTION = 1;
    public static final byte FLAG_MUZZLE = 2;
    public static final byte FLAG_SUPPORT_HAND_SNAPPED = 4;

    public static C2SVrShotPose pose(long taczTimestamp, Vec3 direction, @Nullable Vec3 muzzlePosition,
                                    boolean supportHandSnapped) {
        byte flags = FLAG_DIRECTION;
        if (muzzlePosition != null) {
            flags |= FLAG_MUZZLE;
        }
        if (supportHandSnapped) {
            flags |= FLAG_SUPPORT_HAND_SNAPPED;
        }
        return new C2SVrShotPose(PROTOCOL_VERSION, taczTimestamp, flags, direction, muzzlePosition);
    }

    public boolean supportHandSnapped() {
        return (this.flags & FLAG_SUPPORT_HAND_SNAPPED) != 0;
    }

    public static void encode(C2SVrShotPose message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.protocolVersion);
        buffer.writeLong(message.taczTimestamp);
        buffer.writeByte(message.flags);
        if ((message.flags & FLAG_DIRECTION) != 0) {
            Vec3 direction = message.direction == null ? Vec3.ZERO : message.direction;
            buffer.writeFloat((float) direction.x);
            buffer.writeFloat((float) direction.y);
            buffer.writeFloat((float) direction.z);
        }
        if ((message.flags & FLAG_MUZZLE) != 0) {
            Vec3 muzzle = message.muzzlePosition == null ? Vec3.ZERO : message.muzzlePosition;
            buffer.writeDouble(muzzle.x);
            buffer.writeDouble(muzzle.y);
            buffer.writeDouble(muzzle.z);
        }
    }

    public static C2SVrShotPose decode(FriendlyByteBuf buffer) {
        int protocolVersion = buffer.readVarInt();
        long timestamp = buffer.readLong();
        byte flags = buffer.readByte();
        Vec3 direction = null;
        if ((flags & FLAG_DIRECTION) != 0) {
            direction = new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        }
        Vec3 muzzlePosition = null;
        if ((flags & FLAG_MUZZLE) != 0) {
            muzzlePosition = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }
        return new C2SVrShotPose(protocolVersion, timestamp, flags, direction, muzzlePosition);
    }

    public static void handle(C2SVrShotPose message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null
                    || message.protocolVersion != PROTOCOL_VERSION
                    || (message.flags & FLAG_DIRECTION) == 0
                    || message.direction == null
                    || !TaczVrServerConfig.ENABLE_VR_SHOT_DIRECTION.get()) {
                return;
            }
            ServerShotPoseValidator.validate(
                    sender,
                    message.direction,
                    message.muzzlePosition,
                    message.supportHandSnapped()
            ).ifPresentOrElse(pose -> {
                ServerShotPoseStore.put(sender, message.taczTimestamp, pose);
                TaczVr.LOGGER.debug("Accepted VR shot pose for {} at TACZ timestamp {} (muzzle: {}).",
                        sender.getScoreboardName(), message.taczTimestamp, pose.hasMuzzlePosition());
            }, () -> TaczVr.LOGGER.debug("Rejected VR shot pose for {} at TACZ timestamp {}.",
                    sender.getScoreboardName(), message.taczTimestamp));
        });
        context.setPacketHandled(true);
    }
}
