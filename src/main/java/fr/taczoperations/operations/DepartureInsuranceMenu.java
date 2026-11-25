package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Read-only equipment snapshots; selection and payment are server-authoritative. */
public final class DepartureInsuranceMenu extends AbstractContainerMenu {
    public static final int WIDTH=254,HEIGHT=222,SELECT_ALL=50,CONFIRM=51,SKIP=52,BACK=53;
    public static final int AVAILABLE=1,SELECTED=2,INSURED=3,FEE=RaidSecurity.FEE;
    private record Entry(Supplier<ItemStack> source,ItemStack snapshot){}
    private final Player owner;
    private final Level level;
    private final BlockPos board;
    private final boolean night,duo,industrial;
    private final int balance;
    private final List<Entry> entries;
    private final DataSlot[] states;
    private boolean submitted;

    public static void open(ServerPlayer p,BlockPos board,boolean night,boolean duo,boolean industrial){
        if(!p.isAlive()||p.isSpectator()||p.level().dimension().equals(ForestExpedition.FOREST))return;
        var entries=new ArrayList<Entry>();
        var unique=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ItemStack,Boolean>());
        for(int i=0;i<p.getInventory().getContainerSize();i++){
            final int slot=i;add(entries,unique,()->p.getInventory().getItem(slot));
        }
        for(String key:new String[]{FieldInventoryMenu.GOGGLES,FieldInventoryMenu.BAG,FieldInventoryMenu.RIG})
            add(entries,unique,()->FieldInventoryMenu.current(FieldInventoryMenu.handler(p,key)));
        int balance=SupplyContract.account(p).getInt("credits");
        p.openMenu(new SimpleMenuProvider((id,inv,viewer)->new DepartureInsuranceMenu(id,inv,board,night,duo,industrial,balance,entries,entries.size()),
            Component.literal("Assurance avant raid")),buf->{
                buf.writeVarInt(entries.size());buf.writeVarInt(balance);
                buf.writeBoolean(night);buf.writeBoolean(duo);buf.writeBoolean(industrial);
            });
    }
    private static void add(List<Entry> entries,java.util.Set<ItemStack> unique,Supplier<ItemStack> source){
        ItemStack s=source.get();
        if(RaidSecurity.eligible(s)&&unique.add(s)&&entries.size()<45)entries.add(new Entry(source,s.copy()));
    }
    public DepartureInsuranceMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){
        this(id,inv,buf.readVarInt(),buf.readVarInt(),buf.readBoolean(),buf.readBoolean(),buf.readBoolean());
    }
    private DepartureInsuranceMenu(int id,Inventory inv,int count,int balance,boolean night,boolean duo,boolean industrial){
        this(id,inv,BlockPos.ZERO,night,duo,industrial,balance,List.of(),Math.clamp(count,0,45));
    }
    private DepartureInsuranceMenu(int id,Inventory inv,BlockPos board,boolean night,boolean duo,boolean industrial,int balance,List<Entry> entries,int count){
        super(OperationsMod.DEPARTURE_INSURANCE.get(),id);
        owner=inv.player;level=owner.level();this.board=board.immutable();this.night=night;this.duo=duo;this.industrial=industrial;this.balance=balance;this.entries=List.copyOf(entries);
        states=new DataSlot[count];
        var preview=new SimpleContainer(count);
        for(int i=0;i<count;i++){
            final int index=i;
            if(owner instanceof ServerPlayer p){
                preview.setItem(i,entries.get(i).snapshot().copy());
            }
            addSlot(new Slot(preview,i,11+(i%9)*26,53+(i/9)*23){
                @Override public boolean mayPlace(ItemStack s){return false;}
                @Override public boolean mayPickup(Player p){return false;}
            });
            states[index]=DataSlot.standalone();
            if(owner instanceof ServerPlayer p)states[index].set(RaidSecurity.insuredBy(p,entries.get(index).source().get())?INSURED:AVAILABLE);
            addDataSlot(states[index]);
        }
    }
    public int state(int i){return states[i].get();}
    public int balance(){return balance;}
    public boolean industrial(){return industrial;}
    public boolean night(){return night;}
    public boolean duo(){return duo;}
    public int selected(){int n=0;for(var s:states)if(s.get()==SELECTED)n++;return n;}
    @Override public boolean stillValid(Player p){
        if(p!=owner||!p.isAlive()||p.isSpectator())return false;
        return !(p instanceof ServerPlayer)||(p.level()==level&&!level.dimension().equals(ForestExpedition.FOREST)
            &&level.isLoaded(board)&&level.getBlockState(board).is(OperationsMod.BOARD.get())&&board.distToCenterSqr(p.position())<=64);
    }
    @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
    @Override public void clicked(int index,int button,ClickType type,Player p){} // No inventory movement in this screen.
    @Override public boolean clickMenuButton(Player player,int button){
        if(!(player instanceof ServerPlayer p)||p.containerMenu!=this||!stillValid(p)||submitted)return false;
        if(button>=0&&button<states.length){
            int state=state(button);
            if(state==AVAILABLE||state==SELECTED)states[button].set(state==AVAILABLE?SELECTED:AVAILABLE);
            broadcastChanges();return true;
        }
        if(button==SELECT_ALL){
            boolean all=selected()>0;
            for(var state:states)if(state.get()==AVAILABLE)all=false;
            for(var state:states)if(state.get()==AVAILABLE||state.get()==SELECTED)state.set(all?AVAILABLE:SELECTED);
            broadcastChanges();return true;
        }
        if(button==BACK){OperationsMenu.open(p,board,OperationsMenu.FOREST,0,"");return true;}
        if(button!=CONFIRM&&button!=SKIP)return false;
        var selected=new ArrayList<ItemStack>();
        if(button==CONFIRM){
            for(int i=0;i<states.length;i++)if(state(i)==SELECTED){
                var entry=entries.get(i);ItemStack current=entry.source().get();
                if(!ItemStack.matches(current,entry.snapshot())||!RaidSecurity.eligible(current)||RaidSecurity.insuredBy(p,current)){
                    OperationsMenu.open(p,board,OperationsMenu.FOREST,0,"Équipement modifié : ouvre de nouveau l'assurance.");return true;
                }
                selected.add(current);
            }
            if(!RaidSecurity.insureSelection(p,selected)){
                p.sendSystemMessage(Component.literal("Assurance : crédits insuffisants ou équipement modifié."));return true;
            }
        }
        submitted=true;
        if(!selected.isEmpty())p.sendSystemMessage(Component.literal("Assurance : "+selected.size()+" objet(s) · -"+(selected.size()*FEE)+" crédits."));
        String notice=ForestExpedition.depart(p,board,night,duo,industrial);
        if(!notice.isEmpty()&&stillValid(p))OperationsMenu.open(p,board,OperationsMenu.FOREST,0,notice+(selected.isEmpty()?"":" Assurance conservée."));
        return true;
    }
}
