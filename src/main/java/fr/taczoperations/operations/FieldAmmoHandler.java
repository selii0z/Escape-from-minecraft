package fr.taczoperations.operations;

import com.tacz.guns.api.item.IAmmo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.ArrayList;
import java.util.List;

/** A narrow, extraction-only extension of the native TaCZ ammo lookup. */
public final class FieldAmmoHandler implements IItemHandler {
    private record Storage(String id,ItemStack carrier,int size){}
    private final Player player;
    private final IItemHandler nativeHandler;
    private final int nativeSize;
    private final List<Storage> stores=new ArrayList<>();
    private final int size;
    private FieldAmmoHandler(Player p,IItemHandler original){
        player=p;nativeHandler=original;nativeSize=original.getSlots();int n=nativeSize;
        for(String id:new String[]{FieldInventoryMenu.RIG,FieldInventoryMenu.BAG}){
            var carrier=FieldInventoryMenu.current(FieldInventoryMenu.handler(p,id));
            int capacity=FieldPack.capacity(carrier,id.equals(FieldInventoryMenu.RIG));
            if(capacity>0){stores.add(new Storage(id,carrier,capacity));n+=capacity;}
        }
        size=n;
    }
    public static IItemHandler extend(LivingEntity entity,IItemHandler original){
        if(original==null||original instanceof FieldAmmoHandler||!(entity instanceof Player p)||p.isSpectator()||p.isCreative())return original;
        var extended=new FieldAmmoHandler(p,original);
        return extended.size==extended.nativeSize?original:extended;
    }
    private record Position(Storage storage,int slot){}
    private Position position(int slot){
        int index=slot-nativeSize;
        for(var store:stores){
            if(index<store.size)return index<0?null:new Position(store,index);
            index-=store.size;
        }
        return null;
    }
    private boolean valid(Storage store){
        return player.isAlive()&&FieldInventoryMenu.current(FieldInventoryMenu.handler(player,store.id))==store.carrier;
    }
    @Override public int getSlots(){return size;}
    @Override public ItemStack getStackInSlot(int slot){
        if(slot>=0&&slot<nativeSize)return nativeHandler.getStackInSlot(slot);
        var pos=position(slot);if(pos==null||!valid(pos.storage))return ItemStack.EMPTY;
        var stack=FieldPack.contents(pos.storage.carrier).get(pos.slot);
        // Only loose TaCZ rounds are exposed; boxes and other equipment
        // have separate mutable semantics and are deliberately not exposed.
        return stack.getItem() instanceof IAmmo?stack.copy():ItemStack.EMPTY;
    }
    @Override public ItemStack extractItem(int slot,int amount,boolean simulate){
        if(slot>=0&&slot<nativeSize)return nativeHandler.extractItem(slot,amount,simulate);
        var pos=position(slot);if(pos==null||amount<=0||!valid(pos.storage))return ItemStack.EMPTY;
        var items=FieldPack.contents(pos.storage.carrier);
        var stack=items.get(pos.slot);
        if(!(stack.getItem() instanceof IAmmo)||stack.isEmpty())return ItemStack.EMPTY;
        int n=Math.min(amount,stack.getCount());
        ItemStack result=stack.copyWithCount(n);
        if(!simulate){
            if(player.level().isClientSide())return ItemStack.EMPTY;
            var remaining=stack.copy();remaining.shrink(n);
            items.set(pos.slot,remaining.isEmpty()?ItemStack.EMPTY:remaining);
            FieldPack.replaceContents(pos.storage.carrier,items);
            player.getInventory().setChanged();
        }
        return result;
    }
    @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){
        return slot>=0&&slot<nativeSize?nativeHandler.insertItem(slot,stack,simulate):stack;
    }
    @Override public int getSlotLimit(int slot){return slot>=0&&slot<nativeSize?nativeHandler.getSlotLimit(slot):64;}
    @Override public boolean isItemValid(int slot,ItemStack stack){return slot>=0&&slot<nativeSize&&nativeHandler.isItemValid(slot,stack);}
}
