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
        return roll<35?ASSAULT:roll<55?RECON:roll<70?MARKSMAN:roll<90?SUPPORT:MEDIC;
    }
    String preferredGun(Random rng){return preferred[rng.nextInt(preferred.length)];}
    int reserve(Random rng){return ammoBase+rng.nextInt(ammoSpread);}
    ItemStack fieldLoot(Random rng){
        return switch(this){
            case MEDIC->medicine(rng.nextInt(4)==0?"salewa":rng.nextBoolean()?"hemostat":"bandage",1);
            case SUPPORT->rng.nextBoolean()?TradeLoot.PARACORD.stack():TradeLoot.SCREWDRIVER.stack();
            case RECON->rng.nextBoolean()?TradeLoot.MEASURING_TAPE.stack():TradeLoot.TEA_BOX.stack();
            case MARKSMAN->rng.nextBoolean()?medicine("bandage",1):TradeLoot.RIPSTOP_FABRIC.stack();
            case ASSAULT->medicine(rng.nextInt(4)==0?"splint":"bandage",1);
        };
    }
    static ItemStack medicine(String id,int amount){
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("taczoperations",id)),amount);
    }
}
