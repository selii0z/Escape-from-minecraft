package fr.taczoperations.operations;

import fr.taczoperations.campaign.CampaignTrader;
import fr.taczoperations.campaign.CampaignGraph.State;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Server-owned, read-only catalogue. Click actions recheck live campaign state. */
final class CampaignMenu {
    private static final int PAGE_SIZE=27;
    static void open(ServerPlayer p){open(p,null,0);}
    private static void open(ServerPlayer p,CampaignTrader trader,int requested){
        if(!ProgressionMenus.available(p)||p.containerMenu!=p.inventoryMenu||!p.containerMenu.getCarried().isEmpty())return;
        var live=CampaignRuntime.views(p);
        var all=CampaignPreview.merge(live);
        var liveIds=live.stream().map(CampaignRuntime.QuestView::id).collect(java.util.stream.Collectors.toSet());
        var c=new SimpleContainer(54);
        var actions=new java.util.HashMap<Integer,Runnable>();
        if(trader==null){
            c.setItem(4,tile(Items.WRITTEN_BOOK,"Campagne Tarkov",ChatFormatting.GOLD,List.of(
                live.size()+" quêtes jouables · "+all.size()+" recensées","Sélectionne un marchand")));
            var traders=CampaignTrader.values();
            for(int i=0;i<traders.length;i++){
                var t=traders[i];
                var list=all.stream().filter(q->q.trader().equals(t.id)).toList();
                int completed=(int)list.stream().filter(q->q.state()==State.COMPLETED).count();
                boolean unlocked=CampaignTraderAccount.unlocked(p,t);
                int slot=10+i/7*9+i%7;
                c.setItem(slot,tile(unlocked?icon(t):Items.GRAY_DYE,t.title,unlocked?ChatFormatting.GOLD:ChatFormatting.GRAY,
                    List.of(unlocked?"Fidélité "+CampaignTraderAccount.loyalty(p,t):"Marchand verrouillé",completed+"/"+list.stream().filter(q->liveIds.contains(q.id())).count()+" quêtes jouables terminées",t.questCount+" quêtes dans le catalogue de référence")));
                actions.put(slot,()->open(p,t,0));
            }
            c.setItem(48,tile(Items.MAP,"Anciennes opérations",ChatFormatting.YELLOW,List.of("Campagne militaire originale")));
            actions.put(48,()->ProgressionMenus.missions(p));
        }else{
            var list=all.stream().filter(q->q.trader().equals(trader.id)).toList();
            int pages=Math.max(1,(list.size()+PAGE_SIZE-1)/PAGE_SIZE);
            int page=Math.max(0,Math.min(requested,pages-1));
            c.setItem(4,tile(icon(trader),trader.title+" · "+(page+1)+"/"+pages,ChatFormatting.GOLD,List.of(
                CampaignTraderAccount.unlocked(p,trader)?"Fidélité "+CampaignTraderAccount.loyalty(p,trader):"Marchand verrouillé",
                list.stream().filter(q->liveIds.contains(q.id())).count()+" jouables · "+list.size()+" affichées")));
            for(int i=page*PAGE_SIZE;i<Math.min(list.size(),(page+1)*PAGE_SIZE);i++){
                var q=list.get(i);int slot=9+i-page*PAGE_SIZE;
                var lines=new ArrayList<>(q.lines());
                Item item;ChatFormatting color;
                if(q.claimable()){item=Items.EMERALD;color=ChatFormatting.GREEN;lines.add("Clic : valider et récupérer les récompenses");}
                else if(q.state()==State.AVAILABLE){item=Items.WRITABLE_BOOK;color=ChatFormatting.GOLD;lines.add("Clic : accepter");}
                else if(q.state()==State.COMPLETED){item=Items.LIME_DYE;color=ChatFormatting.GREEN;}
                else if(q.state()==State.ACTIVE){item=Items.PAPER;color=ChatFormatting.AQUA;}
                else{item=Items.GRAY_DYE;color=ChatFormatting.GRAY;}
                c.setItem(slot,tile(item,q.name(),color,lines));
                if(q.claimable()||q.state()==State.AVAILABLE)actions.put(slot,()->{
                    if(q.claimable())CampaignRuntime.claimNamed(p,q.id());else CampaignRuntime.acceptNamed(p,q.id());
                    open(p,trader,page);
                });
            }
            if(page>0){c.setItem(45,tile(Items.ARROW,"Page précédente",ChatFormatting.YELLOW,List.of()));actions.put(45,()->open(p,trader,page-1));}
            if(page+1<pages){c.setItem(53,tile(Items.ARROW,"Page suivante",ChatFormatting.YELLOW,List.of()));actions.put(53,()->open(p,trader,page+1));}
            c.setItem(48,tile(Items.COMPASS,"Tous les marchands",ChatFormatting.AQUA,List.of()));actions.put(48,()->open(p));
        }
        c.setItem(49,tile(Items.BARRIER,"Fermer",ChatFormatting.RED,List.of()));
        actions.put(49,()->{if(Hideout.ownsPlot(p))HideoutMenu.open(p);});
        p.openMenu(new SimpleMenuProvider((id,inv,ignored)->new ChestMenu(MenuType.GENERIC_9x6,id,inv,c,6){
            @Override public boolean stillValid(Player player){return player==p&&ProgressionMenus.available(p);}
            @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
            @Override public void clicked(int slot,int button,ClickType type,Player player){
                if(player!=p||p.containerMenu!=this||!stillValid(player)||!getCarried().isEmpty()||type!=ClickType.PICKUP||button!=0)return;
                Runnable action=actions.get(slot);
                if(action==null)return;
                p.closeContainer();action.run();
            }
        },Component.literal(trader==null?"EFM · Marchands":"EFM · "+trader.title)));
    }
    private static Item icon(CampaignTrader t){return switch(t){
        case PRAPOR->Items.CROSSBOW;case THERAPIST->Items.GOLDEN_APPLE;case SKIER->Items.IRON_NUGGET;
        case PEACEKEEPER->Items.GOLD_INGOT;case MECHANIC->Items.ANVIL;case RAGMAN->Items.LEATHER_CHESTPLATE;
        case JAEGER->Items.COMPASS;case FENCE->Items.ENDER_CHEST;case REF->Items.TARGET;
        case LIGHTKEEPER->Items.LANTERN;case BTR_DRIVER->Items.MINECART;
    };}
    private static ItemStack tile(Item item,String name,ChatFormatting color,List<String> lines){
        var stack=new ItemStack(item);stack.set(DataComponents.CUSTOM_NAME,Component.literal(name).withStyle(color));
        stack.set(DataComponents.LORE,new ItemLore(lines.stream().map(line->(Component)Component.literal(line).withStyle(ChatFormatting.GRAY)).toList()));
        return stack;
    }
    private CampaignMenu(){}
}
