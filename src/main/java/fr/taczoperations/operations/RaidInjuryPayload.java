package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidInjuryPayload(boolean active,int bleed,boolean fracture,boolean arm,int water,int energy,int hitZone,int bleedMask,int fractureZone,int armZone,int bandages,int hemostats,int splints,int salewas,int medkits) implements CustomPacketPayload{
    public static final int HEAD=1,TORSO=2,LEGS=4,FEET=8,LEFT_ARM=16,RIGHT_ARM=32,LEFT_LEG=64,RIGHT_LEG=128,LEFT_FOOT=256,RIGHT_FOOT=512;
    public static final int ARMS=LEFT_ARM|RIGHT_ARM;
    public static final Type<RaidInjuryPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"raid_injuries"));
    public static final StreamCodec<ByteBuf,RaidInjuryPayload> STREAM_CODEC=StreamCodec.of(
        (buf,value)->{buf.writeBoolean(value.active());buf.writeByte(value.bleed());buf.writeBoolean(value.fracture());buf.writeBoolean(value.arm());buf.writeByte(value.water());buf.writeByte(value.energy());buf.writeShort(value.hitZone());buf.writeShort(value.bleedMask());buf.writeShort(value.fractureZone());buf.writeShort(value.armZone());buf.writeByte(value.bandages());buf.writeByte(value.hemostats());buf.writeByte(value.splints());buf.writeByte(value.salewas());buf.writeByte(value.medkits());},
        buf->new RaidInjuryPayload(buf.readBoolean(),Math.clamp(buf.readUnsignedByte(),0,2),buf.readBoolean(),buf.readBoolean(),Math.clamp(buf.readUnsignedByte(),0,100),Math.clamp(buf.readUnsignedByte(),0,100),buf.readUnsignedShort()&1023,buf.readUnsignedShort()&1023,buf.readUnsignedShort()&1023,buf.readUnsignedShort()&1023,readCount(buf),readCount(buf),readCount(buf),readCount(buf),readCount(buf)));
    private static int readCount(ByteBuf buf){return Math.min(99,buf.readUnsignedByte());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
