package fr.taczoperations.operations;

import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.Random;

// Stable identity across chunk unloads. Independent of visual variant and armor rarity.
enum PmcSpecialty {
    ASSAULT("Assaut",new String[]{"m4a1","ak47"},18,13),
    RECON("Reconnaissance",new String[]{"hk_mp5a5","glock_17"},14,12),
    MARKSMAN("Tir de précision",new String[]{"ai_awp"},8,8),
    SUPPORT("Soutien",new String[]{"m870","m4a1"},18,13),
    MEDIC("Médic",new String[]{"hk_mp5a5","m4a1"},12,13);

    final String title;
    final String[] preferred;
    final int ammoBase,ammoSpread;
    PmcSpecialty(String title,String[] preferred,int base,int spread){
        this.title=title;this.preferred=preferred;this.ammoBase=base;this.ammoSpread=spread;
    }
    static PmcSpecialty get(Pillager p){
        int roll=Math.floorMod(p.getUUID().hashCode(),100);
        return roll<50?ASSAULT:roll<70?RECON:roll<85?SUPPORT:roll<95?MEDIC:MARKSMAN;
    }
    String preferredGun(Random rng){return preferred[rng.nextInt(preferred.length)];}
    int reserve(Random rng){return ammoBase+rng.nextInt(ammoSpread);}
    int lootChance(){return this==MARKSMAN?80:this==MEDIC?75:this==SUPPORT?65:this==RECON?55:50;}
    ItemStack fieldLoot(Random rng){
        // Specialty biases the pool, but never restricts it. Every PMC
        // can carry any kind of item, including nothing in a given slot.
        int roll=rng.nextInt(100);
        int medical=this==MEDIC?60:25;
        if(roll<medical){
            int med=rng.nextInt(100);
            String id=med<40?"bandage":med<60?"splint":med<78?"salewa":med<93?"hemostat":"medkit";
            return medicine(id,id.equals("bandage")?1+rng.nextInt(2):1);
        }
        if(roll<medical+10)return RaidRelic.roll(rng).stack();
        if(roll<medical+15)return OperationsShop.attachment(rng.nextBoolean()?"sight_552":"extended_mag_1");
        int category=rng.nextInt(3);
        if(category==2)category=3;
        // Extra preference for industrial supplies on support, without
        // excluding the medical or civilian pools.
        if(this==SUPPORT&&rng.nextBoolean())category=1;
        TradeLoot loot=TradeLoot.roll(rng,category,0);
        return loot==null?ItemStack.EMPTY:loot.stack();
    }
    static ItemStack medicine(String id,int amount){
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("taczoperations",id)),amount);
    }
}
