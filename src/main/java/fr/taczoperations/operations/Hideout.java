package fr.taczoperations.operations;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import java.util.Set;

@net.neoforged.fml.common.EventBusSubscriber(modid="taczoperations")
public final class Hideout {
    public static final ResourceKey<Level> DIMENSION=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath("taczoperations","hideout"));
    private static final int SPACING=256,Y=64,RADIUS=15;
    private static final String RETURN="hideout_return",STATIONS="hideout_stations";
    enum Station {
        RESERVE("reserve","Réserve",TradeLoot.PARACORD,10,500),
        INFIRMERIE("infirmerie","Infirmerie",TradeLoot.MEDICAL_TOOLS,12,750),
        ATELIER("atelier","Atelier",TradeLoot.TOOL_SET,15,1000),
        GENERATEUR("generateur","Générateur",TradeLoot.FUEL_CAN,15,1000),
        CUISINE("cuisine","Cuisine",TradeLoot.TEA_BOX,8,400),
        EAU("eau","Collecteur d'eau",TradeLoot.WATER_FILTER,10,600),
        REPOS("repos","Salle de repos",TradeLoot.RIPSTOP_FABRIC,5,350),
        TIR("tir","Stand de tir",TradeLoot.MASTER_TOOLS,20,1500);
        final String id,label;final TradeLoot material;final int unlock,price;
        Station(String id,String label,TradeLoot material,int unlock,int price){this.id=id;this.label=label;this.material=material;this.unlock=unlock;this.price=price;}
    }
    static BlockPos center(int id){return new BlockPos((id%320)*SPACING+128,Y,(id/320)*SPACING+128);}
    private static boolean inside(ServerPlayer p,BlockPos c){
        return Math.abs(p.getX()-c.getX()-.5)<RADIUS-.5&&Math.abs(p.getZ()-c.getZ()-.5)<RADIUS-.5&&p.getY()>=Y&&p.getY()<Y+7;
    }
    private static boolean survival(ServerPlayer p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator();}
    private static boolean here(ServerPlayer p){return p.level().dimension().equals(DIMENSION);}
    static void say(ServerPlayer p,String s){p.sendSystemMessage(Component.literal(s));}
    private static void block(ServerLevel l,BlockPos pos,Block b){l.setBlock(pos,b.defaultBlockState(),3);}
    private static void build(ServerLevel l,BlockPos c){
        // A marker is placed last. No generated containers or obtainable loot.
        if(l.getBlockState(c.below(2)).is(Blocks.OBSIDIAN))return;
        for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++){
            block(l,c.offset(x,-1,z),(x%5==0||z%5==0)?Blocks.POLISHED_ANDESITE:Blocks.SMOOTH_STONE);
            block(l,c.offset(x,6,z),Blocks.DEEPSLATE_TILES);
            for(int y=0;y<6;y++){
                boolean edge=Math.abs(x)==RADIUS||Math.abs(z)==RADIUS;
                if(edge)block(l,c.offset(x,y,z),y==1?Blocks.POLISHED_DEEPSLATE:Blocks.STONE_BRICKS);
            }
        }
        for(int x=-10;x<=10;x+=5)for(int z=-10;z<=10;z+=5)block(l,c.offset(x,5,z),Blocks.SEA_LANTERN);
        // Eight distinct bays; functional menus arrive in later lots.
        Block[] props={Blocks.IRON_BLOCK,Blocks.WHITE_CONCRETE,Blocks.SMITHING_TABLE,Blocks.COPPER_BLOCK,Blocks.FURNACE,Blocks.CAULDRON,Blocks.GREEN_WOOL,Blocks.TARGET};
        for(int i=0;i<props.length;i++){
            int x=-10+(i%4)*6,z=i<4?-11:11;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)block(l,c.offset(x+dx,-1,z+dz),Blocks.POLISHED_BLACKSTONE);
            block(l,c.offset(x,0,z),props[i]);
            block(l,c.offset(x-1,0,z),Blocks.IRON_BARS);
        }
        block(l,c.below(2),Blocks.OBSIDIAN);
    }
    // Retired feature: keep the dimension registered solely for old world/player data.
    static void fromBoard(ServerPlayer p){say(p,"La planque a été supprimée.");}
    static void leave(ServerPlayer p){
        if(!here(p)||!p.isAlive())return;
        var level=p.server.overworld();var account=SupplyContract.account(p);
        var back=account.getCompound(RETURN);
        var destination=legacyDestination(p,level);
        boolean saved=destination!=null;
        if(!saved)destination=net.minecraft.world.phys.Vec3.atBottomCenterOf(p.adjustSpawnLocation(level,level.getSharedSpawnPos()));
        p.closeContainer();
        p.teleportTo(level,destination.x,destination.y,destination.z,Set.of(),saved?back.getFloat("yaw"):0,saved?back.getFloat("pitch"):0);
        if(p.level()==level){
            account.remove(RETURN);
            say(p,"La planque a été supprimée. Retour dans le monde principal.");
            if(account.contains("hideout_stash",9)&&!account.getList("hideout_stash",10).isEmpty())
                say(p,"Tes anciens objets sont conservés : /efm recuperer_reserve");
        }
    }
    static net.minecraft.world.phys.Vec3 legacyDestination(ServerPlayer p,ServerLevel level){
        var account=SupplyContract.account(p);var back=account.getCompound(RETURN);
        if(!account.contains(RETURN,10)||!back.contains("x",99)||!back.contains("y",99)||!back.contains("z",99))return null;
        double x=back.getDouble("x"),y=back.getDouble("y"),z=back.getDouble("z");
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||y<level.getMinBuildHeight()||y>=level.getMaxBuildHeight()-2)return null;
        var pos=new net.minecraft.world.phys.Vec3(x,y,z);var target=BlockPos.containing(pos);
        if(!level.getWorldBorder().isWithinBounds(target)||!level.noCollision(p,p.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(pos))
            ||!level.getFluidState(target).isEmpty()||!level.getFluidState(target.above()).isEmpty()
            ||!level.getBlockState(target.below()).isFaceSturdy(level,target.below(),net.minecraft.core.Direction.UP))return null;
        return pos;
    }
    static void clearLegacyReturn(ServerPlayer p){SupplyContract.account(p).remove(RETURN);}
    private static CompoundTag stations(ServerPlayer p){
        var a=SupplyContract.account(p);if(!a.contains(STATIONS))a.put(STATIONS,new CompoundTag());return a.getCompound(STATIONS);
    }
    static int tier(ServerPlayer p,Station s){return Math.clamp(stations(p).getInt(s.id),0,3);}
    static boolean ownsPlot(ServerPlayer p){return false;}
    private static void list(ServerPlayer p){
        say(p,"Installations de la planque · niveaux 0 à 3");
        HideoutPower.status(p);
        for(var s:Station.values()){
            int t=tier(p,s);
            say(p,s.label+" : "+t+"/3"+(t==3?" · maximum":" · prochain niveau : "+s.price*(t+1)+" Cr., "+(t+1)+" × "+s.material.title+", niv. militaire "+(s.unlock+t*10)));
        }
    }
    static void upgrade(ServerPlayer p,String id){
        if(!ownsPlot(p)||p.containerMenu!=p.inventoryMenu){say(p,"Amélioration uniquement dans ta planque, menu fermé.");return;}
        var level=p.serverLevel();int plot=HideoutPlots.of(level).find(p.getUUID());
        if(plot<0||!inside(p,center(plot)))return;
        Station s=null;for(var candidate:Station.values())if(candidate.id.equals(id))s=candidate;
        if(s==null){say(p,"Installation inconnue.");return;}
        int old=tier(p,s);if(old>=3){say(p,"Niveau maximal atteint.");return;}
        int n=old+1,cost=s.price*n,required=s.unlock+old*10;
        if(MilitaryProgression.level(SupplyContract.experienceEarned(p))<required){say(p,"Niveau militaire "+required+" requis.");return;}
        int balance=SupplyContract.balance(p);if(balance<cost){say(p,"Crédits insuffisants.");return;}
        var item=s.material.stack().getItem();var storage=CarriedStorage.of(p);
        if(!storage.consume(stack->stack.is(item)&&RaidOrigin.found(stack),n)){say(p,"Il faut "+n+" × "+s.material.title+" trouvé en raid.");return;}
        SupplyContract.account(p).putInt("credits",balance-cost);stations(p).putInt(s.id,n);
        HideoutDecor.refresh(p,center(plot));
        say(p,s.label+" améliorée au niveau "+n+".");
    }
    public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("efm").then(Commands.literal("recuperer_reserve")
            .executes(ctx->HideoutServices.recoverLegacyStash(ctx.getSource().getPlayerOrException()))));
    }
    public static void tick(PlayerTickEvent.Post event){
        if(event.getEntity() instanceof ServerPlayer p&&here(p)&&p.isAlive())leave(p);
    }
    public static void interact(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!here(p))return;
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);e.setCanceled(true);
        if(e.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND||!ownsPlot(p))return;
        int id=HideoutPlots.of(p.serverLevel()).find(p.getUUID());
        BlockPos c=center(id);
        for(int i=0;i<Station.values().length;i++){
            int x=-10+(i%4)*6,z=i<4?-11:11;
            if(e.getPos().equals(c.offset(x,0,z))||e.getPos().equals(c.offset(x,3,z))){
                HideoutMenu.open(p);return;
            }
        }
    }
    // First lot is read-only to avoid free construction resources or cross-plot damage.
    public static void protectBreak(BlockEvent.BreakEvent e){if(e.getLevel() instanceof ServerLevel l&&l.dimension().equals(DIMENSION))e.setCanceled(true);}
    public static void protectPlace(BlockEvent.EntityPlaceEvent e){if(e.getLevel() instanceof ServerLevel l&&l.dimension().equals(DIMENSION))e.setCanceled(true);}
    public static void protectExplosion(ExplosionEvent.Detonate e){if(e.getLevel() instanceof ServerLevel l&&l.dimension().equals(DIMENSION))e.getAffectedBlocks().clear();}
    @net.neoforged.bus.api.SubscribeEvent
    public static void protectPlayer(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event){
        if(event.getEntity() instanceof ServerPlayer p&&here(p)
            &&!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
            &&!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL))event.setCanceled(true);
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void preventMobEntry(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){
        if(event.getLevel() instanceof ServerLevel level&&level.dimension().equals(DIMENSION)
            &&event.getEntity() instanceof net.minecraft.world.entity.Mob)event.setCanceled(true);
    }
    @net.neoforged.bus.api.SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void preserveAccount(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event){
        if(!event.isWasDeath()||!(event.getOriginal() instanceof ServerPlayer old)
            ||!(event.getEntity() instanceof ServerPlayer p)||!here(old))return;
        var previous=old.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if(!previous.contains("taczoperations",10))return;
        var root=p.getPersistentData();var persisted=root.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        // Do not replace an account already transferred by vanilla/NeoForge.
        if(!persisted.contains("taczoperations",10)){
            persisted.put("taczoperations",previous.getCompound("taczoperations").copy());
            root.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG,persisted);
        }
    }
    private Hideout(){}
}
