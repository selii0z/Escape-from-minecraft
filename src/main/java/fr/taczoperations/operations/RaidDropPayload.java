package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidDropPayload(boolean active, boolean landed, int x, int y, int z) implements CustomPacketPayload {
    public static final Type<RaidDropPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OperationsMod.ID, "raid_drop"));
    public static final StreamCodec<ByteBuf, RaidDropPayload> STREAM_CODEC = StreamCodec.of(
        (buf, value) -> {
            buf.writeBoolean(value.active());
            buf.writeBoolean(value.landed());
            buf.writeInt(value.x()); buf.writeInt(value.y()); buf.writeInt(value.z());
        },
        buf -> new RaidDropPayload(buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
