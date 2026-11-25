package fr.taczoperations.operations;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAmmo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;

/** Read-only advisory snapshot; no items are granted, moved or reserved. */
public record RaidLoadout(String weapon,int condition,boolean jammed,int loaded,int reserve,int guns,
                          int armor,int armorCondition,int medical,int bleeding,int food,int free,boolean nightOptics){
    public static RaidLoadout inspect(Player player){
        var carried=new ArrayList<ItemStack>(player.getInventory().items);
        carried.add(player.getOffhandItem());
        for(var stack:java.util.List.copyOf(carried))carried.addAll(FieldPack.contents(stack));
        for(String id:new String[]{FieldInventoryMenu.GOGGLES,FieldInventoryMenu.BAG,FieldInventoryMenu.RIG}){
            var stack=FieldInventoryMenu.current(FieldInventoryMenu.handler(player,id));
            carried.add(stack);carried.addAll(FieldPack.contents(stack));
        }
        ItemStack selected=player.getMainHandItem();
        int guns=0,medical=0,bleeding=0,food=0,free=0;
        boolean optics=false;
        for(var stack:player.getInventory().items)if(stack.isEmpty())free++;
        for(String id:new String[]{FieldInventoryMenu.BAG,FieldInventoryMenu.RIG})
            for(var stack:FieldPack.contents(FieldInventoryMenu.current(FieldInventoryMenu.handler(player,id))))if(stack.isEmpty())free++;
        for(var stack:carried){
            if(stack.isEmpty())continue;
            if(stack.getItem() instanceof IGun){
                guns++;
                if(!(selected.getItem() instanceof IGun))selected=stack;
            }
            if(stack.getItem() instanceof FieldMedicine){
                medical+=stack.getCount();
                String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                if(id.equals("bandage")||id.equals("hemostat"))bleeding+=stack.getCount();
            }
            if(stack.has(DataComponents.FOOD))food+=stack.getCount();
            if(isOptics(stack))optics=true;
        }
        int armor=0,armorCondition=100;
        for(var slot:BallisticVest.SLOTS){
            var stack=player.getItemBySlot(slot);
            if(BallisticVest.tier(stack,slot)>0){
                armor++;
                armorCondition=Math.min(armorCondition,BallisticVest.condition(stack));
            }
            if(isOptics(stack))optics=true;
        }
        int loaded=0,reserve=0,condition=0;
        boolean jammed=false;
        String weapon="Aucune arme TaCZ";
        if(selected.getItem() instanceof IGun gun){
            weapon=selected.getHoverName().getString();
            condition=WeaponCondition.condition(selected);
            jammed=WeaponCondition.jammed(selected);
            loaded=Math.max(0,gun.getCurrentAmmoCount(selected))+(gun.hasBulletInBarrel(selected)?1:0);
            var caliber=TimelessAPI.getCommonGunIndex(gun.getGunId(selected))
                .map(index->index.getGunData().getAmmoId()).orElse(null);
            if(caliber!=null)for(var stack:carried)
                if(stack.getItem() instanceof IAmmo ammo&&caliber.equals(ammo.getAmmoId(stack)))reserve+=stack.getCount();
        }
        return new RaidLoadout(weapon,condition,jammed,loaded,reserve,guns,armor,armorCondition,medical,bleeding,food,free,optics);
    }
    private static boolean isOptics(ItemStack stack){
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.equals("taczoperations:night_vision_goggles")||id.equals("taczoperations:thermal_goggles");
    }
}
