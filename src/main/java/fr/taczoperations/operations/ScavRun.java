package fr.taczoperations.operations;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.*;

/** Server-owned Scav character. The original PMC inventory is escrowed in persistent player data. */
public final class ScavRun {
    private static final String ESCROW="scav_pmc_escrow_v1", LOOT="scav_loot_v1", NEXT="scav_next_ms";
    private static final long COOLDOWN=45*60*1000L;
    private static final int LIMIT=512;
    private static final System.Logger LOG=System.getLogger("taczoperations.scav");
    static boolean active(ServerPlayer p){return SupplyContract.account(p).contains(ESCROW,10);}
    static long remaining(ServerPlayer p){return Math.max(0,SupplyContract.account(p).getLong(NEXT)-System.currentTimeMillis());}
    static String problem(ServerPlayer p){
        if(p.isCreative()||p.isSpectator()||!p.isAlive())return "Scav disponible en survie uniquement.";
        if(active(p))return "Ton équipement PMC est encore en récupération.";
        if(remaining(p)>0){long seconds=(remaining(p)+999)/1000;return "Scav disponible dans "+seconds/60+" min "+seconds%60+" s.";}
        if(SupplyContract.account(p).getList(LOOT,10).size()>LIMIT-96)return "Récupère le butin de ton précédent Scav.";
        if(!SupplyContract.account(p).getList("secure_cases_pending",10).isEmpty())return "Récupère d'abord ton coffre sécurisé.";
        return "";
    }
    private static Map<String,IItemHandlerModifiable> handlers(ServerPlayer p){
        if(!ModList.get().isLoaded("curios"))return Map.of();
        return fr.taczoperations.compat.FieldCurios.scavHandlers(p);
    }
    private record SlotValue(String handler,int slot,ItemStack stack){}
    private static List<SlotValue> readSlots(ServerPlayer p,Map<String,IItemHandlerModifiable> handlers){
        var values=new ArrayList<SlotValue>();var inventory=p.getInventory();
        for(int i=0;i<inventory.getContainerSize();i++)values.add(new SlotValue("",i,inventory.getItem(i)));
        handlers.forEach((id,h)->{for(int i=0;i<h.getSlots();i++)values.add(new SlotValue(id,i,h.getStackInSlot(i)));});
        return values;
    }
    private static String slotKey(SlotValue v){return v.handler()+"/"+v.slot();}
    private static void set(ServerPlayer p,Map<String,IItemHandlerModifiable> handlers,String id,int slot,ItemStack stack){
        if(id.isEmpty())p.getInventory().setItem(slot,stack);
        else handlers.get(id).setStackInSlot(slot,stack);
    }
    private static ListTag encode(ServerPlayer p,List<SlotValue> values){
        var result=new ListTag();
        for(var v:values){
            var row=new CompoundTag();row.putString("handler",v.handler());row.putInt("slot",v.slot());
            row.put("item",v.stack().saveOptional(p.registryAccess()));result.add(row);
        }
        return result;
    }
    private static List<SlotValue> decode(ServerPlayer p,ListTag rows,Map<String,IItemHandlerModifiable> handlers){
        var result=new ArrayList<SlotValue>();var used=new HashSet<String>();
        for(int i=0;i<rows.size();i++){
            var row=rows.getCompound(i);String id=row.getString("handler");int slot=row.getInt("slot");
            int size=id.isEmpty()?p.getInventory().getContainerSize():handlers.containsKey(id)?handlers.get(id).getSlots():-1;
            if(slot<0||slot>=size||!used.add(id+"/"+slot)||!row.contains("item",10))throw new IllegalStateException("PMC slot unavailable: "+id+"/"+slot);
            var tag=row.getCompound("item");var stack=ItemStack.parseOptional(p.registryAccess(),tag);
            if(!tag.isEmpty()&&stack.isEmpty())throw new IllegalStateException("PMC item cannot be decoded");
            result.add(new SlotValue(id,slot,stack));
        }
        if(result.isEmpty())throw new IllegalStateException("Missing PMC escrow");
        return result;
    }
    static String prepare(ServerPlayer p){
        String problem=problem(p);if(!problem.isEmpty())return problem;
        p.closeContainer();
        if(!p.containerMenu.getCarried().isEmpty())return "Range ton objet tenu par le curseur.";
        try{
            var kit=new ArrayList<ItemStack>();
            int roll=p.getRandom().nextInt(3);
            String gun=roll==0?"glock_17":roll==1?"hk_mp5a5":"m870";
            String ammo=roll==2?"12g":"9mm";
            var weapon=OperationsShop.weapon(gun,roll==0?12:roll==1?24:5);
            var ammunition=OperationsShop.ammo(ammo,roll==2?16:48);
            if(weapon.isEmpty()||ammunition.isEmpty())return "Équipement Scav indisponible.";
            kit.add(weapon);kit.add(ammunition);kit.add(new ItemStack(Items.BREAD,3));
            kit.add(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations","bandage")),2));
            kit.add(FieldPack.create(false,0));
            var handlers=handlers(p);
            if(ModList.get().isLoaded("curios")&&handlers.isEmpty())return "Inventaire Curios indisponible.";
            var current=readSlots(p,handlers);
            var saved=new CompoundTag();saved.put("slots",encode(p,current));saved.putInt("selected",p.getInventory().selected);
            saved.putBoolean("survived",false);saved.putBoolean("finished",false);saved.putBoolean("hostile",false);
            SupplyContract.account(p).put(ESCROW,saved);
            for(var v:current)set(p,handlers,v.handler(),v.slot(),ItemStack.EMPTY);
            for(int i=0;i<kit.size();i++)p.getInventory().setItem(i,kit.get(i));
            p.getInventory().selected=0;
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new ItemStack(Items.LEATHER_CHESTPLATE));
            p.getInventory().setChanged();
            return "";
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Scav preparation failed",e);return "Départ Scav annulé : sauvegarde à vérifier.";}
    }
    static void started(ServerPlayer p){SupplyContract.account(p).putLong(NEXT,System.currentTimeMillis()+COOLDOWN);}
    static boolean friendly(ServerPlayer p){return active(p)&&!SupplyContract.account(p).getCompound(ESCROW).getBoolean("hostile");}
    static void betray(ServerPlayer p){
        if(!friendly(p))return;
        SupplyContract.account(p).getCompound(ESCROW).putBoolean("hostile",true);
        CampaignTraderAccount.applyReputation(p,fr.taczoperations.campaign.CampaignTrader.FENCE,-100);
        p.sendSystemMessage(Component.literal("Tu as attaqué un Scav. Les Scavs deviennent hostiles."));
    }
    static void markFinished(ServerPlayer p,boolean survived){
        if(!active(p))return;
        var saved=SupplyContract.account(p).getCompound(ESCROW);
        if(saved.contains("recovery_slots",9))return;
        saved.putBoolean("finished",true);saved.putBoolean("survived",survived);
    }
    static boolean recover(ServerPlayer p){
        if(!active(p))return true;
        if(!p.isAlive()||p.level().dimension().equals(ForestExpedition.FOREST))return false;
        try{
            p.closeContainer();
            if(!p.containerMenu.getCarried().isEmpty())return false;
            var account=SupplyContract.account(p);var saved=account.getCompound(ESCROW);
            // A command teleport or reconnect outside the raid can leave a
            // Scav starting-inventory snapshot behind. Never apply that
            // snapshot to restored PMC items on the next departure.
            if(ForestExpedition.hasReturnPoint(p)||ForestRaid.hasRaidToken(p)){
                ForestRaid.finishParticipant(p);
                ForestExpedition.clearReturnPoint(p);
                ForestRaid.clearPlayer(p);
                StoryMissions.clear(p);
                SupplyContract.abortRun(p);
                RaidLootValue.clear(p);
            }
            var handlers=handlers(p);var originals=decode(p,saved.getList("slots",10),handlers);
            var current=readSlots(p,handlers);
            boolean success=saved.getBoolean("finished")&&saved.getBoolean("survived");
            // Stage the loot once before any inventory mutation. A recovery retry
            // must never capture already restored PMC equipment as Scav loot.
            if(!saved.contains("recovery_slots",9)){
                var queue=account.getList(LOOT,10).copy();
                if(success){
                    for(var slot:current)if(!slot.stack().isEmpty())queue.add(slot.stack().saveOptional(p.registryAccess()));
                    if(queue.size()>LIMIT)throw new IllegalStateException("Scav loot reserve full");
                }
                saved.put("recovery_slots",encode(p,current));
                saved.put("recovery_loot",queue);
                saved.put("recovery_done",new CompoundTag());
            }
            var staged=decode(p,saved.getList("recovery_slots",10),handlers);
            var expected=new HashMap<String,ItemStack>();
            var targets=new HashMap<String,ItemStack>();
            for(var v:staged)expected.put(slotKey(v),v.stack());
            for(var v:originals)targets.put(slotKey(v),v.stack());
            var done=saved.getCompound("recovery_done");
            // If Curios changed its slot layout or inventory contents moved
            // during a partial recovery, retain escrow and stop without clearing.
            if(expected.size()!=current.size()||!expected.keySet().equals(targets.keySet()))
                throw new IllegalStateException("Scav recovery slot layout changed");
            for(var v:current){
                String k=slotKey(v);
                if(!targets.containsKey(k))throw new IllegalStateException("Unexpected Scav slot "+k);
                var target=targets.get(k);
                if(done.getBoolean(k)){
                    if(!ItemStack.matches(v.stack(),target))throw new IllegalStateException("Restored PMC slot changed "+k);
                }else if(!ItemStack.matches(v.stack(),expected.get(k))&&!ItemStack.matches(v.stack(),target))
                    throw new IllegalStateException("Scav recovery inventory changed "+k);
            }
            for(var v:originals){
                String k=slotKey(v);
                if(done.getBoolean(k))continue;
                set(p,handlers,v.handler(),v.slot(),v.stack().copy());
                var actual=v.handler().isEmpty()?p.getInventory().getItem(v.slot()):handlers.get(v.handler()).getStackInSlot(v.slot());
                if(!ItemStack.matches(actual,v.stack()))throw new IllegalStateException("PMC slot restoration rejected "+k);
                done.putBoolean(k,true);
                saved.put("recovery_done",done);
            }
            var finalSlots=readSlots(p,handlers(p));
            if(finalSlots.size()!=targets.size()||finalSlots.stream().anyMatch(v->!targets.containsKey(slotKey(v))||!ItemStack.matches(v.stack(),targets.get(slotKey(v)))))
                throw new IllegalStateException("PMC recovery final verification failed");
            var queue=saved.getList("recovery_loot",10).copy();
            p.getInventory().selected=Math.clamp(saved.getInt("selected"),0,8);
            if(success)account.put(LOOT,queue);
            boolean trusted=success&&!saved.getBoolean("hostile");
            account.remove(ESCROW);
            if(trusted){
                CampaignTraderAccount.applyReputation(p,fr.taczoperations.campaign.CampaignTrader.FENCE,100);
                p.sendSystemMessage(Component.literal("Fence · +0,10 réputation pour une extraction Scav sans trahison."));
            }
            p.getInventory().setChanged();
            p.sendSystemMessage(Component.literal(success?"Scav extrait · équipement PMC restauré. Récupère le butin au tableau.":"Scav terminé · équipement PMC restauré."));
            return true;
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Scav escrow retained for recovery",e);return false;}
    }
    static int pending(ServerPlayer p){return SupplyContract.account(p).getList(LOOT,10).size();}
    static String collect(ServerPlayer p){
        if(active(p)||!PlayerMarket.available(p)||ForestExpedition.hasReturnPoint(p)||p.isCreative())return "Récupération uniquement hors raid.";
        p.closeContainer();
        var account=SupplyContract.account(p);var queue=account.getList(LOOT,10);
        int delivered=0;
        for(int i=0;i<queue.size();){
            var stack=ItemStack.parseOptional(p.registryAccess(),queue.getCompound(i));
            if(stack.isEmpty()){i++;continue;}
            int before=stack.getCount();p.getInventory().add(stack);delivered+=before-stack.getCount();
            if(stack.isEmpty())queue.remove(i);
            else{queue.set(i,stack.saveOptional(p.registryAccess()));i++;}
        }
        account.put(LOOT,queue);p.getInventory().setChanged();
        return delivered+" objet(s) Scav récupéré(s) · "+queue.size()+" piles en réserve.";
    }
    public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){
        if(event.getEntity() instanceof ServerPlayer p&&p.tickCount%20==0&&active(p)&&p.isAlive()&&!p.level().dimension().equals(ForestExpedition.FOREST))recover(p);
    }
    private ScavRun(){}
}
