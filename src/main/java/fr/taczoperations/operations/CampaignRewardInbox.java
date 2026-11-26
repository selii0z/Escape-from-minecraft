package fr.taczoperations.operations;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;

/** Persistent, account-scoped, non-FIR quest reward deliveries. No items are dropped. */
final class CampaignRewardInbox {
    private static final String KEY="campaign_reward_inbox";
    static boolean canQueue(ServerPlayer p,Map<String,Integer> rewards){
        var box=SupplyContract.account(p).getCompound(KEY);
        for(var e:rewards.entrySet()){
            if(e.getValue()<=0||!CampaignSupplies.rewardKnown(e.getKey())
                ||(long)box.getInt(e.getKey())+e.getValue()>Integer.MAX_VALUE)return false;
        }
        return true;
    }
    static void queue(ServerPlayer p,Map<String,Integer> rewards){
        if(rewards.isEmpty())return;
        if(!canQueue(p,rewards))throw new IllegalArgumentException("Invalid reward delivery");
        var account=SupplyContract.account(p);
        var box=account.getCompound(KEY);
        rewards.forEach((id,count)->box.putInt(id,box.getInt(id)+count));
        account.put(KEY,box);
    }
    static long pending(ServerPlayer p){
        var box=SupplyContract.account(p).getCompound(KEY);
        long total=0;
        for(var id:box.getAllKeys())total+=Math.max(0,box.getInt(id));
        return total;
    }
    static int collect(ServerPlayer p){
        if(!PlayerMarket.available(p)||ForestExpedition.hasReturnPoint(p)||p.isCreative()||p.isSpectator())return 0;
        var account=SupplyContract.account(p);var box=account.getCompound(KEY);
        int delivered=0;
        for(var id:new java.util.TreeSet<>(box.getAllKeys())){
            int left=box.getInt(id);
            if(left<=0)continue;
            var template=CampaignSupplies.rewardStack(id);
            if(template.isEmpty())continue;
            while(left>0&&delivered<4096){
                int batch=Math.min(Math.min(left,template.getMaxStackSize()),4096-delivered);
                var stack=template.copyWithCount(batch);
                p.getInventory().add(stack);
                int moved=batch-stack.getCount();
                if(moved<=0)break;
                delivered+=moved;left-=moved;
                if(left==0)box.remove(id);else box.putInt(id,left);
                account.put(KEY,box);
                if(moved<batch)break;
            }
            if(delivered>=4096)break;
        }
        p.getInventory().setChanged();
        return delivered;
    }
    private CampaignRewardInbox(){}
}
