package fr.taczoperations.operations;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;
import java.util.Comparator;

final class BossLoot {
    private static final String SPARE="efm_boss_spare";
    private static final String BONUS="efm_boss_bonus";
    static void equip(Pillager boss){
        var rng=boss.getRandom();
        int tier=rng.nextInt(100)<50?6:5;
        boss.setItemSlot(EquipmentSlot.CHEST,BallisticVest.create(EquipmentSlot.CHEST,tier,BallisticVest.classColor(tier)));
        boss.setItemSlot(EquipmentSlot.HEAD,BallisticVest.create(EquipmentSlot.HEAD,5,BallisticVest.classColor(5)));
        // Independent fifty-percent premium pair roll. Never repeat a gun ID
        // when two compatible catalogue entries exist.
        if(rng.nextInt(100)<50){
            var candidates=new ArrayList<>(TimelessAPI.getAllCommonGunIndex().stream()
                .filter(e->e.getValue().getGunData()!=null&&e.getValue().getGunData().getAmmoId()!=null
                    &&!e.getValue().getGunData().getFireModeSet().isEmpty()).toList());
            candidates.sort(Comparator.comparing(e->e.getKey().toString()));
            // Select tier 4 first, then tier 3 only if the installed pack is limited.
            var high=candidates.stream().filter(e->OperationsShop.weaponTier(e.getKey(),e.getValue().getGunData().getAmmoId(),e.getValue().getGunData().getAmmoAmount())==4).toList();
            var pool=new ArrayList<>(high.size()>=2?high:candidates.stream().filter(e->OperationsShop.weaponTier(e.getKey(),e.getValue().getGunData().getAmmoId(),e.getValue().getGunData().getAmmoAmount())>=3).toList());
            for(int i=pool.size()-1;i>0;i--)java.util.Collections.swap(pool,i,rng.nextInt(i+1));
            int given=0;
            for(var e:pool){
                ItemStack weapon=OperationsShop.weapon(e.getKey(),Math.max(1,e.getValue().getGunData().getAmmoAmount()));
                if(weapon.isEmpty())continue;
                weapon=ForestCombat.customizeWeapon(boss,weapon,true);
                WeaponCondition.usedLoot(weapon,100);
                var data=weapon.has(DataComponents.CUSTOM_DATA)?weapon.get(DataComponents.CUSTOM_DATA).copyTag():new CompoundTag();
                data.putString("efm_boss_edition",GuardRole.get(boss).name());
                weapon.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
                weapon.set(DataComponents.CUSTOM_NAME,Component.literal(weapon.getHoverName().getString()+" · Édition "+GuardRole.get(boss).title));
                if(given++==0)boss.setItemSlot(EquipmentSlot.MAINHAND,weapon);
                else{boss.getPersistentData().put(SPARE,weapon.saveOptional(boss.registryAccess()));break;}
            }
        }
        // A separate rare bonus, independent of armor and weapons.
        int bonus=rng.nextInt(100);
        ItemStack loot=bonus<10?TradeLoot.LEDX.stack():bonus<25?RaidRelic.OMEGA.stack():bonus<50?TradeLoot.DEFIBRILLATOR.stack():ItemStack.EMPTY;
        if(!loot.isEmpty())boss.getPersistentData().put(BONUS,loot.saveOptional(boss.registryAccess()));
    }
    static ItemStack spare(Pillager p){return ItemStack.parseOptional(p.registryAccess(),p.getPersistentData().getCompound(SPARE));}
    static ItemStack bonus(Pillager p){return ItemStack.parseOptional(p.registryAccess(),p.getPersistentData().getCompound(BONUS));}
    static void clear(Pillager p){p.getPersistentData().remove(SPARE);p.getPersistentData().remove(BONUS);}
    private BossLoot(){}
}
