package fr.taczoperations.operations;

import fr.taczoperations.campaign.CampaignGraph;
import fr.taczoperations.campaign.CampaignGraph.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** EFM-only persistence. Catalog revision and raid identity prevent stale progress from being reused. */
final class CampaignStorage {
    private static final String KEY="efm_campaign_v2";
    private static final int SCHEMA=1;
    record Session(CampaignGraph.Player player,UUID raidId){}

    static Session load(ServerPlayer p,CampaignGraph graph,String revision,UUID currentRaid){
        var account=SupplyContract.account(p);
        if(!account.contains(KEY))return new Session(graph.newPlayer(),null);
        var root=account.getCompound(KEY);
        if(root.getInt("schema")!=SCHEMA||!revision.equals(root.getString("revision")))
            throw new IllegalStateException("Campaign save schema/catalog mismatch; explicit EFM wipe required");
        var saved=new HashMap<String,Progress>();
        var quests=root.getCompound("quests");
        for(var id:quests.getAllKeys()){
            var entry=quests.getCompound(id);
            saved.put(id,new Progress(State.valueOf(entry.getString("state")),counters(entry.getCompound("counters"))));
        }
        UUID stored=root.hasUUID("raid")?root.getUUID("raid"):null;
        var pending=new HashMap<String,Map<String,Integer>>();
        boolean sameRaid=stored!=null&&stored.equals(currentRaid);
        if(sameRaid){
            var pendingTag=root.getCompound("pending");
            for(var id:pendingTag.getAllKeys())pending.put(id,counters(pendingTag.getCompound(id)));
        }
        return new Session(graph.restore(new Checkpoint(saved,sameRaid,pending)),sameRaid?stored:null);
    }
    static void save(ServerPlayer p,String revision,Session session){
        var cp=session.player().checkpoint();
        if(cp.inRaid()!=(session.raidId()!=null))throw new IllegalArgumentException("Missing or stale raid identity");
        var root=new CompoundTag();
        root.putInt("schema",SCHEMA);root.putString("revision",revision);
        var quests=new CompoundTag();
        cp.saved().forEach((id,progress)->{
            var entry=new CompoundTag();
            entry.putString("state",progress.state().name());
            entry.put("counters",tag(progress.counters()));
            quests.put(id,entry);
        });
        root.put("quests",quests);
        if(cp.inRaid()){
            root.putUUID("raid",session.raidId());
            var pending=new CompoundTag();
            cp.pending().forEach((id,values)->pending.put(id,tag(values)));
            root.put("pending",pending);
        }
        SupplyContract.account(p).put(KEY,root);
    }
    private static CompoundTag tag(Map<String,Integer> counters){
        var result=new CompoundTag();counters.forEach(result::putInt);return result;
    }
    private static Map<String,Integer> counters(CompoundTag tag){
        var result=new HashMap<String,Integer>();
        for(var id:tag.getAllKeys()){
            if(!tag.contains(id,Tag.TAG_INT))throw new IllegalArgumentException("Invalid campaign counter "+id);
            result.put(id,tag.getInt(id));
        }
        return result;
    }
    private CampaignStorage(){}
}
