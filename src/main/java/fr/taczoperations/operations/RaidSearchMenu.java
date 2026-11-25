package fr.taczoperations.operations;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;

/**
 * Server-side view of an existing raid inventory. Unsearched items are
 * never sent to the client and cannot be taken. The inventory and search
 * cursor belong to the loot source, not to a particular viewer.
 */
public final class RaidSearchMenu extends ChestMenu {
    private static final String REVEALED="efm_search_revealed", NEXT="efm_search_next", LAST_ACTIVE="efm_search_last_active";
    private static final int ITEM_TICKS=10; // half a second per occupied slot at 20 TPS
    private int lastShown=-1;
    private final SearchView view;
    private final Player viewer;
    public RaidSearchMenu(int id,Inventory inventory,Container backing,CompoundTag state,Predicate<Player> valid){
        this(id,inventory,new SearchView(backing,state,valid));
    }
    private RaidSearchMenu(int id,Inventory inventory,SearchView view){
        super(MenuType.GENERIC_9x3,id,inventory,view,3);
        this.view=view;
        this.viewer=inventory.player;
        if(viewer instanceof ServerPlayer p){
            view.skipEmpty();
            if(view.revealed()<27)view.resume(p.level().getGameTime());
            showProgress(p);
        }
    }
    @Override public void broadcastChanges(){
        if(view!=null&&viewer instanceof ServerPlayer p&&p.containerMenu==this&&view.stillValid(p)){
            view.skipEmpty();
            long now=p.level().getGameTime();
            if(view.revealed()<27){
                view.resume(now);
                if(now>=view.state.getLong(NEXT)){
                    view.state.putInt(REVEALED,view.revealed()+1);
                    view.skipEmpty();
                    view.state.putLong(NEXT,now+ITEM_TICKS);
                    view.backing.setChanged();
                }
            }
            showProgress(p);
        }
        super.broadcastChanges();
    }
    private void showProgress(ServerPlayer player){
        int current=view.revealed();
        if(current==lastShown)return;
        lastShown=current;
        player.displayClientMessage(Component.literal(current==27?"Fouille terminée":"Fouille en cours… "+current+"/27"),true);
    }
    @Override protected Slot addSlot(Slot slot){
        if(slot.container instanceof SearchView search)return super.addSlot(new Slot(search,slot.getContainerSlot(),slot.x,slot.y){
            @Override public boolean mayPlace(ItemStack item){return false;}
            @Override public boolean mayPickup(Player player){return search.visible(getContainerSlot())&&search.stillValid(player);}
        });
        return super.addSlot(slot);
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=27||!view.visible(index)||!view.stillValid(player))return ItemStack.EMPTY;
        return super.quickMoveStack(player,index);
    }
    private static final class SearchView implements Container{
        final Container backing;
        final CompoundTag state;
        final Predicate<Player> valid;
        SearchView(Container backing,CompoundTag state,Predicate<Player> valid){
            this.backing=backing;this.state=state;this.valid=valid;
        }
        int revealed(){return Math.clamp(state.getInt(REVEALED),0,27);}
        boolean visible(int i){return i>=0&&i<revealed();}
        // Empty slots cost no search time. Only the next occupied slot can
        // stop the shared cursor. Never expose a hidden non-empty stack.
        void skipEmpty(){
            int from=revealed(),to=from;
            while(to<27&&backing.getItem(to).isEmpty())to++;
            if(to!=from){state.putInt(REVEALED,to);backing.setChanged();}
        }
        void resume(long now){
            long last=state.getLong(LAST_ACTIVE);
            // Closing all viewers pauses the search. Reopening preserves
            // discovered items, but cannot fast-forward the next item.
            if(!state.contains(NEXT)||!state.contains(LAST_ACTIVE)||now<last||now-last>1){
                state.putLong(NEXT,now+ITEM_TICKS);
                backing.setChanged();
            }
            state.putLong(LAST_ACTIVE,now);
        }
        @Override public int getContainerSize(){return 27;}
        @Override public boolean isEmpty(){for(int i=0;i<revealed();i++)if(!getItem(i).isEmpty())return false;return true;}
        @Override public ItemStack getItem(int i){return visible(i)?backing.getItem(i):ItemStack.EMPTY;}
        @Override public ItemStack removeItem(int i,int count){return visible(i)?backing.removeItem(i,count):ItemStack.EMPTY;}
        @Override public ItemStack removeItemNoUpdate(int i){return visible(i)?backing.removeItemNoUpdate(i):ItemStack.EMPTY;}
        @Override public void setItem(int i,ItemStack stack){if(visible(i)&&stack.isEmpty())backing.setItem(i,stack);}
        @Override public void setChanged(){backing.setChanged();}
        @Override public boolean stillValid(Player player){return valid.test(player)&&backing.stillValid(player);}
        @Override public boolean canPlaceItem(int i,ItemStack stack){return false;}
        @Override public void clearContent(){} // a menu view must never erase the backing inventory
    }
}
