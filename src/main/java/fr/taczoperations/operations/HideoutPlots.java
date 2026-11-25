package fr.taczoperations.operations;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** One stable plot per UUID, never recycled. Stored in the hideout dimension. */
final class HideoutPlots extends SavedData {
    private static final Factory<HideoutPlots> FACTORY=new Factory<>(HideoutPlots::new,HideoutPlots::read);
    private static final int LIMIT=100000;
    private final Map<UUID,Integer> plots=new HashMap<>();
    private int next=0;
    static HideoutPlots of(ServerLevel level){return level.getDataStorage().computeIfAbsent(FACTORY,"efm_hideout_plots");}
    int find(UUID owner){return plots.getOrDefault(owner,-1);}
    int allocate(UUID owner){
        int old=find(owner);if(old>=0)return old;
        if(next>=LIMIT)return -1;
        int id=next++;plots.put(owner,id);setDirty();return id;
    }
    private static HideoutPlots read(CompoundTag tag,HolderLookup.Provider registries){
        var data=new HideoutPlots();
        var used=new java.util.HashSet<Integer>();
        for(var raw:tag.getList("plots",10)){
            if(!(raw instanceof CompoundTag entry)||!entry.hasUUID("owner"))continue;
            int id=entry.getInt("id");var owner=entry.getUUID("owner");
            if(id<0||id>=LIMIT||data.plots.containsKey(owner)||!used.add(id))continue;
            data.plots.put(owner,id);data.next=Math.max(data.next,id+1);
        }
        data.next=Math.clamp(Math.max(data.next,tag.getInt("next")),0,LIMIT);
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var list=new ListTag();plots.forEach((owner,id)->{var entry=new CompoundTag();entry.putUUID("owner",owner);entry.putInt("id",id);list.add(entry);});
        tag.put("plots",list);tag.putInt("next",next);return tag;
    }
}
