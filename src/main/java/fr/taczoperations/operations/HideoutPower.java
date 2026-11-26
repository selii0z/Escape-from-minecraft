package fr.taczoperations.operations;

import net.minecraft.server.level.ServerPlayer;

final class HideoutPower {
    private static final String POWER="hideout_power",REST="hideout_rest";
    static int energy(ServerPlayer p){
        int tier=Hideout.tier(p,Hideout.Station.GENERATEUR);
        return Math.clamp(SupplyContract.account(p).getInt(POWER),0,tier*24);
    }
    static boolean available(ServerPlayer p){return energy(p)>0;}
    static void use(ServerPlayer p){
        SupplyContract.account(p).putInt(POWER,Math.max(0,energy(p)-1));
    }
    static void refuel(ServerPlayer p){
        if(!Hideout.ownsPlot(p)||p.containerMenu!=p.inventoryMenu)return;
        int tier=Hideout.tier(p,Hideout.Station.GENERATEUR);
        if(tier==0){Hideout.say(p,"Générateur niveau 1 requis.");return;}
        int current=energy(p),capacity=tier*24,amount=tier*12;
        if(capacity-current<amount){Hideout.say(p,"Il faut "+amount+" unités de place libre dans le générateur.");return;}
        var item=TradeLoot.FUEL_CAN.stack().getItem();
        if(!CarriedStorage.of(p).consume(s->s.is(item),1)){Hideout.say(p,"Un bidon de carburant est nécessaire.");return;}
        SupplyContract.account(p).putInt(POWER,current+amount);
        Hideout.say(p,"Générateur : "+(current+amount)+"/"+capacity+" unités.");
    }
    static void repair(ServerPlayer p){
        if(!Hideout.ownsPlot(p)||p.containerMenu!=p.inventoryMenu)return;
        int tier=Hideout.tier(p,Hideout.Station.ATELIER);
        if(tier==0){Hideout.say(p,"Atelier niveau 1 requis.");return;}
        if(!available(p)){Hideout.say(p,"Alimente le générateur.");return;}
        int before=SupplyContract.balance(p);
        String result=WeaponCondition.repair(p,tier*10);
        if(SupplyContract.balance(p)<before)use(p);
        Hideout.say(p,result);
    }
    static void rest(ServerPlayer p){
        if(!Hideout.ownsPlot(p)||p.containerMenu!=p.inventoryMenu)return;
        int tier=Hideout.tier(p,Hideout.Station.REPOS);
        if(tier==0){Hideout.say(p,"Salle de repos niveau 1 requise.");return;}
        var account=SupplyContract.account(p);
        if(account.getInt(REST)>0){Hideout.say(p,"Bonus de repos déjà prêt pour ton prochain raid.");return;}
        account.putInt(REST,60+tier*60);
        Hideout.say(p,"Repos préparé : énergie préservée "+(60+tier*60)+" secondes au prochain raid.");
    }
    static int claimRest(ServerPlayer p){
        var a=SupplyContract.account(p);int seconds=Math.clamp(a.getInt(REST),0,240);
        a.remove(REST);return seconds;
    }
    static void status(ServerPlayer p){
        Hideout.say(p,"Générateur : "+energy(p)+"/"+(Hideout.tier(p,Hideout.Station.GENERATEUR)*24)+" unités · repos prochain raid : "+SupplyContract.account(p).getInt(REST)+" s.");
    }
    private HideoutPower(){}
}
