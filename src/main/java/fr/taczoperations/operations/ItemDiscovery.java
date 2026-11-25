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
    static boolean inspect(ServerPlayer p,ItemStack s){
        String key=identity(s);if(key.isEmpty())return false;
        var account=SupplyContract.account(p);var known=account.getCompound(KEY);
        if(known.getBoolean(key))return false;
        if(known.getAllKeys().size()>=LIMIT){
            p.displayClientMessage(Component.literal("Registre d'inspection plein."),true);return false;
        }
        known.putBoolean(key,true);account.put(KEY,known);
        PacketDistributor.sendToPlayer(p,new DiscoveryState(false,java.util.List.of(key)));
        p.displayClientMessage(Component.literal("Objet identifié : "+s.getHoverName().getString()),true);
        return true;
    }
    private static void sync(ServerPlayer p){
        var keys=SupplyContract.account(p).getCompound(KEY).getAllKeys().stream()
            .filter(k->k.length()<=MAX_KEY).limit(LIMIT).sorted().toList();
        PacketDistributor.sendToPlayer(p,new DiscoveryState(true,keys));
    }
    public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    static void request(ServerPlayer p,InspectItem msg){
        if(!p.isAlive()||p.isSpectator()||p.containerMenu.containerId!=msg.menuId()||!p.containerMenu.stillValid(p))return;
        var menu=p.containerMenu;int row=msg.index();
        if(menu instanceof OperationsMenu operations){operations.inspectOffer(p,msg.kind(),row);return;}
        if(msg.kind()!=0||row<0||row>=menu.slots.size())return;
        if(!(menu instanceof FieldInventoryMenu||menu instanceof RaidSearchMenu||menu==p.inventoryMenu))return;
        if(menu instanceof RaidSearchMenu search&&row<27&&row>=search.discoveredSlots())return;
        inspect(p,menu.getSlot(row).getItem());
    }
    private ItemDiscovery(){}
}
