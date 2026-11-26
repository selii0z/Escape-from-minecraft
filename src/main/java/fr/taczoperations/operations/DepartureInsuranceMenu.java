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
    public static final int WIDTH=760,HEIGHT=420,SELECT_ALL=50,CONFIRM=51,SKIP=52,BACK=53,LAUNCH=54,EDIT=55;
    public static final int AVAILABLE=1,SELECTED=2,INSURED=3,FEE=RaidSecurity.FEE;
    private record Entry(Supplier<ItemStack> source,ItemStack snapshot){}
    private final Player owner;
    private final Level level;
    private final BlockPos board;
    private final boolean night,duo,industrial;
    private final fr.taczoperations.worldgen.ExpressZone express;
    private final int balance;
    private final String partnerName;
    private final long expiresAt;
    private static final long MAX_WAIT_TICKS=20L*180;
    private final List<Entry> entries;
    private final DataSlot[] states;
    private boolean submitted;
    private final DataSlot readiness=DataSlot.standalone();
    private DuoDeparture session;
    private static final class DuoDeparture {
        final ServerPlayer leader,partner;
        final BlockPos board;
        final boolean night,industrial;
        final fr.taczoperations.worldgen.ExpressZone express;
        DepartureInsuranceMenu first,second;
        boolean closed;
        DuoDeparture(ServerPlayer leader,ServerPlayer partner,BlockPos board,boolean night,boolean industrial,fr.taczoperations.worldgen.ExpressZone express){
            this.leader=leader;this.partner=partner;this.board=board;this.night=night;this.industrial=industrial;this.express=express;
        }
        boolean valid(){
            return !closed&&first!=null&&second!=null
                &&leader.server.getPlayerList().getPlayer(leader.getUUID())==leader
                &&partner.server.getPlayerList().getPlayer(partner.getUUID())==partner
                &&leader.containerMenu==first&&partner.containerMenu==second
                &&first.stillValid(leader)&&second.stillValid(partner)
                &&leader.getUUID().equals(OperationsGroups.of(partner).partner(partner))
                &&partner.getUUID().equals(OperationsGroups.of(leader).partner(leader));
        }
        void cancel(String message){
            if(closed)return;
            closed=true;
            leader.sendSystemMessage(Component.literal(message));
            partner.sendSystemMessage(Component.literal(message));
            if(leader.containerMenu==first)leader.closeContainer();
            if(partner.containerMenu==second)partner.closeContainer();
        }
        void tryLaunch(){
            if(!valid()){cancel("Départ duo annulé : équipe ou tableau indisponible. Assurance conservée.");return;}
            if(!first.submitted||!second.submitted)return;
            String problem=ForestExpedition.departureProblem(leader,board,true,industrial,express);
            var a=first.resolveSelection(leader);var b=second.resolveSelection(partner);
            if(!problem.isEmpty()||a==null||b==null){
                cancel(problem.isEmpty()?"Équipement ou crédits modifiés : départ annulé, aucun nouveau paiement.":problem);return;
            }
            if(!RaidSecurity.insureSelection(leader,a)||!RaidSecurity.insureSelection(partner,b)){
                cancel("Assurance indisponible : départ annulé. Les assurances déjà validées restent conservées.");return;
            }
            closed=true;
            String notice=ForestExpedition.depart(leader,board,night,true,industrial,express);
            if(!notice.isEmpty()){
                leader.sendSystemMessage(Component.literal(notice+" Assurance conservée."));
                partner.sendSystemMessage(Component.literal(notice+" Assurance conservée."));
            }
            if(leader.containerMenu==first)leader.closeContainer();
            if(partner.containerMenu==second)partner.closeContainer();
        }
    }

    public static void open(ServerPlayer p,BlockPos board,boolean night,boolean duo,boolean industrial){open(p,board,night,duo,industrial,null);}
    public static void open(ServerPlayer p,BlockPos board,boolean night,boolean duo,boolean industrial,fr.taczoperations.worldgen.ExpressZone express){
        if(!p.isAlive()||p.isSpectator()||p.level().dimension().equals(ForestExpedition.FOREST))return;
        String problem=ForestExpedition.departureProblem(p,board,duo,industrial,express);
        if(!problem.isEmpty()){OperationsMenu.open(p,board,OperationsMenu.FOREST,0,problem);return;}
        if(duo){
            var partnerId=OperationsGroups.of(p).partner(p);
            var partner=partnerId==null?null:p.server.getPlayerList().getPlayer(partnerId);
            if(partner==null||!partner.isAlive()||partner.isSpectator()||partner.level()!=p.level()
                ||board.distToCenterSqr(partner.position())>64
                ||partner.containerMenu!=partner.inventoryMenu&&!(partner.containerMenu instanceof OperationsMenu)){
                OperationsMenu.open(p,board,OperationsMenu.FOREST,0,"Ton équipier doit être disponible près du tableau.");return;
            }
            var session=new DuoDeparture(p,partner,board.immutable(),night,industrial,express);
            openOne(p,board,night,true,industrial,express,session);
            openOne(partner,board,night,true,industrial,express,session);
            if(!session.valid())session.cancel("Départ duo annulé : impossible d'ouvrir les deux assurances.");
            return;
        }
        openOne(p,board,night,false,industrial,express,null);
    }
    private static void openOne(ServerPlayer p,BlockPos board,boolean night,boolean duo,boolean industrial,fr.taczoperations.worldgen.ExpressZone express,DuoDeparture session){
        var entries=new ArrayList<Entry>();
        var unique=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ItemStack,Boolean>());
        for(int i=0;i<p.getInventory().getContainerSize();i++){
            final int slot=i;add(entries,unique,()->p.getInventory().getItem(slot));
        }
        for(String key:new String[]{FieldInventoryMenu.GOGGLES,FieldInventoryMenu.BAG,FieldInventoryMenu.RIG})
            add(entries,unique,()->FieldInventoryMenu.current(FieldInventoryMenu.handler(p,key)));
        int balance=SupplyContract.account(p).getInt("credits");
        String partnerName=session==null?"":(p==session.leader?session.partner:session.leader).getGameProfile().getName();
        p.openMenu(new SimpleMenuProvider((id,inv,viewer)->{
            var menu=new DepartureInsuranceMenu(id,inv,board,night,duo,industrial,express,balance,entries,entries.size(),partnerName);
            menu.session=session;
            if(session!=null){if(p==session.leader)session.first=menu;else session.second=menu;}
            return menu;
        },
            Component.literal("Assurance avant raid")),buf->{
                buf.writeVarInt(entries.size());buf.writeVarInt(balance);
                buf.writeBoolean(night);buf.writeBoolean(duo);buf.writeBoolean(industrial);buf.writeVarInt(express==null?0:express.id);buf.writeUtf(partnerName);
            });
    }
    private static void add(List<Entry> entries,java.util.Set<ItemStack> unique,Supplier<ItemStack> source){
        ItemStack s=source.get();
        if(RaidSecurity.eligible(s)&&unique.add(s)&&entries.size()<45)entries.add(new Entry(source,s.copy()));
    }
    public DepartureInsuranceMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){
        this(id,inv,buf.readVarInt(),buf.readVarInt(),buf.readBoolean(),buf.readBoolean(),buf.readBoolean(),fr.taczoperations.worldgen.ExpressZone.byId(buf.readVarInt()),buf.readUtf());
    }
    private DepartureInsuranceMenu(int id,Inventory inv,int count,int balance,boolean night,boolean duo,boolean industrial,fr.taczoperations.worldgen.ExpressZone express,String partnerName){
        this(id,inv,BlockPos.ZERO,night,duo,industrial,express,balance,List.of(),Math.clamp(count,0,45),partnerName);
    }
    private DepartureInsuranceMenu(int id,Inventory inv,BlockPos board,boolean night,boolean duo,boolean industrial,fr.taczoperations.worldgen.ExpressZone express,int balance,List<Entry> entries,int count,String partnerName){
        super(OperationsMod.DEPARTURE_INSURANCE.get(),id);
        owner=inv.player;level=owner.level();expiresAt=level.getGameTime()+MAX_WAIT_TICKS;this.board=board.immutable();this.night=night;this.duo=duo;this.industrial=industrial;this.express=express;this.balance=balance;this.entries=List.copyOf(entries);this.partnerName=partnerName;
        addDataSlot(readiness);
        states=new DataSlot[count];
        var preview=new SimpleContainer(count);
        for(int i=0;i<count;i++){
            final int index=i;
            if(owner instanceof ServerPlayer p){
                preview.setItem(i,entries.get(i).snapshot().copy());
            }
            addSlot(new Slot(preview,i,-10000,-10000){
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
    public boolean waiting(){return readiness.get()==1;}
    public boolean reviewing(){return readiness.get()!=0;}
    public String partnerName(){return partnerName;}
    public int insured(){int n=0;for(var s:states)if(s.get()==INSURED)n++;return n;}
    public boolean industrial(){return industrial;}
    public fr.taczoperations.worldgen.ExpressZone express(){return express;}
    public boolean night(){return night;}
    public boolean duo(){return duo;}
    public int selected(){int n=0;for(var s:states)if(s.get()==SELECTED)n++;return n;}
    @Override public boolean stillValid(Player p){
        if(p!=owner||!p.isAlive()||p.isSpectator())return false;
        return !(p instanceof ServerPlayer)||(p.level()==level&&level.getGameTime()<expiresAt&&!p.isPassenger()&&!p.isSleeping()&&!level.dimension().equals(ForestExpedition.FOREST)
            &&level.isLoaded(board)&&level.getBlockState(board).is(OperationsMod.BOARD.get())&&board.distToCenterSqr(p.position())<=64);
    }
    @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
    @Override public void clicked(int index,int button,ClickType type,Player p){} // No inventory movement in this screen.
    private ArrayList<ItemStack> resolveSelection(ServerPlayer p){
        var chosen=new ArrayList<ItemStack>();
        if(!RaidSecurity.available(p)||p.level().dimension().equals(ForestExpedition.FOREST))return null;
        var unique=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ItemStack,Boolean>());
        for(int i=0;i<states.length;i++)if(state(i)==SELECTED){
            var entry=entries.get(i);ItemStack current=entry.source().get();
            if(!ItemStack.matches(current,entry.snapshot())||!RaidSecurity.eligible(current)||RaidSecurity.insuredBy(p,current)||!unique.add(current))return null;
            chosen.add(current);
        }
        return SupplyContract.balance(p)>=chosen.size()*FEE?chosen:null;
    }
    @Override public boolean clickMenuButton(Player player,int button){
        if(!(player instanceof ServerPlayer p)||p.containerMenu!=this||!stillValid(p))return false;
        if(session!=null&&!session.valid()){session.cancel("Départ duo annulé.");return true;}
        if(button==BACK){
            if(session!=null)session.cancel("Départ duo annulé.");
            else OperationsMenu.open(p,board,OperationsMenu.FOREST,0,"Départ annulé.");
            return true;
        }
        if(submitted)return false;
        if(button==EDIT&&reviewing()){readiness.set(0);broadcastChanges();return true;}
        if(!reviewing()){
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
            if(button!=CONFIRM&&button!=SKIP)return false;
            if(button==SKIP)for(var state:states)if(state.get()==SELECTED)state.set(AVAILABLE);
            if(resolveSelection(p)==null){p.sendSystemMessage(Component.literal("Assurance : crédits insuffisants ou équipement modifié."));return true;}
            readiness.set(2);broadcastChanges();return true;
        }
        if(button!=LAUNCH)return false;
        String problem=ForestExpedition.departureProblem(p,board,duo,industrial,express);
        if(!problem.isEmpty()){
            if(session!=null)session.cancel(problem);
            else OperationsMenu.open(p,board,OperationsMenu.FOREST,0,problem);
            return true;
        }
        var chosen=resolveSelection(p);
        if(chosen==null){p.sendSystemMessage(Component.literal("Équipement ou crédits modifiés : annule et recommence la préparation."));return true;}
        if(session!=null){
            submitted=true;readiness.set(1);broadcastChanges();
            session.tryLaunch();return true;
        }
        if(!RaidSecurity.insureSelection(p,chosen)){
            p.sendSystemMessage(Component.literal("Assurance : crédits insuffisants ou équipement modifié."));return true;
        }
        submitted=true;
        String notice=ForestExpedition.depart(p,board,night,false,industrial,express);
        if(!notice.isEmpty()&&stillValid(p))OperationsMenu.open(p,board,OperationsMenu.FOREST,0,notice+(chosen.isEmpty()?"":" Assurance conservée."));
        return true;
    }
    @Override public void removed(Player player){
        super.removed(player);
        if(player instanceof ServerPlayer&&session!=null&&!session.closed){
            // Mark the session closed before closing the other screen, avoiding recursive cancellation.
            session.closed=true;
            ServerPlayer other=player==session.leader?session.partner:session.leader;
            DepartureInsuranceMenu otherMenu=player==session.leader?session.second:session.first;
            other.sendSystemMessage(Component.literal("Ton équipier a fermé l'assurance. Départ annulé, assurance conservée."));
            if(otherMenu!=null&&other.containerMenu==otherMenu)other.closeContainer();
        }
    }
}
