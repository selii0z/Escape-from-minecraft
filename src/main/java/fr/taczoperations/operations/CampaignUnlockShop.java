package fr.taczoperations.operations;

import com.google.gson.JsonParser;
import fr.taczoperations.campaign.CampaignTrader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Quest-gated supplies. The server rechecks ownership, completed quest and loyalty on purchase. */
final class CampaignUnlockShop {
    private static final System.Logger LOG=System.getLogger("taczoperations.campaign.offers");
    private record Offer(String id,String quest,String questName,CampaignTrader trader,String item,int loyalty,int count,int price){}
    private static final List<Offer> OFFERS=load();
    private static List<Offer> load(){
        try(var stream=CampaignUnlockShop.class.getResourceAsStream("/data/taczoperations/campaign/campaign-unlock-offers.json")){
            if(stream==null)return List.of();
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Invalid offer catalogue");
            var result=new ArrayList<Offer>();var ids=new HashSet<String>();
            for(var entry:root.getAsJsonArray("offers")){
                var q=entry.getAsJsonObject();
                if(!q.get("supported").getAsBoolean())continue;
                var o=new Offer(q.get("id").getAsString(),q.get("quest").getAsString(),q.get("questName").getAsString(),
                    CampaignTrader.byId(q.get("trader").getAsString()),q.get("item").getAsString(),q.get("loyalty").getAsInt(),
                    q.get("count").getAsInt(),q.get("price").getAsInt());
                if(!ids.add(o.id())||!o.id().matches("[0-9a-f]{24}")||!o.quest().matches("[0-9a-f]{24}")||
                    !CampaignSupplies.rewardKnown(o.item())||o.count()<1||o.count()>64||o.price()<1||o.loyalty()<1||o.loyalty()>4)
                    throw new IllegalArgumentException("Invalid quest offer");
                result.add(o);
            }
            return List.copyOf(result);
        }catch(Exception e){LOG.log(System.Logger.Level.ERROR,"Quest offer catalogue rejected",e);return List.of();}
    }
    private static boolean accessible(ServerPlayer p){
        return ProgressionMenus.available(p)&&PlayerMarket.available(p)&&!ForestExpedition.hasReturnPoint(p)&&!p.isCreative()&&!p.isSpectator();
    }
    private static boolean unlocked(ServerPlayer p,Offer o,Set<String> completed){
        return completed.contains(o.quest())&&CampaignTraderAccount.unlocked(p,o.trader())&&CampaignTraderAccount.loyalty(p,o.trader())>=o.loyalty();
    }
    private static String buy(ServerPlayer p,Offer o){
        if(!accessible(p)||!p.containerMenu.getCarried().isEmpty()||!unlocked(p,o,CampaignRuntime.completedQuests(p)))return "Quête ou fidélité requise.";
        var account=SupplyContract.account(p);int balance=account.getInt("credits");
        if(balance<o.price())return "Crédits insuffisants.";
        var reward=Map.of(o.item(),o.count());
        if(!CampaignRewardInbox.canQueue(p,reward)||CampaignSupplies.rewardStack(o.item()).isEmpty())return "Article indisponible.";
        CampaignRewardInbox.queue(p,reward);
        account.putInt("credits",balance-o.price());
        return "Achat effectué : "+o.count()+" objet(s), -"+o.price()+" crédits. À récupérer dans la réserve.";
    }
    static void open(ServerPlayer p,CampaignTrader trader,int requested,Runnable back){
        if(!accessible(p)||p.containerMenu!=p.inventoryMenu||!p.containerMenu.getCarried().isEmpty())return;
        var completed=CampaignRuntime.completedQuests(p);
        var offers=OFFERS.stream().filter(o->o.trader()==trader).toList();
        int size=27,pages=Math.max(1,(offers.size()+size-1)/size),page=Math.clamp(requested,0,pages-1);
        var c=new SimpleContainer(54);var actions=new HashMap<Integer,Runnable>();
        c.setItem(4,tile(new ItemStack(Items.GOLD_INGOT),trader.title+" · Offres de quête",List.of("Page "+(page+1)+"/"+pages,offers.size()+" offres adaptées")));
        for(int i=page*size;i<Math.min(offers.size(),(page+1)*size);i++){
            var o=offers.get(i);boolean available=unlocked(p,o,completed);var item=CampaignSupplies.rewardStack(o.item());
            if(item.isEmpty())continue;
            String name=item.getHoverName().getString();
            int slot=9+i-page*size;
            c.setItem(slot,tile(available?item:new ItemStack(Items.GRAY_DYE),name,List.of(
                "Lot : "+o.count()+" · "+o.price()+" crédits","Quête : "+CampaignFrench.title(o.questName()),"Fidélité "+o.loyalty(),
                available?"Clic : acheter vers la réserve":"Verrouillé")));
            if(available)actions.put(slot,()->{p.sendSystemMessage(Component.literal(buy(p,o)));open(p,trader,page,back);});
        }
        c.setItem(49,tile(new ItemStack(Items.COMPASS),"Retour aux quêtes",List.of()));actions.put(49,back);
        if(page>0){c.setItem(45,tile(new ItemStack(Items.ARROW),"Page précédente",List.of()));actions.put(45,()->open(p,trader,page-1,back));}
        if(page+1<pages){c.setItem(53,tile(new ItemStack(Items.ARROW),"Page suivante",List.of()));actions.put(53,()->open(p,trader,page+1,back));}
        p.openMenu(new SimpleMenuProvider((id,inv,ignored)->new ChestMenu(MenuType.GENERIC_9x6,id,inv,c,6){
            @Override public boolean stillValid(Player player){return player==p&&accessible(p);}
            @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
            @Override public void clicked(int slot,int button,ClickType type,Player player){
                if(player!=p||p.containerMenu!=this||!stillValid(player)||!getCarried().isEmpty()||type!=ClickType.PICKUP||button!=0)return;
                Runnable action=actions.get(slot);if(action==null)return;
                p.closeContainer();action.run();
            }
        },Component.literal("EFM · Offres de quête")));
    }
    private static ItemStack tile(ItemStack icon,String name,List<String> lines){
        icon=icon.copyWithCount(1);icon.set(DataComponents.CUSTOM_NAME,Component.literal(name).withStyle(ChatFormatting.GOLD));
        icon.set(DataComponents.LORE,new ItemLore(lines.stream().map(s->(Component)Component.literal(s).withStyle(ChatFormatting.GRAY)).toList()));
        return icon;
    }
    private CampaignUnlockShop(){}
}
