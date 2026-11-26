package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-owned time remaining. The client does not advance the deadline independently. */
public record RaidTimerPayload(int seconds,boolean industrial) implements CustomPacketPayload {
    public static final Type<RaidTimerPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"raid_timer"));
    public static final StreamCodec<ByteBuf,RaidTimerPayload> STREAM_CODEC=StreamCodec.of(
        (buf,value)->{buf.writeInt(value.seconds());buf.writeBoolean(value.industrial());},
        buf->new RaidTimerPayload(buf.readInt(),buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
