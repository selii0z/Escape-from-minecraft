package fr.taczoperations.operations;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Only the selected merchant is sent. No virtual inventory or menu reopen is necessary. */
public record CampaignTabData(int menuId,int trader,int credits,int experience,long inbox,List<Integer> loyalties,List<Quest> quests,String notice) implements CustomPacketPayload {
    public record Quest(String id,String name,String status,boolean playable,boolean followed,String art,int objectives,List<String> lines,List<ItemStack> rewards){
        public Quest{lines=List.copyOf(lines);rewards=List.copyOf(rewards);}
    }
    public CampaignTabData{loyalties=List.copyOf(loyalties);quests=List.copyOf(quests);}
    public static final Type<CampaignTabData> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("taczoperations","campaign_tab_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CampaignTabData> CODEC=StreamCodec.of((b,v)->{
        b.writeVarInt(v.menuId());b.writeVarInt(v.trader());b.writeVarInt(v.credits());b.writeVarInt(v.experience());b.writeVarLong(v.inbox());
        b.writeCollection(v.loyalties(),(buf,i)->buf.writeVarInt(i));
        b.writeCollection(v.quests(),(buf,q)->{
            buf.writeUtf(q.id(),64);buf.writeUtf(q.name(),1024);buf.writeUtf(q.status(),64);buf.writeBoolean(q.playable());buf.writeBoolean(q.followed());
            buf.writeUtf(q.art(),64);buf.writeVarInt(q.objectives());buf.writeCollection(q.lines(),(out,s)->out.writeUtf(s));
            buf.writeCollection(q.rewards(),(out,stack)->ItemStack.OPTIONAL_STREAM_CODEC.encode(out,stack));
        });
        b.writeUtf(v.notice(),1024);
    },b->new CampaignTabData(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarLong(),
        b.readList(buf->buf.readVarInt()),b.readList(buf->new Quest(buf.readUtf(64),buf.readUtf(1024),buf.readUtf(64),buf.readBoolean(),buf.readBoolean(),buf.readUtf(64),buf.readVarInt(),buf.readList(in->in.readUtf()),buf.readList(in->ItemStack.OPTIONAL_STREAM_CODEC.decode(in)))),b.readUtf(1024)));
    @Override public Type<CampaignTabData> type(){return TYPE;}
}
