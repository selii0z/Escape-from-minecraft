package fr.taczoperations.operations;

import com.tacz.guns.api.item.IGun;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;

/** Net additional saleable items at extraction; advisory, never a payout. */
final class RaidLootValue {
    private static final String BASE="efm_loot_value_baseline";
    record Estimate(long credits,int count){}
    private static String key(ItemStack stack){
        if(stack.getItem() instanceof IGun gun)return "gun/"+gun.getGunId(stack);
        return "item/"+BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
    private static void count(Map<String,Integer> counts,ItemStack stack){
        if(!stack.isEmpty())counts.merge(key(stack),stack.getCount(),Integer::sum);
    }
    private static Map<String,Integer> inventory(ServerPlayer p){
        Map<String,Integer> counts=new HashMap<>();
        for(int i=0;i<p.getInventory().getContainerSize();i++){
            ItemStack stack=p.getInventory().getItem(i);
            count(counts,stack);
            for(ItemStack inside:FieldPack.contents(stack))count(counts,inside);
            if(stack.getItem() instanceof SecureCaseItem)
                for(ItemStack inside:RaidSecurity.caseContents(p,stack))count(counts,inside);
        }
        for(String id:new String[]{FieldInventoryMenu.GOGGLES,FieldInventoryMenu.BAG,FieldInventoryMenu.RIG}){
            var stack=FieldInventoryMenu.current(FieldInventoryMenu.handler(p,id));
            count(counts,stack);
            for(var inside:FieldPack.contents(stack))count(counts,inside);
        }
        return counts;
    }
    static void departure(ServerPlayer p){
        CompoundTag baseline=new CompoundTag();
        inventory(p).forEach(baseline::putInt);
        p.getPersistentData().put(BASE,baseline);
    }
    static Estimate estimate(ServerPlayer p){
        if(!p.getPersistentData().contains(BASE,10))return new Estimate(-1,0);
        CompoundTag baseline=p.getPersistentData().getCompound(BASE);
        var current=inventory(p);
        Map<String,Integer> prices=new HashMap<>();
        long day=ResaleMarket.day();
        for(var merchant:OperationsShop.values())for(var offer:merchant.offers()){
            if(offer.action()!=OperationsShop.Action.SELL||offer.price()<=0)continue;
            ItemStack sample=offer.stack().get();
            if(!sample.isEmpty())prices.merge(key(sample),ResaleMarket.price(offer,day),Math::max);
        }
        long value=0;
        int amount=0;
        for(var entry:current.entrySet()){
            int price=prices.getOrDefault(entry.getKey(),0);
            int extra=Math.max(0,entry.getValue()-baseline.getInt(entry.getKey()));
            if(price>0&&extra>0){
                value+=(long)price*extra;
                amount+=extra;
            }
        }
        return new Estimate(value,amount);
    }
    static void clear(ServerPlayer p){p.getPersistentData().remove(BASE);}
    private RaidLootValue(){}
}
