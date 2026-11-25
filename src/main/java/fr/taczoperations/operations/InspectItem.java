package fr.taczoperations.operations;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** No client-supplied item identity; index is resolved against the active server menu. */
public record InspectItem(int menuId,int kind,int index) implements CustomPacketPayload{
    public static final Type<InspectItem> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("taczoperations","inspect_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf,InspectItem> CODEC=StreamCodec.of(
        (b,v)->{b.writeVarInt(v.menuId);b.writeVarInt(v.kind);b.writeVarInt(v.index);},
        b->new InspectItem(b.readVarInt(),b.readVarInt(),b.readVarInt()));
    @Override public Type<InspectItem> type(){return TYPE;}
}
