package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidExitPayload(boolean visible,boolean industrial,int primaryDistance,int secondState,
                              int secondDistance,int thirdState,int thirdDistance,int price) implements CustomPacketPayload {
    public static final int HIDDEN=0,OPEN=1,CLOSED=2,RELAY=3,KEY=4,CREDITS=5;
    public static final Type<RaidExitPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"raid_exits"));
    public static final StreamCodec<ByteBuf,RaidExitPayload> STREAM_CODEC=StreamCodec.of(
        (buf,v)->{buf.writeBoolean(v.visible());buf.writeBoolean(v.industrial());buf.writeInt(v.primaryDistance());
            buf.writeByte(v.secondState());buf.writeInt(v.secondDistance());buf.writeByte(v.thirdState());
            buf.writeInt(v.thirdDistance());buf.writeInt(v.price());},
        buf->new RaidExitPayload(buf.readBoolean(),buf.readBoolean(),buf.readInt(),buf.readUnsignedByte(),
            buf.readInt(),buf.readUnsignedByte(),buf.readInt(),buf.readInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
