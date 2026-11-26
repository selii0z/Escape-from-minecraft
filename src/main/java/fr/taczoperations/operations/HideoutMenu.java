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

/** Read-only action tiles; all transactions are checked again on the server. */
final class HideoutMenu {
    private static final Item[] ICONS={Items.CHEST,Items.GOLDEN_APPLE,Items.ANVIL,Items.REDSTONE,
        Items.COOKED_BEEF,Items.WATER_BUCKET,Items.GREEN_BED,Items.TARGET};
    static void open(ServerPlayer p){
        if(!Hideout.ownsPlot(p)||p.containerMenu!=p.inventoryMenu||!p.containerMenu.getCarried().isEmpty())return;
        var icons=new SimpleContainer(54){
            @Override public boolean stillValid(Player player){return player==p&&Hideout.ownsPlot(p);}
        };
        for(int i=0;i<Hideout.Station.values().length;i++){
            var station=Hideout.Station.values()[i];int tier=Hideout.tier(p,station);
            String label=station.label+" · "+tier+"/3";
            icons.setItem(10+i,tile(ICONS[i],label,tier==0?ChatFormatting.GRAY:ChatFormatting.GREEN));
            if(tier<3)icons.setItem(19+i,upgradeTile(p,station,tier));
            else icons.setItem(19+i,tile(Items.LIME_DYE,"Niveau maximum",ChatFormatting.GREEN));
        }
        icons.setItem(28,tile(Items.CHEST,"Ouvrir la réserve",ChatFormatting.AQUA));
        icons.setItem(29,recipe(p,Items.PAPER,HideoutServices.Recipe.BANDAGE,"Bandages ×2"));
        icons.setItem(30,recipe(p,Items.GOLDEN_APPLE,HideoutServices.Recipe.SALEWA,"Salewa ×1"));
        icons.setItem(31,recipe(p,Items.COOKED_BEEF,HideoutServices.Recipe.RATION,"Rations ×3"));
        icons.setItem(32,recipe(p,Items.GLASS_BOTTLE,HideoutServices.Recipe.WATER,"Eau ×4"));
        icons.setItem(33,fuelTile(p));
        icons.setItem(34,repairTile(p));
        icons.setItem(35,restTile(p));
        icons.setItem(37,tile(Items.TARGET,"Entrer dans le stand de tir",ChatFormatting.AQUA));
        icons.setItem(39,tile(Items.WRITABLE_BOOK,"Campagne Tarkov",ChatFormatting.GOLD));
        icons.setItem(41,tile(Items.EXPERIENCE_BOTTLE,"Compétences passives",ChatFormatting.GREEN));
        icons.setItem(43,tile(Items.PAINTING,"Décoration : "+HideoutDecor.current(p).label+" · clic pour changer",ChatFormatting.AQUA));
        icons.setItem(45,tile(Items.REDSTONE,"Énergie : "+HideoutPower.energy(p)+"/"+Hideout.tier(p,Hideout.Station.GENERATEUR)*24,ChatFormatting.YELLOW));
        icons.setItem(46,tile(Items.GOLD_INGOT,"Solde : "+SupplyContract.balance(p)+" crédits",ChatFormatting.GOLD));
        icons.setItem(49,tile(Items.COMPASS,"Quitter la planque",ChatFormatting.RED));
        p.openMenu(new SimpleMenuProvider((id,inv,ignored)->new HideoutControlMenu(id,inv,icons),Component.literal("EFM · Gestion de la planque")),buf->{});
    }
    static boolean action(ServerPlayer p,int slot){
        if(!Hideout.ownsPlot(p)||!((slot>=19&&slot<=26)||(slot>=28&&slot<=35)||slot==37||slot==39||slot==41||slot==43||slot==49))return false;
        if(slot>=19&&slot<=26&&Hideout.tier(p,Hideout.Station.values()[slot-19])>=3)return false;
        p.closeContainer();
        if(slot>=19&&slot<=26)Hideout.upgrade(p,Hideout.Station.values()[slot-19].id);
        else switch(slot){
            case 28->HideoutServices.reserve(p);
            case 29->HideoutServices.craft(p,"bandage");
            case 30->HideoutServices.craft(p,"salewa");
            case 31->HideoutServices.craft(p,"ration");
            case 32->HideoutServices.craft(p,"eau");
            case 33->HideoutPower.refuel(p);
            case 34->HideoutPower.repair(p);
            case 35->HideoutPower.rest(p);
            case 37->HideoutRange.toggle(p);
            case 39->CampaignMenu.open(p);
            case 41->ProgressionMenus.skills(p);
            case 43->HideoutDecor.cycle(p);
            case 49->Hideout.leave(p);
            default->{}
        }
        if(slot!=49&&slot!=37&&p.containerMenu==p.inventoryMenu&&Hideout.ownsPlot(p))open(p);
        return true;
    }
    private static ItemStack repairTile(ServerPlayer p){
        int tier=Hideout.tier(p,Hideout.Station.ATELIER),balance=SupplyContract.balance(p);
        var gun=p.getMainHandItem();
        boolean equipped=gun.getItem() instanceof com.tacz.guns.api.item.IGun;
        int cost=WeaponCondition.repairCost(gun,tier*10);
        boolean power=HideoutPower.available(p);
        var stack=tile(Items.ANVIL,"Réparer l'arme en main",tier>0&&equipped&&cost>0&&power&&balance>=cost?ChatFormatting.GREEN:ChatFormatting.GRAY);
        stack.set(DataComponents.LORE,new net.minecraft.world.item.component.ItemLore(java.util.List.<Component>of(
            requirement("Atelier niveau 1",tier>0),
            requirement(equipped?gun.getHoverName().getString():"Tenir une arme TaCZ en main",equipped),
            Component.literal(equipped?String.format(java.util.Locale.FRANCE,"État : %.1f %%",WeaponCondition.condition(gun)/100.0):"Aucune arme sélectionnée").withStyle(ChatFormatting.GRAY),
            requirement("Énergie : 1 unité",power),
            requirement(cost>0?"Crédits : "+balance+"/"+cost:"Aucune réparation nécessaire",equipped&&balance>=cost),
            Component.literal("Réduction atelier : "+tier*10+" %").withStyle(ChatFormatting.AQUA))));
        return stack;
    }
    private static ItemStack fuelTile(ServerPlayer p){
        int tier=Hideout.tier(p,Hideout.Station.GENERATEUR),energy=HideoutPower.energy(p);
        int amount=tier*12,capacity=tier*24;
        var fuel=TradeLoot.FUEL_CAN.stack().getItem();
        int owned=CarriedStorage.of(p).count(s->s.is(fuel));
        boolean ready=tier>0&&owned>0&&capacity-energy>=amount;
        var stack=tile(Items.COAL,"Ravitailler le générateur",ready?ChatFormatting.GREEN:ChatFormatting.GRAY);
        stack.set(DataComponents.LORE,new net.minecraft.world.item.component.ItemLore(java.util.List.<Component>of(
            requirement("Générateur niveau 1",tier>0),
            requirement("Bidons : "+owned+"/1",owned>0),
            requirement("Place disponible : "+(capacity-energy)+"/"+amount,tier>0&&capacity-energy>=amount),
            Component.literal("Énergie : "+energy+"/"+capacity+" · +"+amount+" unités").withStyle(ChatFormatting.AQUA))));
        return stack;
    }
    private static ItemStack restTile(ServerPlayer p){
        int tier=Hideout.tier(p,Hideout.Station.REPOS);
        int seconds=SupplyContract.account(p).getInt("hideout_rest");
        var stack=tile(Items.GREEN_BED,seconds>0?"Repos déjà préparé":"Préparer le bonus de repos",tier>0?ChatFormatting.GREEN:ChatFormatting.GRAY);
        stack.set(DataComponents.LORE,new net.minecraft.world.item.component.ItemLore(java.util.List.<Component>of(
            requirement("Salle de repos niveau 1",tier>0),
            Component.literal(seconds>0?"Bonus prêt : "+seconds+" secondes":tier>0?"Prochain raid : "+(60+tier*60)+" secondes":"Installation verrouillée").withStyle(ChatFormatting.AQUA),
            Component.literal("Bonus consommé au départ du prochain raid.").withStyle(ChatFormatting.GRAY))));
        return stack;
    }
    private static ItemStack upgradeTile(ServerPlayer p,Hideout.Station station,int tier){
        int amount=tier+1,cost=station.price*amount,required=station.unlock+tier*10;
        int rank=MilitaryProgression.level(SupplyContract.experienceEarned(p)),balance=SupplyContract.balance(p);
        var material=station.material.stack().getItem();
        int found=CarriedStorage.of(p).count(s->s.is(material)&&RaidOrigin.found(s));
        boolean ready=rank>=required&&balance>=cost&&found>=amount;
        var stack=tile(ready?Items.EXPERIENCE_BOTTLE:Items.GRAY_DYE,
            station.label+" · niveau "+amount,ready?ChatFormatting.GREEN:ChatFormatting.GRAY);
        stack.set(DataComponents.LORE,new net.minecraft.world.item.component.ItemLore(java.util.List.<Component>of(
            requirement("Niveau militaire : "+rank+"/"+required,rank>=required),
            requirement("Crédits : "+balance+"/"+cost,balance>=cost),
            requirement(station.material.title+" : "+found+"/"+amount,found>=amount),
            Component.literal("Matériaux trouvés en raid uniquement.").withStyle(ChatFormatting.GRAY),
            Component.literal(switch(station){
                case RESERVE->"Réserve : "+(amount*18)+" cases";
                case GENERATEUR->"Capacité : "+(amount*24)+" unités";
                case ATELIER->"Réparation : -"+(amount*10)+" %";
                case INFIRMERIE->amount==1?"Bandages disponibles":amount==2?"Salewa disponible":"Infirmerie niveau maximum";
                case CUISINE->"Fabrication de rations";
                case EAU->"Production de bouteilles d'eau";
                case REPOS->"Repos : "+(60+amount*60)+" secondes";
                case TIR->"Cibles jusqu'à "+(amount==1?25:amount==2?50:100)+" m";
            }).withStyle(ChatFormatting.AQUA))));
        return stack;
    }
    private static ItemStack recipe(ServerPlayer p,Item item,HideoutServices.Recipe r,String label){
        var material=r.material.stack().getItem();
        int owned=CarriedStorage.of(p).count(s->s.is(material));
        boolean tier=Hideout.tier(p,r.station)>=r.tier,power=HideoutPower.available(p),money=SupplyContract.balance(p)>=r.price;
        var stack=tile(item,label+" · "+r.price+" Cr.",tier&&power&&money&&owned>=r.amount?ChatFormatting.GREEN:ChatFormatting.GRAY);
        stack.set(DataComponents.LORE,new net.minecraft.world.item.component.ItemLore(java.util.List.<Component>of(
            requirement(r.station.label+" niveau "+r.tier,tier),
            requirement(r.material.title+" : "+owned+"/"+r.amount,owned>=r.amount),
            requirement("Énergie : 1 unité",power),
            requirement("Crédits : "+r.price,money),
            Component.literal("Capacité vérifiée à la fabrication.").withStyle(ChatFormatting.GRAY))));
        return stack;
    }
    private static Component requirement(String label,boolean ok){
        return Component.literal(label).withStyle(ok?ChatFormatting.GREEN:ChatFormatting.RED);
    }
    private static ItemStack tile(Item item,String label,ChatFormatting color){
        var stack=new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal(label).withStyle(color));
        return stack;
    }
    private HideoutMenu(){}
}
