package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidInjuryPayload(boolean active,int bleed,boolean fracture,boolean arm,int water,int energy,int hitZone,int bleedMask) implements CustomPacketPayload{
    public static final int HEAD=1,TORSO=2,LEGS=4,FEET=8;
    public static final Type<RaidInjuryPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"raid_injuries"));
    public static final StreamCodec<ByteBuf,RaidInjuryPayload> STREAM_CODEC=StreamCodec.of(
        (buf,value)->{buf.writeBoolean(value.active());buf.writeByte(value.bleed());buf.writeBoolean(value.fracture());buf.writeBoolean(value.arm());buf.writeByte(value.water());buf.writeByte(value.energy());buf.writeByte(value.hitZone());buf.writeByte(value.bleedMask());},
        buf->new RaidInjuryPayload(buf.readBoolean(),Math.clamp(buf.readUnsignedByte(),0,2),buf.readBoolean(),buf.readBoolean(),Math.clamp(buf.readUnsignedByte(),0,100),Math.clamp(buf.readUnsignedByte(),0,100),buf.readUnsignedByte()&15,buf.readUnsignedByte()&15));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
