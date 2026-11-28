package fr.taczoperations.operations;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Knowledge belongs to the player, never the physical item or its owner. */
public final class ItemDiscovery {
    private static final String KEY="efm_discovered_v1";
    public static final int LIMIT=2048,MAX_KEY=256;
    public static boolean inspectable(ItemStack s){
        if(s.isEmpty())return false;
        var id=BuiltInRegistries.ITEM.getKey(s.getItem());
        return RaidOrigin.found(s)||id.getNamespace().equals("taczoperations")||id.getNamespace().equals("tacz");
    }
    public static String identity(ItemStack s){
        if(!inspectable(s))return "";
        var item=s.getItem();
        String key;
        if(item instanceof com.tacz.guns.api.item.IGun gun)key="gun/"+gun.getGunId(s);
        else if(item instanceof com.tacz.guns.api.item.IAmmo ammo)key="ammo/"+ammo.getAmmoId(s);
        else if(item instanceof com.tacz.guns.api.item.IAttachment attachment)key="attachment/"+attachment.getAttachmentId(s);
        else key="item/"+BuiltInRegistries.ITEM.getKey(item);
        return key.length()<=MAX_KEY?key:"";
    }
    // Retain legacy signatures and data; examination is no longer a gameplay mechanic.
    static boolean inspect(ServerPlayer p,ItemStack s){return false;}
    public static void login(PlayerEvent.PlayerLoggedInEvent e){}
    public static void respawn(PlayerEvent.PlayerRespawnEvent e){}
    public static void request(ServerPlayer p,InspectItem msg){}
    private ItemDiscovery(){}
}
