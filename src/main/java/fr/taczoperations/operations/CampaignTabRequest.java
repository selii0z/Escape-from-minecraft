package fr.taczoperations.operations;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record CampaignTabRequest(int menuId,int trader,int action,String quest) implements CustomPacketPayload {
    public static final Type<CampaignTabRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("taczoperations","campaign_tab_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CampaignTabRequest> CODEC=StreamCodec.of(
        (b,v)->{b.writeVarInt(v.menuId());b.writeVarInt(v.trader());b.writeVarInt(v.action());b.writeUtf(v.quest(),64);},
        b->new CampaignTabRequest(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(64)));
    @Override public Type<CampaignTabRequest> type(){return TYPE;}
}
