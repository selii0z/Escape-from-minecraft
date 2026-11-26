package fr.taczoperations.operations;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record CampaignAction(int menuId,int action) implements CustomPacketPayload {
    public static final Type<CampaignAction> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("taczoperations","campaign_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CampaignAction> CODEC=StreamCodec.of(
        (buf,v)->{buf.writeVarInt(v.menuId());buf.writeVarInt(v.action());},
        buf->new CampaignAction(buf.readVarInt(),buf.readVarInt()));
    @Override public Type<CampaignAction> type(){return TYPE;}
}
