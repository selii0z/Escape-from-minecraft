package fr.taczoperations.operations;

import fr.taczoperations.campaign.CampaignGraph;
import fr.taczoperations.campaign.CampaignGraph.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** EFM-only persistence. Per-quest definition signatures permit additive catalogue updates without resetting progress. */
final class CampaignStorage {
    private static final String KEY="efm_campaign_v2";
    private static final int SCHEMA=1;
    private static final String RETIRED_ARCHIVE="efm_campaign_retired_v1";
    private static final Set<String> RETIRED=loadRetired();
    private static Set<String> loadRetired(){
        try(var in=CampaignStorage.class.getResourceAsStream("/data/taczoperations/campaign/campaign-retired-quests.json")){
            if(in==null)return Set.of();
            var root=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Invalid retired quest manifest");
            var ids=new HashSet<String>();
            for(var id:root.getAsJsonArray("quests")){
                String value=id.getAsString();
                if(!value.matches("[0-9a-f]{24}")||!ids.add(value))throw new IllegalArgumentException("Invalid retired quest ID");
            }
            return Set.copyOf(ids);
        }catch(Exception e){throw new IllegalStateException("Cannot load retired campaign quests",e);}
    }
    static boolean retired(String id){return RETIRED.contains(id);}
    record Session(CampaignGraph.Player player,UUID raidId){}

    static Session load(ServerPlayer p,CampaignGraph graph,String revision,UUID currentRaid){
        var account=SupplyContract.account(p);
        if(!account.contains(KEY))return new Session(graph.newPlayer(),null);
        var root=account.getCompound(KEY);
        if(root.getInt("schema")!=SCHEMA)
            throw new IllegalStateException("Campaign save schema mismatch; preserve backup for migration");
        boolean revised=!revision.equals(root.getString("revision"));
        var saved=new HashMap<String,Progress>();
        var quests=root.getCompound("quests");
        for(var id:quests.getAllKeys()){
            var entry=quests.getCompound(id);
            if(!graph.quests().containsKey(id)&&retired(id)){
                // Keep the original state and counters for support/rollback. Do not complete or reward it.
                var archive=account.getCompound(RETIRED_ARCHIVE);
                if(!archive.contains(id))archive.put(id,entry.copy());
                account.put(RETIRED_ARCHIVE,archive);
                continue;
            }
            if(revised&&(!graph.quests().containsKey(id)||!graph.definitionFingerprint(id).equals(entry.getString("definition"))))
                throw new IllegalStateException("Incompatible quest update: "+id+"; save preserved for migration");
            saved.put(id,new Progress(State.valueOf(entry.getString("state")),counters(entry.getCompound("counters"))));
        }
        UUID stored=root.hasUUID("raid")?root.getUUID("raid"):null;
        var pending=new HashMap<String,Map<String,Integer>>();
        boolean sameRaid=stored!=null&&stored.equals(currentRaid);
        if(sameRaid){
            var pendingTag=root.getCompound("pending");
            for(var id:pendingTag.getAllKeys())if(!retired(id)||graph.quests().containsKey(id))pending.put(id,counters(pendingTag.getCompound(id)));
        }
        var player=graph.restore(new Checkpoint(saved,sameRaid,pending));
        if(stored!=null&&!sameRaid)CampaignRuntime.resetFailedRaidChallenges(player);
        return new Session(player,sameRaid?stored:null);
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
            entry.putString("definition",session.player().definitionFingerprint(id));
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
