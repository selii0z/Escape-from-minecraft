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
    public static final StreamCodec<RegistryFriendlyByteBuf,CampaignTabData> CODEC=StreamCodec.of(CampaignTabData::write,CampaignTabData::read);
    private static void write(RegistryFriendlyByteBuf b,CampaignTabData v){
        b.writeVarInt(v.menuId());b.writeVarInt(v.trader());b.writeVarInt(v.credits());b.writeVarInt(v.experience());b.writeVarLong(v.inbox());
        b.writeVarInt(v.loyalties().size());for(int i:v.loyalties())b.writeVarInt(i);
        b.writeVarInt(v.quests().size());
        for(var q:v.quests()){
            b.writeUtf(q.id(),64);b.writeUtf(q.name(),1024);b.writeUtf(q.status(),64);b.writeBoolean(q.playable());b.writeBoolean(q.followed());
            b.writeUtf(q.art(),64);b.writeVarInt(q.objectives());b.writeVarInt(q.lines().size());for(var line:q.lines())b.writeUtf(line);
            b.writeVarInt(q.rewards().size());for(var stack:q.rewards())ItemStack.OPTIONAL_STREAM_CODEC.encode(b,stack);
        }
        b.writeUtf(v.notice(),1024);
    }
    private static int size(RegistryFriendlyByteBuf b,int max){int n=b.readVarInt();if(n<0||n>max)throw new IllegalArgumentException("Invalid campaign data size");return n;}
    private static CampaignTabData read(RegistryFriendlyByteBuf b){
        int menu=b.readVarInt(),trader=b.readVarInt(),credits=b.readVarInt(),xp=b.readVarInt();long inbox=b.readVarLong();
        int n=size(b,32);var loyalty=new ArrayList<Integer>(n);for(int i=0;i<n;i++)loyalty.add(b.readVarInt());
        n=size(b,1000);var quests=new ArrayList<Quest>(n);
        for(int i=0;i<n;i++){
            String id=b.readUtf(64),name=b.readUtf(1024),status=b.readUtf(64);boolean playable=b.readBoolean(),followed=b.readBoolean();
            String art=b.readUtf(64);int objectives=b.readVarInt(),linesCount=size(b,2048);
            var lines=new ArrayList<String>(linesCount);for(int j=0;j<linesCount;j++)lines.add(b.readUtf());
            int count=size(b,1024);var rewards=new ArrayList<ItemStack>(count);for(int j=0;j<count;j++)rewards.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(b));
            quests.add(new Quest(id,name,status,playable,followed,art,objectives,lines,rewards));
        }
        return new CampaignTabData(menu,trader,credits,xp,inbox,loyalty,quests,b.readUtf(1024));
    }
    @Override public Type<CampaignTabData> type(){return TYPE;}
}
