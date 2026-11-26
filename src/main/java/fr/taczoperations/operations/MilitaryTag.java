package fr.taczoperations.operations;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

final class MilitaryTag {
    static boolean isTag(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("taczoperations:military_tag");}
    static ItemStack create(Pillager guard,ServerPlayer killer){
        var item=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("taczoperations","military_tag"));
        ItemStack stack=new ItemStack(item);
        String faction=(guard.getUUID().getLeastSignificantBits()&1)==0?"USEC":"BEAR";
        String[] calls={"Viper","Rook","Wolf","Ghost","Raven","Hawk","Nomad","Fox","Jackal","Echo","Bishop","Cobra"};
        String name=calls[Math.floorMod(guard.getUUID().hashCode(),calls.length)]+"-"+Integer.toUnsignedString(guard.getUUID().hashCode(),36).toUpperCase(java.util.Locale.ROOT);
        int rank=1+Math.floorMod(guard.getUUID().hashCode(),50);
        String victor=killer==null?"Inconnu":killer.getGameProfile().getName();
        Component weaponName=killer==null?Component.literal("Inconnue"):killer.getMainHandItem().getHoverName().copy();
        String weapon=weaponName.getString();
        if(killer!=null&&killer.getMainHandItem().getItem() instanceof com.tacz.guns.api.item.IGun gun){
            var id=gun.getGunId(killer.getMainHandItem());
            String fallback=id.getPath().replace('_',' ').replace('-',' ').toUpperCase(java.util.Locale.ROOT);
            if(weapon.startsWith("item."))weaponName=Component.translatableWithFallback(weapon,fallback);
        }
        String date=java.time.Instant.now().toString();
        var tag=new CompoundTag();tag.putUUID("operator",guard.getUUID());tag.putString("callsign",name);tag.putString("faction",faction);tag.putInt("rank",rank);
        tag.putString("eliminated_by",victor);tag.putString("weapon",weapon);tag.putString("date",date);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Plaque militaire · "+name));
        stack.set(DataComponents.LORE,new ItemLore(java.util.List.of(Component.literal(faction+" · Niveau "+rank),
            Component.literal("Éliminé par : "+victor),Component.literal("Arme tenue : ").append(weaponName),Component.literal("Date : "+java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.parse(date))))));
        return stack;
    }
    private MilitaryTag(){}
}
