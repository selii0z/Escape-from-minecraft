package fr.taczoperations.operations;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client supplies a price only; the server chooses the quoted inventory item. */
public record ListPlayerItem(int menuId,int price) implements CustomPacketPayload {
    public static final Type<ListPlayerItem> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("taczoperations","list_player_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ListPlayerItem> CODEC=StreamCodec.of(
        (buf,msg)->{buf.writeVarInt(msg.menuId());buf.writeVarInt(msg.price());},
        buf->new ListPlayerItem(buf.readVarInt(),buf.readVarInt()));
    @Override public Type<ListPlayerItem> type(){return TYPE;}
}
