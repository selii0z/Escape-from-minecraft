package fr.taczoperations.operations;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.ArrayList;

public record DiscoveryState(boolean replace,List<String> keys) implements CustomPacketPayload{
    public static final Type<DiscoveryState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("taczoperations","discovery_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DiscoveryState> CODEC=StreamCodec.of(
        (b,v)->{b.writeBoolean(v.replace);b.writeVarInt(v.keys.size());for(String key:v.keys)b.writeUtf(key,ItemDiscovery.MAX_KEY);},
        b->{boolean replace=b.readBoolean();int n=b.readVarInt();if(n<0||n>ItemDiscovery.LIMIT)throw new IllegalArgumentException("Invalid discovery count");
            var keys=new ArrayList<String>(n);for(int i=0;i<n;i++)keys.add(b.readUtf(ItemDiscovery.MAX_KEY));return new DiscoveryState(replace,List.copyOf(keys));});
    @Override public Type<DiscoveryState> type(){return TYPE;}
}
