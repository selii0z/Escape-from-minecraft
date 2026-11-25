package fr.taczoperations.operations;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Server-thread inventory transactions including equipped field packs.
 * The secure case and unequipped bags are intentionally excluded. */
final class CarriedStorage {
    private final ServerPlayer player;
    private final List<ItemStack> nativeItems;
    private final List<Pack> packs=new ArrayList<>();
    private record Pack(ItemStack carrier,List<ItemStack> items,boolean rig){}
    private CarriedStorage(ServerPlayer player){
        this.player=player;
        nativeItems=player.getInventory().items;
        for(String id:new String[]{FieldInventoryMenu.RIG,FieldInventoryMenu.BAG}){
            var carrier=FieldInventoryMenu.current(FieldInventoryMenu.handler(player,id));
            boolean rig=id.equals(FieldInventoryMenu.RIG);
            if(FieldPack.capacity(carrier,rig)>0)packs.add(new Pack(carrier,FieldPack.contents(carrier),rig));
        }
    }
    static CarriedStorage of(ServerPlayer player){return new CarriedStorage(player);}
    int count(Predicate<ItemStack> filter){
        long count=0;
        for(var s:nativeItems)if(!s.isEmpty()&&filter.test(s))count+=s.getCount();
        for(var pack:packs)for(var s:pack.items)if(!s.isEmpty()&&filter.test(s))count+=s.getCount();
        return (int)Math.min(Integer.MAX_VALUE,count);
    }
    boolean consume(Predicate<ItemStack> filter,int amount){
        if(amount<=0||count(filter)<amount)return false;
        int remaining=take(nativeItems,filter,amount);
        for(var pack:packs){
            if(remaining==0)break;
            int next=take(pack.items,filter,remaining);
            if(next!=remaining)FieldPack.replaceContents(pack.carrier,pack.items);
            remaining=next;
        }
        player.getInventory().setChanged();
        return remaining==0;
    }
    private static int take(List<ItemStack> items,Predicate<ItemStack> filter,int remaining){
        for(int i=0;i<items.size()&&remaining>0;i++){
            var s=items.get(i);if(s.isEmpty()||!filter.test(s))continue;
            int n=Math.min(remaining,s.getCount());s.shrink(n);remaining-=n;
            if(s.isEmpty())items.set(i,ItemStack.EMPTY);
        }
        return remaining;
    }
    /** Accept a copy of as many units as fit, shrinking the real source only
     * after writing the carrier component. Rig is preferred for compatible items. */
    int insertIntoPacks(ItemStack source){
        if(source.isEmpty())return 0;
        int total=0;
        for(var pack:packs){
            if(source.isEmpty())break;
            if(!FieldPack.accepts(source,pack.rig))continue;
            int remaining=source.getCount();
            for(var s:pack.items){
                if(remaining<=0)break;
                if(s.isEmpty()||!ItemStack.isSameItemSameComponents(s,source))continue;
                int n=Math.min(remaining,Math.max(0,s.getMaxStackSize()-s.getCount()));
                s.grow(n);remaining-=n;
            }
            for(int i=0;i<pack.items.size()&&remaining>0;i++){
                if(!pack.items.get(i).isEmpty())continue;
                int n=Math.min(remaining,source.getMaxStackSize());
                pack.items.set(i,source.copyWithCount(n));remaining-=n;
            }
            int moved=source.getCount()-remaining;
            if(moved>0){
                FieldPack.replaceContents(pack.carrier,pack.items);
                source.shrink(moved);total+=moved;
            }
        }
        if(total>0)player.getInventory().setChanged();
        return total;
    }
}
