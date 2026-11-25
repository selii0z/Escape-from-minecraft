package fr.taczoperations.operations;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenFieldInventory() implements CustomPacketPayload {
    public static final Type<OpenFieldInventory> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("taczoperations","open_field_inventory"));
    public static final StreamCodec<RegistryFriendlyByteBuf,OpenFieldInventory> CODEC=StreamCodec.unit(new OpenFieldInventory());
    @Override public Type<OpenFieldInventory> type(){return TYPE;}
}
