package fr.taczoperations.operations;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Inventory-style prototype panels: presentation items are never transferable. */
final class ProgressionMenus {
    private static final Item[] SKILL_ICONS={Items.LEATHER_BOOTS,Items.IRON_INGOT,Items.GOLDEN_APPLE,Items.SPYGLASS,Items.BLACK_DYE,Items.COMPASS,Items.CROSSBOW,Items.EMERALD};
    private static boolean available(ServerPlayer p){
        return PlayerMarket.available(p)&&(!p.level().dimension().equals(Hideout.DIMENSION)||Hideout.ownsPlot(p));
    }
    private static boolean canOpen(ServerPlayer p){return available(p)&&p.containerMenu==p.inventoryMenu&&p.containerMenu.getCarried().isEmpty();}
    static void missions(ServerPlayer p){
        if(!canOpen(p))return;
        var c=new SimpleContainer(54);int next=StoryMissions.next(p);
        var all=StoryMissions.Mission.values();
        for(int i=0;i<all.length;i++){
            var m=all[i];boolean done=i<next,active=i==next;
            String status=done?"Terminée":active?StoryMissions.current(p)!=null?"Acceptée":"Disponible":"Verrouillée";
            c.setItem(10+(i/7)*9+i%7,tile(done?Items.LIME_DYE:active?Items.WRITABLE_BOOK:Items.GRAY_DYE,m.title,
                done?ChatFormatting.GREEN:active?ChatFormatting.GOLD:ChatFormatting.GRAY,List.of(
                status+" · Niv. "+m.level,
                (m.industrial?"Zone industrielle":"Forêt Noire")+(m.night?" · Nuit obligatoire":""),
                "1. "+m.site,"2. "+m.quantity+" × "+m.relic.title,"3. "+m.kills+" "+m.target.label,
                "4. Extraction réussie",m.credits+" crédits · "+m.xp+" XP")));
        }
        if(next<all.length&&StoryMissions.current(p)==null)c.setItem(40,tile(Items.LIME_CONCRETE,"Accepter : "+all[next].title,ChatFormatting.GREEN,List.of("Niveau "+all[next].level+" requis")));
        else c.setItem(40,tile(Items.PAPER,next==all.length?"Campagne terminée":"Opération acceptée",ChatFormatting.GRAY,List.of()));
        c.setItem(49,tile(Items.BARRIER,"Retour",ChatFormatting.RED,List.of()));
        open(p,c,"EFM · Campagne "+next+"/"+all.length,slot->{
            if(slot==40&&StoryMissions.current(p)==null&&StoryMissions.next(p)<all.length){
                p.closeContainer();StoryMissions.accept(p);missions(p);
            }else if(slot==49){p.closeContainer();if(Hideout.ownsPlot(p))HideoutMenu.open(p);}
        });
    }
    static void skills(ServerPlayer p){
        if(!canOpen(p))return;
        var c=new SimpleContainer(54);var all=PassiveSkills.Skill.values();
        for(int i=0;i<all.length;i++){
            var s=all[i];int level=PassiveSkills.level(p,s);
            int earned=PassiveSkills.xp(p,s)-PassiveSkills.threshold(level);
            int goal=level==50?0:PassiveSkills.threshold(level+1)-PassiveSkills.threshold(level);
            String progress=level==50?"Niveau maximum":earned+" / "+goal+" XP";
            String bonus=s==PassiveSkills.Skill.TRADE?"Réduction achats : "+PassiveSkills.bonus(p,s)+" %":"Bonus actuel : "+PassiveSkills.bonus(p,s)+" %";
            c.setItem(10+(i/4)*18+(i%4)*2,tile(SKILL_ICONS[i],s.title+" · "+level+"/50",ChatFormatting.GOLD,List.of(progress,bonus,"Bonus maximal : "+s.maxBonus+" %")));
            c.setItem(19+(i/4)*18+(i%4)*2,tile(Items.EXPERIENCE_BOTTLE,progress,ChatFormatting.GREEN,List.of()));
        }
        c.setItem(49,tile(Items.BARRIER,"Retour",ChatFormatting.RED,List.of()));
        open(p,c,"EFM · Compétences",slot->{if(slot==49){p.closeContainer();if(Hideout.ownsPlot(p))HideoutMenu.open(p);}});
    }
    private static void open(ServerPlayer p,SimpleContainer c,String title,IntConsumer action){
        p.openMenu(new SimpleMenuProvider((id,inv,ignored)->new ChestMenu(MenuType.GENERIC_9x6,id,inv,c,6){
            @Override public boolean stillValid(Player player){return player==p&&available(p);}
            @Override public void clicked(int slot,int button,ClickType click,Player player){
                if(player!=p||p.containerMenu!=this||!stillValid(player)||!getCarried().isEmpty()||button!=0||click!=ClickType.PICKUP||slot<0||slot>=54)return;
                action.accept(slot);
            }
            @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
        },Component.literal(title)));
    }
    private static ItemStack tile(Item item,String title,ChatFormatting color,List<String> lines){
        var stack=new ItemStack(item);stack.set(DataComponents.CUSTOM_NAME,Component.literal(title).withStyle(color));
        List<Component> lore=new ArrayList<>();for(String line:lines)lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        if(!lore.isEmpty())stack.set(DataComponents.LORE,new ItemLore(lore));return stack;
    }
    private ProgressionMenus(){}
}
