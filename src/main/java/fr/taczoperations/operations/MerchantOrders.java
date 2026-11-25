package fr.taczoperations.operations;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;

/** Four independent daily delivery requests; no automatic consumption in raid. */
final class MerchantOrders {
    static final int SIZE=4;
    private static final String ROOT="merchant_orders_v1";
    private record Order(String title,Item item,int amount,int credits,int xp,int reputation){}
    private static Order trade(String title,TradeLoot loot,int amount){
        int base=loot.price*amount;
        return new Order(title,loot.stack().getItem(),amount,base*130/100+100,100+base/8,12);
    }
    private static Order relic(String title,RaidRelic loot,int amount){
        int base=loot.price*amount;
        return new Order(title,loot.item(),amount,base*130/100+100,100+base/8,12);
    }
    private static Order order(int index,long day){
        var pool=switch(index){
            case 0->List.of(relic("Équipement de transmission",RaidRelic.RADIO,3),relic("Électronique militaire",RaidRelic.CIRCUIT,1),relic("Identification des unités",RaidRelic.PLAQUE,5));
            case 1->List.of(trade("Réserve médicale",TradeLoot.SALINE,4),trade("Bloc opératoire",TradeLoot.MEDICAL_TOOLS,2),trade("Urgence chirurgicale",TradeLoot.SURGICAL_KIT,1),trade("Réserve de transfusion",TradeLoot.BLOOD_SET,2));
            case 2->List.of(trade("Atelier de campagne",TradeLoot.WRENCH,3),trade("Maintenance du dépôt",TradeLoot.HAND_DRILL,2),trade("Réseau de filtration",TradeLoot.WATER_FILTER,2),trade("Équipement textile",TradeLoot.PARACORD,3));
            default->List.of(relic("Renseignement confidentiel",RaidRelic.DOCUMENTS,3),relic("Archives numériques",RaidRelic.DRIVE,1),relic("Dossier de liaison",RaidRelic.CASE,1));
        };
        return pool.get(new Random(day*0x9e3779b97f4a7c15L ^ index*0x632be59bd9b4e019L).nextInt(pool.size()));
    }
    private static CompoundTag state(ServerPlayer p,long day){
        var account=SupplyContract.account(p);
        var state=account.getCompound(ROOT);
        if(!state.contains("day")||state.getLong("day")<day){
            state=new CompoundTag();state.putLong("day",day);account.put(ROOT,state);
        }
        return state;
    }
    static List<String> view(ServerPlayer p){
        long day=BlackMarket.day();
        var state=state(p,day);
        var result=new ArrayList<String>();
        result.add(Long.toString(day));
        for(int i=0;i<SIZE;i++){
            var order=order(i,day);
            int count=p.getInventory().items.stream().filter(s->s.is(order.item())).mapToInt(ItemStack::getCount).sum();
            result.add(OperationsShop.values()[i].title+"|"+order.title()+"|"+new ItemStack(order.item()).getHoverName().getString().replace('|','/')+"|"+order.amount()+"|"+count+"|"+order.credits()+"|"+order.xp()+"|"+order.reputation()+"|"+(state.getBoolean("done"+i)?1:0));
        }
        return List.copyOf(result);
    }
    static String deliver(ServerPlayer p,int index,long quotedDay){
        if(!p.isAlive()||p.isSpectator()||p.level().dimension().equals(ForestExpedition.FOREST))return "Commandes disponibles hors raid.";
        long day=BlackMarket.day();
        if(day!=quotedDay)return "Commandes renouvelées. Vérifie les nouvelles demandes.";
        if(index<0||index>=SIZE)return "Commande indisponible.";
        var state=state(p,day);
        if(state.getBoolean("done"+index))return "Commande déjà livrée aujourd'hui.";
        var order=order(index,day);
        int balance=SupplyContract.balance(p);
        if((long)balance+order.credits()>Integer.MAX_VALUE)return "Solde maximal atteint.";
        if(!SupplyContract.consume(p,order.item(),order.amount()))return "Quantité insuffisante.";
        state.putBoolean("done"+index,true);
        var account=SupplyContract.account(p);
        account.putInt("credits",balance+order.credits());
        add(account,"merchant_order_xp",order.xp());
        add(account,"merchant_order_rep_"+index,order.reputation());
        SupplyContract.rewardLevels(p);
        return "Commande livrée · +"+order.credits()+" cr. · +"+order.xp()+" XP · +"+order.reputation()+" rép.";
    }
    private static void add(CompoundTag tag,String key,int amount){
        tag.putInt(key,(int)Math.min(Integer.MAX_VALUE,(long)Math.max(0,tag.getInt(key))+amount));
    }
    private MerchantOrders(){}
}
