package fr.taczoperations.operations;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public final class BlackMarket {
    public static final int SIZE=6;
    private record Offer(String id,String title,int price,int stock,OperationsShop merchant,int loyalty,Supplier<ItemStack> item){}
    private static final String STATE="black_market_v1";
    static long day(){return LocalDate.now(ZoneOffset.UTC).toEpochDay();}
    private static ItemStack medical(String id,int count){
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("taczoperations",id)),count);
    }
    private static Offer armor(String id,EquipmentSlot slot,int tier,int price){
        return new Offer(id,BallisticVest.name(slot)+" · Classe "+tier,price,1,OperationsShop.SALVAGER,tier==3?3:4,()->BallisticVest.create(slot,tier,BallisticVest.classColor(tier)));
    }
    private static List<Offer> rotation(long day) {
        var pool=new ArrayList<>(List.of(
            armor("c3_head",EquipmentSlot.HEAD,3,1550),
            armor("c3_chest",EquipmentSlot.CHEST,3,1950),
            armor("c3_legs",EquipmentSlot.LEGS,3,1450),
            armor("c4_head",EquipmentSlot.HEAD,4,4400),
            armor("c4_chest",EquipmentSlot.CHEST,4,5500),
            armor("c4_legs",EquipmentSlot.LEGS,4,4100),
            new Offer("nvg","Vision nocturne",12750,1,OperationsShop.SALVAGER,4,()->medical("night_vision_goggles",1)),
            new Offer("thermal","Lunettes thermiques",51000,1,OperationsShop.SALVAGER,4,()->medical("thermal_goggles",1)),
            new Offer("salewa","Lot de 4 Salewa",500,2,OperationsShop.MEDIC,1,()->medical("salewa",4)),
            new Offer("hemostat","Lot de 4 kits hémostatiques",400,2,OperationsShop.MEDIC,1,()->medical("hemostat",4)),
            new Offer("bandages","Lot de 8 bandages",295,2,OperationsShop.MEDIC,1,()->medical("bandage",8)),
            new Offer("splints","Lot de 4 attelles",320,2,OperationsShop.MEDIC,1,()->medical("splint",4))
        ));
        Collections.shuffle(pool,new Random(day ^ 0x7a5b3c129L));
        return List.copyOf(pool.subList(0,SIZE));
    }
    private static CompoundTag state(ServerPlayer player,long day) {
        var account=SupplyContract.account(player);
        var state=account.getCompound(STATE);
        if(!state.contains("day")||state.getLong("day")<day){
            state=new CompoundTag();state.putLong("day",day);
            account.put(STATE,state);
        }
        return state;
    }
    private static boolean eligible(ServerPlayer p,Offer o){
        return OperationsShop.reputationLevel(o.merchant.reputation(p),SupplyContract.experienceEarned(p))>=o.loyalty;
    }
    private static int remaining(CompoundTag state,Offer o){return Math.max(0,o.stock-Math.max(0,state.getInt(o.id)));}
    static List<String> view(ServerPlayer p) {
        long now=day();var state=state(p,now);
        var list=new ArrayList<String>();
        list.add(Long.toString(now));
        for(var o:rotation(now)){
            String requirement=o.loyalty==1?"Libre":o.merchant.title+" · fidélité "+o.loyalty+" · niv. "+OperationsShop.requiredPlayerLevel(o.loyalty);
            list.add(o.title+"|"+o.price+"|"+remaining(state,o)+"|"+(eligible(p,o)?1:0)+"|"+requirement);
        }
        return List.copyOf(list);
    }
    static String buy(ServerPlayer p,int index,long expectedDay){
        if(!p.isAlive()||p.isSpectator()||p.level().dimension().equals(ForestExpedition.FOREST))return "Marché noir disponible hors raid.";
        long now=day();
        if(now!=expectedDay)return "Le marché a été renouvelé. Sélectionne une nouvelle offre.";
        if(index<0||index>=SIZE)return "Offre indisponible.";
        var o=rotation(now).get(index);var state=state(p,now);
        if(remaining(state,o)==0)return "Stock épuisé pour aujourd'hui.";
        if(!eligible(p,o))return "Niveau ou fidélité insuffisants.";
        int balance=SupplyContract.balance(p);
        if(balance<o.price)return "Crédits insuffisants.";
        ItemStack stack=o.item.get();if(stack.isEmpty())return "Article indisponible.";
        var inventory=p.getInventory();
        int capacity=inventory.items.stream().mapToInt(slot->slot.isEmpty()?stack.getMaxStackSize():ItemStack.isSameItemSameComponents(slot,stack)?slot.getMaxStackSize()-slot.getCount():0).sum();
        if(capacity<stack.getCount())return "Inventaire plein.";
        inventory.add(stack);
        state.putInt(o.id,state.getInt(o.id)+1);
        SupplyContract.account(p).putInt("credits",balance-o.price);
        inventory.setChanged();
        return "Achat au marché noir effectué · -"+o.price+" crédits.";
    }
    private BlackMarket(){}
}
