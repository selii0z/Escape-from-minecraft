package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.campaign.CampaignTrader;
import fr.taczoperations.campaign.CampaignGraph.State;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.ArrayList;
import java.util.List;

/** Server-owned snapshot for the custom quest screen. No inventory slots are exposed. */
public final class CampaignMenu extends AbstractContainerMenu {
    public static final int TRADERS=CampaignTrader.values().length, INBOX=TRADERS, FIRST=TRADERS+1;
    public static final int COLLECT=100, LEGACY=101, BACK=102, SHOP=103, REFRESH=104;
    public static final int ACCEPT=1000, CLAIM=2000, FOLLOW=3000;
    static final String TRACKED="campaign_followed_quest";
    private final Player owner;
    private final int trader,selected,count;
    private final List<CampaignRuntime.QuestView> serverQuests;

    public CampaignMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){
        this(id,inv,buf.readVarInt(),buf.readVarInt(),buf.readVarInt());
    }
    private CampaignMenu(int id,Inventory inv,int trader,int selected,int count){
        this(id,inv,trader,selected,count,new SimpleContainer(FIRST+count),List.of());
    }
    private CampaignMenu(int id,Inventory inv,int trader,int selected,int count,SimpleContainer data,List<CampaignRuntime.QuestView> quests){
        super(OperationsMod.CAMPAIGN_MENU.get(),id);
        this.owner=inv.player;this.trader=trader;this.selected=selected;this.count=count;this.serverQuests=List.copyOf(quests);
        for(int i=0;i<data.getContainerSize();i++)addSlot(new Slot(data,i,-10000,-10000){
            @Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean mayPickup(Player player){return false;}
        });
    }
    public int trader(){return trader;}
    public int selected(){return selected;}
    public int count(){return count;}
    public ItemStack traderStack(int index){return slots.get(index).getItem();}
    public ItemStack questStack(int index){return slots.get(FIRST+index).getItem();}
    public ItemStack inboxStack(){return slots.get(INBOX).getItem();}
    static void open(ServerPlayer p){open(p,CampaignTrader.PRAPOR,0);}
    private static void open(ServerPlayer p,CampaignTrader trader,int requested){
        if(!ProgressionMenus.available(p)||p.containerMenu!=p.inventoryMenu||!p.containerMenu.getCarried().isEmpty())return;
        var live=CampaignRuntime.views(p);
        var all=CampaignPreview.merge(live);
        var liveIds=live.stream().map(CampaignRuntime.QuestView::id).collect(java.util.stream.Collectors.toSet());
        var list=all.stream().filter(q->q.trader().equals(trader.id)).toList();
        var c=new SimpleContainer(FIRST+list.size());
        for(var t:CampaignTrader.values()){
            var quests=all.stream().filter(q->q.trader().equals(t.id)).toList();
            long done=quests.stream().filter(q->q.state()==State.COMPLETED).count();
            long playable=quests.stream().filter(q->liveIds.contains(q.id())).count();
            c.setItem(t.ordinal(),tile(icon(t),t.title,List.of(
                CampaignTraderAccount.unlocked(p,t)?"Fidélité "+CampaignTraderAccount.loyalty(p,t):"Verrouillé",
                done+"/"+playable+" terminées",playable+"/"+quests.size()+" jouables")));
        }
        c.setItem(INBOX,tile(Items.CHEST,"Réserve",List.of(CampaignRewardInbox.pending(p)+" objet(s) en réserve")));
        String tracked=SupplyContract.account(p).getString(TRACKED);
        for(int i=0;i<list.size();i++){
            var q=list.get(i);boolean playable=liveIds.contains(q.id());
            String status=q.claimable()?"À valider":CampaignRuntime.stateLabel(q.state());
            if(!playable)status="Non jouable";
            var lines=new ArrayList<String>();
            lines.add(status);
            lines.add(playable?"1":"0");
            lines.add(Integer.toString(playable?CampaignRuntime.objectiveCount(q.id()):0));
            lines.add(q.id().equals(tracked)?"1":"0");
            lines.add(playable?CampaignRuntime.questArt(q.id()):"military");
            lines.addAll(q.lines());
            if(playable)lines.addAll(CampaignRuntime.requirementLines(q.id()));
            Item item=switch(q.state()){
                case COMPLETED->Items.LIME_DYE;case READY->Items.EMERALD;case ACTIVE->Items.PAPER;
                case AVAILABLE->Items.WRITABLE_BOOK;case FAILED->Items.BARRIER;default->Items.GRAY_DYE;
            };
            var stack=tile(q.claimable()?Items.EMERALD:item,q.name(),lines);
            if(playable){
                var rewards=CampaignRuntime.rewardIcons(q.id());
                if(!rewards.isEmpty())stack.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(rewards));
            }
            c.setItem(FIRST+i,stack);
        }
        int selection=Math.max(0,Math.min(requested,list.size()-1));
        p.openMenu(new SimpleMenuProvider((id,inv,ignored)->new CampaignMenu(id,inv,trader.ordinal(),selection,list.size(),c,list),
            Component.literal("EFM · "+trader.title)),buf->{buf.writeVarInt(trader.ordinal());buf.writeVarInt(selection);buf.writeVarInt(list.size());});
    }
    @Override public boolean stillValid(Player p){return p==owner&&p.isAlive()&&!p.isSpectator()&&(!(p instanceof ServerPlayer server)||ProgressionMenus.available(server));}
    @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
    @Override public void clicked(int slot,int button,ClickType type,Player p){}
    @Override public boolean clickMenuButton(Player player,int action){
        if(!(player instanceof ServerPlayer p)||p.containerMenu!=this||!stillValid(p)||!getCarried().isEmpty())return false;
        var t=CampaignTrader.values()[trader];
        if(action>=0&&action<TRADERS){p.closeContainer();open(p,CampaignTrader.values()[action],0);return true;}
        if(action==BACK){p.closeContainer();if(Hideout.ownsPlot(p))HideoutMenu.open(p);return true;}
        if(action==LEGACY){p.closeContainer();ProgressionMenus.missions(p);return true;}
        if(action==SHOP){p.closeContainer();CampaignUnlockShop.open(p,t,0,()->open(p,t,selected));return true;}
        if(action==COLLECT){p.closeContainer();int moved=CampaignRewardInbox.collect(p);p.sendSystemMessage(Component.literal(moved+" objet(s) récupéré(s) · "+CampaignRewardInbox.pending(p)+" en réserve"));open(p,t,selected);return true;}
        if(action==REFRESH){p.closeContainer();open(p,t,selected);return true;}
        int kind=action/1000,index=action%1000;
        if(kind<1||kind>3||index<0||index>=serverQuests.size())return false;
        var q=serverQuests.get(index);
        if(kind==1&&q.state()!=State.AVAILABLE||kind==2&&!q.claimable())return false;
        if(kind==3&&q.state()!=State.ACTIVE&&q.state()!=State.READY)return false;
        p.closeContainer();
        if(kind==1)CampaignRuntime.acceptNamed(p,q.id());
        else if(kind==2)CampaignRuntime.claimNamed(p,q.id());
        else{
            var account=SupplyContract.account(p);
            if(account.getString(TRACKED).equals(q.id()))account.remove(TRACKED);
            else account.putString(TRACKED,q.id());
        }
        open(p,t,index);return true;
    }
    private static Item icon(CampaignTrader t){return switch(t){
        case PRAPOR->Items.CROSSBOW;case THERAPIST->Items.GOLDEN_APPLE;case SKIER->Items.IRON_NUGGET;
        case PEACEKEEPER->Items.GOLD_INGOT;case MECHANIC->Items.ANVIL;case RAGMAN->Items.LEATHER_CHESTPLATE;
        case JAEGER->Items.COMPASS;case FENCE->Items.ENDER_CHEST;case REF->Items.TARGET;
        case LIGHTKEEPER->Items.LANTERN;case BTR_DRIVER->Items.MINECART;
    };}
    private static ItemStack tile(Item item,String name,List<String> lines){
        var stack=new ItemStack(item);stack.set(DataComponents.CUSTOM_NAME,Component.literal(name));
        stack.set(DataComponents.LORE,new ItemLore(lines.stream().map(line->(Component)Component.literal(line)).toList()));
        return stack;
    }
}
