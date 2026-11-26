package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.worldgen.ForestChunkGenerator;
import fr.taczoperations.worldgen.ForestPlan;
import fr.taczoperations.worldgen.IndustrialZone;
import fr.taczoperations.worldgen.ExpressZone;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;
import java.util.UUID;

public final class ForestExpedition {
    public static final ResourceKey<Level> FOREST = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(OperationsMod.ID, "black_forest"));
    private static final String RETURN_POINT = "taczoperations_forest_return";
    private static final String RAID_INVENTORY = "taczoperations_raid_inventory";
    private static final int EXTRACTION_TICKS=100;
    private record ExtractionHold(BlockPos exit,int start,int last){}
    private static final java.util.Map<ServerPlayer,ExtractionHold> EXTRACTION_HOLDS=new java.util.WeakHashMap<>();

    private static void sendExtraction(ServerPlayer p,boolean active,int elapsed){
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
            new ExtractionProgressPayload(active,Math.clamp(elapsed,0,EXTRACTION_TICKS)));
    }
    private static void cancelExtraction(ServerPlayer p,boolean notify){
        if(EXTRACTION_HOLDS.remove(p)==null)return;
        sendExtraction(p,false,0);
        if(notify)p.displayClientMessage(Component.literal("Extraction annulée"),true);
    }
    private static boolean inExtraction(BlockPos feet,BlockPos center){return fr.taczoperations.worldgen.ExtractionLayout.contains(feet.getX()-center.getX(),feet.getY()-center.getY(),feet.getZ()-center.getZ());}
    private static boolean extractionReady(ServerPlayer p,BlockPos exit){
        int now=p.tickCount;
        var hold=EXTRACTION_HOLDS.get(p);
        if(hold==null||!exit.equals(hold.exit())||now!=hold.last()+1){
            hold=new ExtractionHold(exit.immutable(),now,now);
        }else{
            hold=new ExtractionHold(hold.exit(),hold.start(),now);
        }
        EXTRACTION_HOLDS.put(p,hold);
        int elapsed=Math.max(0,now-hold.start());
        if(elapsed>=EXTRACTION_TICKS)return true;
        if(elapsed%5==0)sendExtraction(p,true,elapsed);
        return false;
    }

    private ForestExpedition() {
    }

    static void clearReturnPoint(ServerPlayer player) {
        cancelExtraction(player,false);
        player.getPersistentData().remove(RETURN_POINT);
        player.getPersistentData().remove(RAID_INVENTORY);
    }

    private static void snapshotInventory(ServerPlayer player) {
        ListTag items = new ListTag();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", slot);
            entry.put("item", player.getInventory().getItem(slot).saveOptional(player.registryAccess()));
            items.add(entry);
        }
        // Nested pack contents and the dedicated Curios are counted for
        // interrupted recovery, but never restored into a native slot.
        for(int i=0;i<player.getInventory().getContainerSize();i++)
            appendPackContents(items,player,player.getInventory().getItem(i));
        for(String id:new String[]{FieldInventoryMenu.GOGGLES,FieldInventoryMenu.BAG,FieldInventoryMenu.RIG}){
            var stack=FieldInventoryMenu.current(FieldInventoryMenu.handler(player,id));
            appendRecoveryItem(items,player,stack);
            appendPackContents(items,player,stack);
        }
        player.getPersistentData().put(RAID_INVENTORY, items);
    }

    private static void appendRecoveryItem(ListTag items,ServerPlayer p,ItemStack stack){
        if(stack.isEmpty())return;
        CompoundTag entry=new CompoundTag();entry.putInt("slot",-1);
        entry.put("item",stack.saveOptional(p.registryAccess()));items.add(entry);
    }
    private static void appendPackContents(ListTag items,ServerPlayer p,ItemStack stack){
        for(var content:FieldPack.contents(stack))appendRecoveryItem(items,p,content);
    }

    // A full snapshot is safe only before the session starts (failed duo
    // teleport). After gameplay, another player may already own a dropped
    // starting item; recreating it from NBT would duplicate that item.
    private static void recoverInterruptedInventory(ServerPlayer player){
        var data=player.getPersistentData();
        if(!data.contains(RAID_INVENTORY,9))return;
        if(!data.contains("taczoperations_run_started")&&!ForestRaid.hasRaidToken(player)){
            restoreInventory(player);
            return;
        }
        var allowance=new java.util.HashMap<String,Integer>();
        var snapshot=data.getList(RAID_INVENTORY,10);
        for(int i=0;i<snapshot.size();i++){
            var item=ItemStack.parseOptional(player.registryAccess(),snapshot.getCompound(i).getCompound("item"));
            if(!item.isEmpty())allowance.merge(recoveryKey(item),item.getCount(),Integer::sum);
        }
        // Keep only currently held starting quantities; never create stacks.
        // The secure case remains protected, including its current contents.
        var inventory=player.getInventory();
        for(int i=0;i<inventory.getContainerSize();i++){
            var item=inventory.getItem(i);
            if(item.isEmpty()||item.getItem() instanceof SecureCaseItem)continue;
            String key=recoveryKey(item);
            int keep=Math.min(item.getCount(),allowance.getOrDefault(key,0));
            allowance.put(key,allowance.getOrDefault(key,0)-keep);
            if(keep==0)inventory.setItem(i,ItemStack.EMPTY);
            else if(keep<item.getCount())item.setCount(keep);
        }
        // Curios gear and pack contents share the same quantity allowance.
        for(String id:new String[]{FieldInventoryMenu.GOGGLES,FieldInventoryMenu.BAG,FieldInventoryMenu.RIG}){
            var handler=FieldInventoryMenu.handler(player,id);
            var item=FieldInventoryMenu.current(handler);
            if(item.isEmpty())continue;
            String key=recoveryKey(item);
            int keep=Math.min(item.getCount(),allowance.getOrDefault(key,0));
            allowance.put(key,allowance.getOrDefault(key,0)-keep);
            if(keep==0)handler.setStackInSlot(0,ItemStack.EMPTY);
        }
        for(int i=0;i<inventory.getContainerSize();i++)trimPack(inventory.getItem(i),allowance);
        for(String id:new String[]{FieldInventoryMenu.BAG,FieldInventoryMenu.RIG})
            trimPack(FieldInventoryMenu.current(FieldInventoryMenu.handler(player,id)),allowance);
        inventory.setChanged();
    }
    private static void trimPack(ItemStack carrier,java.util.Map<String,Integer> allowance){
        if(!(carrier.getItem() instanceof FieldPack))return;
        var contents=FieldPack.contents(carrier);
        for(int i=0;i<contents.size();i++){
            var stack=contents.get(i);
            if(stack.isEmpty())continue;
            String key=recoveryKey(stack);
            int keep=Math.min(stack.getCount(),allowance.getOrDefault(key,0));
            allowance.put(key,allowance.getOrDefault(key,0)-keep);
            if(keep==0)contents.set(i,ItemStack.EMPTY);
            else if(keep<stack.getCount())stack.setCount(keep);
        }
        FieldPack.replaceContents(carrier,contents);
    }
    private static String recoveryKey(ItemStack item){
        if(item.getItem() instanceof com.tacz.guns.api.item.IGun gun)return "gun/"+gun.getGunId(item);
        if(item.getItem() instanceof com.tacz.guns.api.item.IAmmo ammo)return "ammo/"+ammo.getAmmoId(item);
        if(item.getItem() instanceof com.tacz.guns.api.item.IAttachment attachment)return "attachment/"+attachment.getAttachmentId(item);
        return "item/"+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem());
    }

    private static void restoreInventory(ServerPlayer player) {
        if (!player.getPersistentData().contains(RAID_INVENTORY, 9)) return;
        ListTag items = player.getPersistentData().getList(RAID_INVENTORY, 10);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            int slot = entry.getInt("slot");
            if (slot >= 0 && slot < player.getInventory().getContainerSize()) {
                player.getInventory().setItem(slot,
                    ItemStack.parseOptional(player.registryAccess(), entry.getCompound("item")));
            }
        }
        player.getInventory().setChanged();
    }

    static int initialWeather(ServerPlayer player){
        return player.getPersistentData().getCompound(RETURN_POINT).getInt("weather");
    }

    static boolean isNightRaid(ServerPlayer player) {
        return player.getPersistentData().getCompound(RETURN_POINT).getBoolean("night");
    }

    static boolean hasReturnPoint(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(RETURN_POINT, 10)) return false;
        CompoundTag point = data.getCompound(RETURN_POINT);
        ResourceLocation dimension = ResourceLocation.tryParse(point.getString("dimension"));
        return dimension != null && !dimension.equals(FOREST.location())
            && point.contains("x", 99) && point.contains("y", 99) && point.contains("z", 99)
            && Double.isFinite(point.getDouble("x"))
            && Double.isFinite(point.getDouble("y"))
            && Double.isFinite(point.getDouble("z"));
    }

    // Read-only eligibility check before opening or charging an insurance selection.
    static String departureProblem(ServerPlayer player,BlockPos board,boolean duo,boolean industrial){return departureProblem(player,board,duo,industrial,null);}
    private static boolean insideMap(int x,int z,boolean industrial,ExpressZone express){return express!=null?express.inside(x,z):industrial?IndustrialZone.inside(x,z):ForestPlan.inside(x,z);}
    static String departureProblem(ServerPlayer player,BlockPos board,boolean duo,boolean industrial,ExpressZone express){
        if(ScavRun.active(player))return "Récupération du matériel PMC en cours.";
        if(express!=null&&!express.available())return "Les ressources de cette carte sont absentes du serveur.";
        ServerLevel forest=player.server.getLevel(FOREST);
        if(forest==null||!(forest.getChunkSource().getGenerator() instanceof ForestChunkGenerator))
            return "La dimension Forêt Noire est indisponible.";
        if(player.level()==forest||!canDepart(player))return "Tu dois être à pied, vivant et hors raid.";
        if(!player.level().isLoaded(board)||!player.level().getBlockState(board).is(OperationsMod.BOARD.get())
            ||board.distToCenterSqr(player.position())>64)return "Le tableau des opérations est indisponible.";
        if(duo){
            UUID id=OperationsGroups.of(player).partner(player);
            ServerPlayer partner=id==null?null:player.server.getPlayerList().getPlayer(id);
            if(partner==null||partner.level()!=player.level()||!canDepart(partner)
                ||board.distToCenterSqr(partner.position())>64
                ||!player.getUUID().equals(OperationsGroups.of(partner).partner(partner)))
                return "Ton équipier doit être à pied près du tableau.";
        }
        if(ForestRaid.hasActiveSession(forest,industrial,express)||forest.players().stream().anyMatch(other->!other.isSpectator()
            &&insideMap(other.getBlockX(),other.getBlockZ(),industrial,express)))
            return "Une équipe occupe déjà cette carte.";
        return "";
    }

    static String depart(ServerPlayer player, BlockPos board, boolean night, boolean duo, boolean industrial){return depart(player,board,night,duo,industrial,null);}
    static String depart(ServerPlayer player, BlockPos board, boolean night, boolean duo, boolean industrial,ExpressZone express) {
        String problem=departureProblem(player,board,duo,industrial,express);
        if(!problem.isEmpty())return problem;
        ServerLevel forest = player.server.getLevel(FOREST);
        if (forest == null || !(forest.getChunkSource().getGenerator() instanceof ForestChunkGenerator)) {
            return "La dimension Forêt Noire est indisponible.";
        }
        if (player.level() == forest) {
            return "Tu es déjà dans la Forêt Noire.";
        }
        if (!canDepart(player)) {
            return "Tu dois être à pied et vivant.";
        }
        // A player outside the raid cannot be an active participant. Recover
        // stale return data left by a death, command teleport or failed exit;
        // otherwise the player would be permanently blocked from departing.
        clearStaleDeparture(player);

        UUID partnerId = duo ? OperationsGroups.of(player).partner(player) : null;
        if (duo && partnerId == null) {
            return "Invite d’abord un équipier.";
        }
        ServerPlayer partner = partnerId == null ? null : player.server.getPlayerList().getPlayer(partnerId);
        if (partnerId != null && (partner == null || partner.level() != player.level()
            || board.distToCenterSqr(partner.position()) > 64 || !canDepart(partner))) {
            return "Ton équipier doit être à pied près du tableau. Quitte le duo pour partir seul.";
        }
        if (partner != null) {
            clearStaleDeparture(partner);
        }

        if(ForestRaid.hasActiveSession(forest,industrial,express)
            ||forest.players().stream().anyMatch(other->!other.isSpectator()&&
                insideMap(other.getBlockX(),other.getBlockZ(),industrial,express))){
            return "Une équipe occupe déjà cette carte (joueurs déconnectés inclus).";
        }
        var random = forest.getRandom();
        var entry = express!=null?express.pos(express.entry()):industrial ? industrialPosition(IndustrialZone.ENTRY)
            : ForestLandmarks.position(ForestPlan.ENTRIES.get(random.nextInt(ForestPlan.ENTRIES.size())));
        int weatherRoll=random.nextInt(100);
        int weather=weatherRoll<50?0:weatherRoll<85?1:2;
        if (!enter(player, forest, entry, night, weather, industrial,express)) {
            return "Le départ a été annulé.";
        }
        if (partner != null && !enter(partner, forest, entry.south(), night, weather, industrial,express)) {
            returnToOrigin(player);
            player.sendSystemMessage(Component.literal("Départ annulé : ton équipier n’a pas pu entrer."));
            return "";
        }
        ForestRaid.begin(forest, player, partner, industrial,express);
        return "";
    }

    static String departScav(ServerPlayer player,BlockPos board,boolean night,boolean industrial,ExpressZone express){
        String problem=departureProblem(player,board,false,industrial,express);
        if(!problem.isEmpty())return problem;
        problem=ScavRun.problem(player);if(!problem.isEmpty())return problem;
        clearStaleDeparture(player);
        var forest=player.server.getLevel(FOREST);
        if(forest==null)return "Dimension indisponible.";
        var random=forest.getRandom();
        var entry=express!=null?express.pos(express.entry()):industrial?industrialPosition(IndustrialZone.ENTRY):
            ForestLandmarks.position(ForestPlan.ENTRIES.get(random.nextInt(ForestPlan.ENTRIES.size())));
        int roll=random.nextInt(100),weather=roll<50?0:roll<85?1:2;
        problem=ScavRun.prepare(player);
        if(!problem.isEmpty()){if(ScavRun.active(player))ScavRun.recover(player);return problem;}
        if(!enter(player,forest,entry,night,weather,industrial,express)){
            ScavRun.markFinished(player,false);ScavRun.recover(player);return "Départ Scav annulé.";
        }
        ForestRaid.begin(forest,player,null,industrial,express);
        ScavRun.started(player);
        player.sendSystemMessage(Component.literal("Raid Scav · équipement prêté · Scavs neutres tant que tu ne les attaques pas. Prochain départ dans 45 minutes."));
        return "";
    }

    static BlockPos industrialPosition(ForestPlan.Point point) {
        return new BlockPos(IndustrialZone.ORIGIN + point.x(), IndustrialZone.FLOOR + 1, IndustrialZone.ORIGIN + point.z());
    }

    private static void clearStaleDeparture(ServerPlayer player) {
        if (player.level().dimension().equals(FOREST)) {
            return;
        }
        recoverInterruptedInventory(player);
        SupplyContract.abortRun(player);
        clearReturnPoint(player);
        ForestRaid.clearPlayer(player);
    }

    private static boolean canDepart(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isSleeping() && !ScavRun.active(player);
    }

    private static boolean enter(ServerPlayer player, ServerLevel forest, BlockPos destination, boolean night, int weather, boolean industrial,ExpressZone express) {
        CompoundTag point = new CompoundTag();
        point.putString("dimension", player.level().dimension().location().toString());
        point.putDouble("x", player.getX());
        point.putDouble("y", player.getY());
        point.putDouble("z", player.getZ());
        point.putFloat("yaw", player.getYRot());
        point.putFloat("pitch", player.getXRot());
        point.putBoolean("night", night);
        point.putInt("weather", weather);
        point.putBoolean("industrial", industrial);
        point.putInt("express",express==null?0:express.id);
        player.getPersistentData().put(RETURN_POINT, point);
        player.closeContainer();
        snapshotInventory(player);
        if(express!=null)forest.getChunk(destination);
        Vec3 spawn = Vec3.atBottomCenterOf(destination);
        if (!standingRoom(player, forest, spawn)) {
            spawn = safeSpawn(player, forest, destination);
        }
        player.teleportTo(forest, spawn.x, spawn.y, spawn.z, Set.of(), -90, 0);
        if (player.level() != forest) {
            clearReturnPoint(player);
            return false;
        }
        atmosphere(player, point);
        player.sendSystemMessage(Component.literal(express!=null?express.title+" · "+express.minutes()+" minutes, deux sorties libres.":industrial ? "Zone industrielle · sortie principale gratuite et sorties secondaires selon la session."
            : "Forêt Noire · checkpoint d’extraction principal. Relais secondaire selon la session."));
        if(industrial){
            BlockPos key=industrialPosition(IndustrialZone.KEY_EXIT),paid=industrialPosition(IndustrialZone.PAID_EXIT);
            player.sendSystemMessage(Component.literal("Sortie à clé · X "+key.getX()+" Z "+key.getZ()+" | Sortie payante · X "+paid.getX()+" Z "+paid.getZ()));
        }
        return true;
    }

    private static int exitDistance(ServerPlayer p,BlockPos pos){
        return (int)Math.min(Integer.MAX_VALUE,Math.round(Math.hypot(p.getX()-pos.getX()-.5,p.getZ()-pos.getZ()-.5)));
    }
    private static void syncExits(ServerPlayer p,boolean industrial,boolean relayAvailable,boolean relay,BlockPos primary,BlockPos secondary){
        boolean visible=SupplyContract.showExtraction(p);
        int second=relay?RaidExitPayload.OPEN:relayAvailable?RaidExitPayload.RELAY: RaidExitPayload.CLOSED;
        int third=RaidExitPayload.HIDDEN;
        BlockPos secondPos=secondary,thirdPos=primary;
        if(industrial){
            var key=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"industrial_key"));
            second= !ForestRaid.secondaryOpen(p,1)?RaidExitPayload.CLOSED:
                CarriedStorage.of(p).count(stack->stack.is(key))>0?RaidExitPayload.OPEN:RaidExitPayload.KEY;
            third= !ForestRaid.secondaryOpen(p,2)?RaidExitPayload.CLOSED:
                SupplyContract.balance(p)>=IndustrialZone.PAID_EXIT_COST?RaidExitPayload.OPEN:RaidExitPayload.CREDITS;
            secondPos=industrialPosition(IndustrialZone.KEY_EXIT);
            thirdPos=industrialPosition(IndustrialZone.PAID_EXIT);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new RaidExitPayload(
            visible,industrial,exitDistance(p,primary),second,exitDistance(p,secondPos),third,
            exitDistance(p,thirdPos),industrial?IndustrialZone.PAID_EXIT_COST:0));
    }
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()
            || !player.level().dimension().equals(FOREST)) {
            return;
        }
        // A player without the current raid token must not remain in the
        // protected dimension (including after a server restart or stale login).
        if (!ForestRaid.canContinue(player)) {
            returnToOrigin(player, false);
            return;
        }
        CompoundTag point = player.getPersistentData().getCompound(RETURN_POINT);
        boolean industrial = point.getBoolean("industrial");
        ExpressZone express=ExpressZone.byId(point.getInt("express"));
        boolean inside=insideMap(player.getBlockX(),player.getBlockZ(),industrial,express);
        if (!inside || player.getY() < 1
            || player.getY() >= player.level().getMaxBuildHeight()) {
            returnToOrigin(player);
            return;
        }
        ForestRaid.populateNear(player);
        BlockPos primary = express!=null?express.pos(express.exitA()):industrial ? industrialPosition(IndustrialZone.EXIT) : ForestLandmarks.position(ForestPlan.EXTRACTION);
        BlockPos secondary = express!=null?express.pos(express.exitB()):industrial ? primary : ForestLandmarks.position(ForestPlan.CONDITIONAL_EXIT);
        boolean relayAvailable=express!=null||!industrial&&ForestRaid.secondaryOpen(player,0);
        boolean relay = express!=null || relayAvailable && point.getBoolean("relay");
        BlockPos feet = player.blockPosition();
        if(express==null&&player.tickCount%20==0)ExtractionRenovation.near(player,industrial);
        if(player.tickCount%20==0)syncExits(player,industrial,relayAvailable,relay,primary,secondary);
        if(industrial && !ScavRun.active(player) && !player.isSpectator() && player.onGround()){
            BlockPos keyExit=industrialPosition(IndustrialZone.KEY_EXIT);
            BlockPos paidExit=industrialPosition(IndustrialZone.PAID_EXIT);
            boolean key=inExtraction(feet,keyExit);
            boolean paid=inExtraction(feet,paidExit);
            if(key||paid){
                if(!ForestRaid.secondaryOpen(player,key?1:2)){
                    cancelExtraction(player,true);
                    if(player.tickCount%20==0)player.displayClientMessage(Component.literal("Sortie fermée pour ce raid."),true);
                    return;
                }
                var keyItem=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"industrial_key"));
                boolean eligible=key?CarriedStorage.of(player).count(s->s.is(keyItem))>0
                    :SupplyContract.balance(player)>=IndustrialZone.PAID_EXIT_COST;
                if(!eligible){
                    cancelExtraction(player,true);
                    if(player.tickCount%20==0)player.displayClientMessage(Component.literal(key?"Extraction verrouillée · clé industrielle requise.":"Extraction payante · "+IndustrialZone.PAID_EXIT_COST+" crédits requis."),true);
                    return;
                }
                if(!extractionReady(player,key?keyExit:paidExit))return;
                // The server thread is synchronous. Charge only after a confirmed dimension change.
                if(!ForestRaid.canContinue(player)){returnToOrigin(player,false);return;}
                if(returnToOrigin(player,true)){
                    if(key)SupplyContract.consume(player,keyItem,1);
                    else SupplyContract.account(player).putInt("credits",SupplyContract.balance(player)-IndustrialZone.PAID_EXIT_COST);
                    player.sendSystemMessage(Component.literal(key?"Extraction sécurisée · clé consommée.":"Extraction payante · -"+IndustrialZone.PAID_EXIT_COST+" crédits."));
                    SupplyContract.extracted(player,true);
                }else cancelExtraction(player,true);
                return;
            }
        }
        if (!player.isSpectator() && player.onGround()
            && (inExtraction(feet,primary) || (relay && inExtraction(feet,secondary)))) {
            if(!extractionReady(player,inExtraction(feet,primary)?primary:secondary))return;
            if(!ForestRaid.canContinue(player)){returnToOrigin(player,false);return;}
            if (returnToOrigin(player, true)) {
                SupplyContract.extracted(player, industrial);
            }else cancelExtraction(player,true);
            return;
        }
        cancelExtraction(player,true);
        if (player.tickCount % 20 == 0) {
            if (!industrial && inExtraction(feet,secondary) && !relay) {
                player.displayClientMessage(Component.literal(relayAvailable?"Sortie verrouillée · active le relais de la station.":"Sortie fermée pour ce raid."), true);
                return;
            }
            if (!SupplyContract.showExtraction(player)) {
                return;
            }
            BlockPos extraction = relay && secondary.distSqr(feet) < primary.distSqr(feet) ? secondary : primary;
            String exitLabel="Extraction";
            if(industrial){
                BlockPos keyExit=industrialPosition(IndustrialZone.KEY_EXIT),paidExit=industrialPosition(IndustrialZone.PAID_EXIT);
                var keyItem=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"industrial_key"));
                if(ForestRaid.secondaryOpen(player,1)&&CarriedStorage.of(player).count(s->s.is(keyItem))>0 && keyExit.distSqr(feet)<extraction.distSqr(feet)){
                    extraction=keyExit;exitLabel="Extraction (clé)";
                }
                if(ForestRaid.secondaryOpen(player,2)&&SupplyContract.balance(player)>=IndustrialZone.PAID_EXIT_COST && paidExit.distSqr(feet)<extraction.distSqr(feet)){
                    extraction=paidExit;exitLabel="Extraction ("+IndustrialZone.PAID_EXIT_COST+" cr.)";
                }
            }
            double distance = Math.hypot(player.getX() - extraction.getX() - .5, player.getZ() - extraction.getZ() - .5);
            player.displayClientMessage(Component.literal(exitLabel+" · X " + extraction.getX()
                + "  Y " + extraction.getY() + "  Z " + extraction.getZ() + " · "
                + Math.round(distance) + " m"), true);
        }
    }

    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.level().dimension().equals(FOREST)
            || player.isSpectator() || !ForestRaid.isParticipant(player)) {
            return;
        }
        BlockPos relay = ForestLandmarks.position(ForestPlan.RELAY);
        if (!event.getPos().equals(relay) && !event.getPos().equals(relay.above())) {
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if(!ForestRaid.secondaryOpen(player,0)){
            player.sendSystemMessage(Component.literal("Relais indisponible pour ce raid."));
            return;
        }
        player.getPersistentData().getCompound(RETURN_POINT).putBoolean("relay", true);
        BlockPos exit = ForestLandmarks.position(ForestPlan.CONDITIONAL_EXIT);
        player.sendSystemMessage(Component.literal("Relais activé. Sortie secondaire ouverte pour toi · X "
            + exit.getX() + " Z " + exit.getZ() + "."));
    }

    public static void synchronizeAtmosphere(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.getPersistentData().contains(RETURN_POINT)) {
                atmosphere(player, player.getPersistentData().getCompound(RETURN_POINT));
            }
        }
    }

    private static void atmosphere(ServerPlayer player, CompoundTag point) {
        long now=player.level().getGameTime();
        RaidAtmosphere session=ForestRaid.atmosphere(player);
        boolean night=point.getBoolean("night");
        player.connection.send(new ClientboundSetTimePacket(now,
            session==null?(night?16000:3500):session.skyTime(night,now),false));
        int weather=point.getInt("weather");
        float rain=session==null?(weather==0?0:weather==1?.65F:1F):session.rain(now);
        float thunder=session==null?(weather==2?.8F:0):session.thunder(now);
        if (rain <= .001F) {
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0));
        } else {
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
        }
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE,
            rain));
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE,
            thunder));
    }

    private static boolean returnToOrigin(ServerPlayer player) {
        return returnToOrigin(player, false);
    }

    private static boolean returnToOrigin(ServerPlayer player, boolean extracted) {
        ServerLevel raidLevel = player.serverLevel();
        boolean leavingRaid = raidLevel.dimension().equals(FOREST);
        CompoundTag point = player.getPersistentData().getCompound(RETURN_POINT);
        ResourceLocation key = ResourceLocation.tryParse(point.getString("dimension"));
        ServerLevel origin = key == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
        Vec3 destination = new Vec3(point.getDouble("x"), point.getDouble("y"), point.getDouble("z"));
        // A missing or corrupted return point must never send a player to
        // arbitrary coordinates or back into the raid dimension.
        if (!hasReturnPoint(player) || origin == null || origin == player.level()
            || origin.dimension().equals(FOREST) || !standingRoom(player, origin, destination)) {
            origin = player.server.overworld();
            destination = safeSpawn(player, origin, origin.getSharedSpawnPos());
        }
        // Return carried cursor items before changing dimension / restoring snapshot.
        player.closeContainer();
        if(player.isUsingItem()&&player.getUseItem().getItem() instanceof FieldMedicine)player.stopUsingItem();
        player.teleportTo(origin, destination.x, destination.y, destination.z, Set.of(),
            point.getFloat("yaw"), point.getFloat("pitch"));
        if (player.level() != origin) {
            return false;
        }
        if (leavingRaid) {
            RaidInjuries.clear(player);
            RaidAudio.clear(player);
            if(extracted)RaidAudio.extract(player);
            ForestRaid.campaignEnd(player,extracted);
            ForestRaid.finishParticipant(player);
        }
        boolean scav=ScavRun.active(player);
        if(scav){
            ScavRun.markFinished(player,extracted);
            StoryMissions.clear(player);SupplyContract.abortRun(player);
        }else if (!extracted) {
            StoryMissions.clear(player);
            recoverInterruptedInventory(player);
            if(leavingRaid)SupplyContract.interrupted(player,point.getBoolean("industrial"));
            SupplyContract.abortRun(player);
        }
        clearReturnPoint(player);
        ForestRaid.clearPlayer(player);
        if(scav)ScavRun.recover(player);
        if (leavingRaid) {
            ForestRaid.cleanupWhenEmpty(raidLevel);
        }
        return true;
    }

    private static Vec3 safeSpawn(ServerPlayer player, ServerLevel level, BlockPos preferred) {
        return Vec3.atBottomCenterOf(player.adjustSpawnLocation(level, preferred));
    }

    private static boolean standingRoom(ServerPlayer player, ServerLevel level, Vec3 position) {
        BlockPos feet = BlockPos.containing(position);
        return level.getWorldBorder().isWithinBounds(feet) && !level.isOutsideBuildHeight(feet)
            && level.noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(position))
            && level.getFluidState(feet).isEmpty()
            && level.getBlockState(feet.below()).isSolidRender(level, feet.below());
    }
}
