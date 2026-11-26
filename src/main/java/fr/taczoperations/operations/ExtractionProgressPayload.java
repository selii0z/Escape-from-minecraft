package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExtractionProgressPayload(boolean active,int elapsed) implements CustomPacketPayload {
    public static final Type<ExtractionProgressPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"extraction_progress"));
    public static final StreamCodec<ByteBuf,ExtractionProgressPayload> STREAM_CODEC=StreamCodec.of(
        (buf,value)->{buf.writeBoolean(value.active());buf.writeInt(value.elapsed());},
        buf->new ExtractionProgressPayload(buf.readBoolean(),buf.readInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
