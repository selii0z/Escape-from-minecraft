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
    private static BlockPos center(int id){return new BlockPos((id%320)*SPACING+128,Y,(id/320)*SPACING+128);}
    private static boolean inside(ServerPlayer p,BlockPos c){
        return Math.abs(p.getX()-c.getX()-.5)<RADIUS-.5&&Math.abs(p.getZ()-c.getZ()-.5)<RADIUS-.5&&p.getY()>=Y&&p.getY()<Y+7;
    }
    private static boolean survival(ServerPlayer p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator();}
    private static boolean here(ServerPlayer p){return p.level().dimension().equals(DIMENSION);}
    private static void say(ServerPlayer p,String s){p.sendSystemMessage(Component.literal(s));}
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
    private static void go(ServerPlayer p){
        if(!survival(p)||!p.level().dimension().equals(Level.OVERWORLD)||ForestRaid.isParticipant(p)){say(p,"Accès à la planque depuis le monde principal, hors raid.");return;}
        var level=p.server.getLevel(DIMENSION);if(level==null){say(p,"Dimension planque indisponible.");return;}
        int id=HideoutPlots.of(level).allocate(p.getUUID());
        if(id<0){say(p,"Aucune parcelle disponible.");return;}
        BlockPos c=center(id);build(level,c);
        var back=new CompoundTag();back.putDouble("x",p.getX());back.putDouble("y",p.getY());back.putDouble("z",p.getZ());back.putFloat("yaw",p.getYRot());back.putFloat("pitch",p.getXRot());
        SupplyContract.account(p).put(RETURN,back);
        p.closeContainer();
        p.teleportTo(level,c.getX()+.5,Y,c.getZ()+.5,Set.of(),0,0);
        say(p,"Bienvenue dans ta planque personnelle. /efm planque installations · /efm planque sortir");
    }
    private static void leave(ServerPlayer p){
        if(!here(p))return;
        var level=p.server.overworld();var account=SupplyContract.account(p);var back=account.getCompound(RETURN);
        double x=back.getDouble("x"),y=back.getDouble("y"),z=back.getDouble("z");
        BlockPos target=BlockPos.containing(x,y,z);
        boolean safe=account.contains(RETURN)&&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&level.getWorldBorder().isWithinBounds(target)
            &&y>=level.getMinBuildHeight()&&y<level.getMaxBuildHeight()-2
            &&level.getBlockState(target).getCollisionShape(level,target).isEmpty()
            &&level.getBlockState(target.above()).getCollisionShape(level,target.above()).isEmpty()
            &&level.getBlockState(target.below()).isFaceSturdy(level,target.below(),net.minecraft.core.Direction.UP);
        if(!safe){target=level.getSharedSpawnPos();x=target.getX()+.5;y=target.getY()+1;z=target.getZ()+.5;}
        p.closeContainer();p.teleportTo(level,x,y,z,Set.of(),safe?back.getFloat("yaw"):0,safe?back.getFloat("pitch"):0);
        account.remove(RETURN);
    }
    private static CompoundTag stations(ServerPlayer p){
        var a=SupplyContract.account(p);if(!a.contains(STATIONS))a.put(STATIONS,new CompoundTag());return a.getCompound(STATIONS);
    }
    private static int tier(ServerPlayer p,Station s){return Math.clamp(stations(p).getInt(s.id),0,3);}
    private static void list(ServerPlayer p){
        say(p,"Installations de la planque · niveaux 0 à 3");
        for(var s:Station.values()){
            int t=tier(p,s);
            say(p,s.label+" : "+t+"/3"+(t==3?" · maximum":" · prochain niveau : "+s.price*(t+1)+" Cr., "+(t+1)+" × "+s.material.title+", niv. militaire "+(s.unlock+t*10)));
        }
    }
    private static void upgrade(ServerPlayer p,String id){
        if(!here(p)||!survival(p)){say(p,"Amélioration uniquement dans ta planque.");return;}
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
        say(p,s.label+" améliorée au niveau "+n+" (fonctionnalités propres à chaque station à venir).");
    }
    public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("efm").then(Commands.literal("planque")
            .executes(ctx->{go(ctx.getSource().getPlayerOrException());return 1;})
            .then(Commands.literal("sortir").executes(ctx->{leave(ctx.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("installations").executes(ctx->{list(ctx.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("ameliorer").then(Commands.argument("installation",StringArgumentType.word())
                .suggests((ctx,b)->{for(var s:Station.values())if(s.id.startsWith(b.getRemainingLowerCase()))b.suggest(s.id);return b.buildFuture();})
                .executes(ctx->{upgrade(ctx.getSource().getPlayerOrException(),StringArgumentType.getString(ctx,"installation"));return 1;})))));
    }
    public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!here(p))return;
        int id=HideoutPlots.of(p.serverLevel()).find(p.getUUID());
        if(id<0){leave(p);return;}
        if(!inside(p,center(id))){var c=center(id);p.teleportTo(p.serverLevel(),c.getX()+.5,Y,c.getZ()+.5,Set.of(),0,0);}
    }
    // First lot is read-only to avoid free construction resources or cross-plot damage.
    public static void protectBreak(BlockEvent.BreakEvent e){if(e.getLevel() instanceof ServerLevel l&&l.dimension().equals(DIMENSION))e.setCanceled(true);}
    public static void protectPlace(BlockEvent.EntityPlaceEvent e){if(e.getLevel() instanceof ServerLevel l&&l.dimension().equals(DIMENSION))e.setCanceled(true);}
    public static void protectExplosion(ExplosionEvent.Detonate e){if(e.getLevel().dimension().equals(DIMENSION))e.getAffectedBlocks().clear();}
    private Hideout(){}
}
