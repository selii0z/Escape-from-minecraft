package fr.taczoperations.operations;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

public final class KeyRingItem extends Item {
    public KeyRingItem(Properties props){super(props.stacksTo(1).component(DataComponents.CONTAINER,ItemContainerContents.EMPTY));}
    @Override public boolean canFitInsideContainerItems(){return false;}
    static boolean key(ItemStack s){return java.util.Set.of("taczoperations:industrial_key","taczoperations:forest_key").contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString());}
    static NonNullList<ItemStack> contents(ItemStack ring){
        var items=NonNullList.withSize(16,ItemStack.EMPTY);
        ring.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyInto(items);
        for(int i=0;i<16;i++)items.set(i,items.get(i).copy());
        return items;
    }
    static boolean has(ServerPlayer p,java.util.function.Predicate<ItemStack> matches){
        for(var stack:p.getInventory().items){
            if(matches.test(stack))return true;
            if(stack.getItem() instanceof KeyRingItem&&contents(stack).stream().anyMatch(matches))return true;
        }
        return CarriedStorage.of(p).count(matches)>0||RaidSecurity.secureKey(p,matches,false);
    }
    static boolean consume(ServerPlayer p,java.util.function.Predicate<ItemStack> matches){
        for(var ring:p.getInventory().items){
            if(!(ring.getItem() instanceof KeyRingItem))continue;
            var items=contents(ring);
            for(var item:items)if(matches.test(item)){
                item.shrink(1);ring.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(items));
                p.getInventory().setChanged();return true;
            }
        }
        return CarriedStorage.of(p).consume(matches,1)||RaidSecurity.secureKey(p,matches,true);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player p,InteractionHand hand){
        ItemStack ring=p.getItemInHand(hand);
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResultHolder.pass(ring);
        if(p instanceof ServerPlayer server&&p.containerMenu==p.inventoryMenu){
            int held=p.getInventory().selected;
            var store=new RingStorage(server,ring,held);
            server.openMenu(new SimpleMenuProvider((id,inv,viewer)->new ChestMenu(MenuType.GENERIC_9x2,id,inv,store,2){
                {
                    for(int i=0;i<18;i++){
                        final int index=i;Slot old=slots.get(i);
                        Slot filtered=new Slot(store,i,old.x,old.y){
                            @Override public boolean mayPlace(ItemStack s){return index<16&&key(s);}
                            @Override public boolean mayPickup(Player who){return index<16&&store.stillValid(who);}
                        };filtered.index=i;slots.set(i,filtered);
                    }
                }
                @Override public boolean stillValid(Player p){return store.stillValid(p);}
                @Override public void clicked(int index,int button,ClickType type,Player p){
                    if(!stillValid(p)||type==ClickType.QUICK_CRAFT||type==ClickType.PICKUP_ALL||type==ClickType.CLONE)return;
                    if(type==ClickType.SWAP&&button==held)return;
                    if(index>=0&&index<slots.size()&&slots.get(index).container==inv&&slots.get(index).getContainerSlot()==held)return;
                    super.clicked(index,button,type,p);
                }
                @Override public ItemStack quickMoveStack(Player p,int index){
                    if(!stillValid(p)||index<0||index>=slots.size())return ItemStack.EMPTY;
                    if(slots.get(index).container==inv&&slots.get(index).getContainerSlot()==held)return ItemStack.EMPTY;
                    return super.quickMoveStack(p,index);
                }
            },Component.literal("Trousseau · 16 emplacements")));
        }
        return InteractionResultHolder.sidedSuccess(ring,level.isClientSide());
    }
    private static final class RingStorage extends SimpleContainer{
        private final ServerPlayer owner;private final ItemStack ring;private final int held;
        private boolean ready;private ItemContainerContents expected;
        RingStorage(ServerPlayer p,ItemStack ring,int held){super(18);this.owner=p;this.ring=ring;this.held=held;
            var items=contents(ring);for(int i=0;i<16;i++)super.setItem(i,items.get(i));
            expected=ring.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY);ready=true;}
        @Override public boolean stillValid(Player p){return p==owner&&owner.isAlive()&&owner.getInventory().getItem(held)==ring
            &&java.util.Objects.equals(expected,ring.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY));}
        @Override public void setChanged(){super.setChanged();if(!ready||!stillValid(owner))return;
            var items=NonNullList.withSize(16,ItemStack.EMPTY);for(int i=0;i<16;i++)items.set(i,getItem(i).copy());
            expected=ItemContainerContents.fromItems(items);ring.set(DataComponents.CONTAINER,expected);owner.getInventory().setChanged();}
    }
}
