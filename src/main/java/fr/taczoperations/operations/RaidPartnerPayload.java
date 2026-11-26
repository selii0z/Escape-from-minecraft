package fr.taczoperations.operations;
import fr.taczoperations.OperationsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record RaidPartnerPayload(boolean visible,String name,double x,double y,double z) implements CustomPacketPayload {
    public static final Type<RaidPartnerPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"raid_partner"));
    public static final StreamCodec<ByteBuf,RaidPartnerPayload> STREAM_CODEC=StreamCodec.of(
        (buf,v)->{buf.writeBoolean(v.visible());writeName(buf,v.name());buf.writeDouble(v.x());buf.writeDouble(v.y());buf.writeDouble(v.z());},
        buf->new RaidPartnerPayload(buf.readBoolean(),readName(buf),buf.readDouble(),buf.readDouble(),buf.readDouble()));
    private static void writeName(ByteBuf buf,String name){byte[] bytes=name.getBytes(java.nio.charset.StandardCharsets.UTF_8);int n=Math.min(64,bytes.length);buf.writeByte(n);buf.writeBytes(bytes,0,n);}
    private static String readName(ByteBuf buf){int n=buf.readUnsignedByte();if(n>64)throw new IllegalArgumentException("Invalid partner name");byte[] bytes=new byte[n];buf.readBytes(bytes);return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
