package fr.taczoperations.operations;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Quantity is a number of catalogue lots, not a client-defined item count. */
public record ShopBuy(int menuId,int row,int lots) implements CustomPacketPayload {
    public static final Type<ShopBuy> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("taczoperations","shop_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ShopBuy> CODEC=StreamCodec.of(
        (b,v)->{b.writeVarInt(v.menuId());b.writeVarInt(v.row());b.writeVarInt(v.lots());},
        b->new ShopBuy(b.readVarInt(),b.readVarInt(),b.readVarInt()));
    @Override public Type<ShopBuy> type(){return TYPE;}
}
