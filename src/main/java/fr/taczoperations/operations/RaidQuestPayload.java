package fr.taczoperations.operations;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

public record RaidQuestPayload(String trader,String title,List<Objective> objectives,int more) implements CustomPacketPayload {
    public record Objective(String title,int saved,int tentative,int target,boolean atBase,boolean optional){}
    public static final RaidQuestPayload EMPTY=new RaidQuestPayload("","",List.of(),0);
    public static final Type<RaidQuestPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("taczoperations","raid_quest"));
    public RaidQuestPayload{objectives=List.copyOf(objectives);if(objectives.size()>5||more<0)throw new IllegalArgumentException("Invalid tracked quest");}
    public static final StreamCodec<RegistryFriendlyByteBuf,RaidQuestPayload> CODEC=StreamCodec.of((buf,v)->{
        buf.writeUtf(v.trader(),256);buf.writeUtf(v.title(),1024);buf.writeVarInt(v.objectives().size());
        for(var o:v.objectives()){
            buf.writeUtf(o.title(),2048);buf.writeVarInt(o.saved());buf.writeVarInt(o.tentative());buf.writeVarInt(o.target());
            buf.writeBoolean(o.atBase());buf.writeBoolean(o.optional());
        }
        buf.writeVarInt(v.more());
    },buf->{
        String trader=buf.readUtf(256),title=buf.readUtf(1024);
        int n=buf.readVarInt();if(n<0||n>5)throw new IllegalArgumentException("Invalid objective count");
        var objectives=new ArrayList<Objective>(n);
        for(int i=0;i<n;i++){
            String label=buf.readUtf(2048);int saved=buf.readVarInt(),tentative=buf.readVarInt(),target=buf.readVarInt();
            if(target<1||saved<0||saved>target||tentative<0||tentative>target-saved)throw new IllegalArgumentException("Invalid progress");
            objectives.add(new Objective(label,saved,tentative,target,buf.readBoolean(),buf.readBoolean()));
        }
        return new RaidQuestPayload(trader,title,objectives,buf.readVarInt());
    });
    @Override public Type<RaidQuestPayload> type(){return TYPE;}
}
