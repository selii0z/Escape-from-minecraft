package fr.taczoperations.operations;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import java.util.Set;

/** Private firing lane. Real ammo is used until an isolated training-ammo path is validated. */
public final class HideoutRange {
    private static final String OWNER="efm_range_owner",SHOTS="efm_range_shots",HITS="efm_range_hits",DIST="efm_range_distance";
    private static final String MOBILE="efm_range_mobile",HEADS="efm_range_heads",RECORDS="efm_range_records";
    private static final int[] DISTANCES={10,25,50,100};
    private static final int[] LANES={-3,-1,1,3};
    private static BlockPos origin(BlockPos plot){return plot.offset(0,0,32);}
    private static int count(int tier){return Math.min(4,tier+1);}
    private static int length(int tier){return DISTANCES[count(tier)-1]+4;}
    static boolean inside(ServerPlayer p,BlockPos plot){
        int tier=Hideout.tier(p,Hideout.Station.TIR);if(tier<=0)return false;
        var c=origin(plot);
        return p.getX()>c.getX()-3&&p.getX()<c.getX()+length(tier)
            &&Math.abs(p.getZ()-c.getZ()-.5)<5&&p.getY()>=c.getY()&&p.getY()<c.getY()+6;
    }
    private static BlockPos plot(ServerPlayer p){
        if(!p.level().dimension().equals(Hideout.DIMENSION)||!p.isAlive()||p.isCreative()||p.isSpectator())return null;
        int id=HideoutPlots.of(p.serverLevel()).find(p.getUUID());
        return id<0?null:Hideout.center(id);
    }
    static boolean active(ServerPlayer p){var c=plot(p);return c!=null&&inside(p,c);}
    static void toggle(ServerPlayer p){
        var c=plot(p);if(c==null||p.containerMenu!=p.inventoryMenu)return;
        if(inside(p,c)){report(p);p.teleportTo(p.serverLevel(),c.getX()+.5,c.getY(),c.getZ()+.5,Set.of(),0,0);return;}
        if(!Hideout.ownsPlot(p))return;
        int tier=Hideout.tier(p,Hideout.Station.TIR);
        if(tier==0){Hideout.say(p,"Stand de tir niveau 1 requis.");return;}
        var level=p.serverLevel();var o=origin(c);build(level,o,tier);
        for(int i=0;i<count(tier);i++)target(level,p,o,i);
        p.getPersistentData().putInt(SHOTS,0);p.getPersistentData().putInt(HITS,0);p.getPersistentData().putInt(HEADS,0);
        for(int distance:DISTANCES)p.getPersistentData().remove(HITS+"_"+distance);
        p.teleportTo(level,o.getX()+.5,o.getY(),o.getZ()+.5,Set.of(),-90,0);
        Hideout.say(p,"Stand privé · munitions réelles, pas d'usure EFM ni d'XP. /efm planque tir pour revenir.");
    }
    private static void build(ServerLevel level,BlockPos o,int tier){
        if(level.getBlockState(o.below(2)).is(Blocks.OBSIDIAN)&&level.getBlockState(o.offset(length(tier),0,0)).is(Blocks.STONE_BRICKS))return;
        int end=length(tier);
        for(int x=-4;x<=end;x++)for(int z=-5;z<=5;z++){
            level.setBlock(o.offset(x,-1,z),(x==0?Blocks.YELLOW_CONCRETE:Blocks.SMOOTH_STONE).defaultBlockState(),3);
            level.setBlock(o.offset(x,5,z),Blocks.DEEPSLATE_TILES.defaultBlockState(),3);
            for(int y=0;y<5;y++){
                if(x==-4||x==end||Math.abs(z)==5)level.setBlock(o.offset(x,y,z),Blocks.STONE_BRICKS.defaultBlockState(),3);
                else if(level.getBlockState(o.offset(x,y,z)).is(Blocks.STONE_BRICKS))level.setBlock(o.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
            }
        }
        for(int x=0;x<end;x+=8)level.setBlock(o.offset(x,4,0),Blocks.SEA_LANTERN.defaultBlockState(),3);
        for(int i=0;i<count(tier);i++)level.setBlock(o.offset(DISTANCES[i],-1,LANES[i]),Blocks.RED_CONCRETE.defaultBlockState(),3);
        level.setBlock(o.below(2),Blocks.OBSIDIAN.defaultBlockState(),3);
    }
    private static void target(ServerLevel level,ServerPlayer p,BlockPos o,int i){
        var pos=o.offset(DISTANCES[i],0,LANES[i]);level.getChunkAt(pos);
        for(var stand:level.getEntitiesOfClass(ArmorStand.class,new AABB(pos).inflate(2),
            e->e.getPersistentData().hasUUID(OWNER)&&e.getPersistentData().getUUID(OWNER).equals(p.getUUID())&&e.getPersistentData().getInt(DIST)==DISTANCES[i]))stand.discard();
        var stand=EntityType.ARMOR_STAND.create(level);if(stand==null)return;
        stand.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,90,0);
        stand.setNoGravity(true);stand.setInvulnerable(true);
        stand.setCustomName(Component.literal("Cible · "+DISTANCES[i]+" m"));stand.setCustomNameVisible(true);
        stand.getPersistentData().putUUID(OWNER,p.getUUID());stand.getPersistentData().putInt(DIST,DISTANCES[i]);
        level.addFreshEntity(stand);
    }
    static void mobile(ServerPlayer p){
        if(!active(p)||Hideout.tier(p,Hideout.Station.TIR)<2){Hideout.say(p,"Cibles mobiles : stand niveau 2 requis.");return;}
        var a=SupplyContract.account(p);boolean next=!a.getBoolean(MOBILE);a.putBoolean(MOBILE,next);
        Hideout.say(p,next?"Cibles mobiles activées.":"Cibles fixes activées.");
    }
    public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p)||p.tickCount%4!=0)return;
        var c=plot(p);if(c==null||!inside(p,c))return;
        int tier=Hideout.tier(p,Hideout.Station.TIR);
        if(tier<2)return;
        var level=p.serverLevel();var o=origin(c);
        boolean mobile=SupplyContract.account(p).getBoolean(MOBILE);
        for(int i=2;i<count(tier);i++){
            var base=o.offset(DISTANCES[i],0,LANES[i]);
            if(!level.hasChunkAt(base))continue;
            final int distance=DISTANCES[i];
            for(var stand:level.getEntitiesOfClass(ArmorStand.class,new AABB(base).inflate(5),
                e->e.getPersistentData().hasUUID(OWNER)&&e.getPersistentData().getUUID(OWNER).equals(p.getUUID())&&e.getPersistentData().getInt(DIST)==distance)){
                double z=mobile?o.getZ()+.5+Math.sin((level.getGameTime()%2000)/(distance==100?13.0:21.0))*3.3:base.getZ()+.5;
                stand.moveTo(base.getX()+.5,base.getY(),z,90,0);
            }
        }
    }
    public static void shoot(GunShootEvent e){
        if(e.getLogicalSide().isClient()||!(e.getShooter() instanceof ServerPlayer p)||!p.level().dimension().equals(Hideout.DIMENSION))return;
        if(!active(p))e.setCanceled(true);
    }
    public static void fire(GunFireEvent e){
        if(e.getLogicalSide().isClient()||e.isCanceled()||!(e.getShooter() instanceof ServerPlayer p)||!p.level().dimension().equals(Hideout.DIMENSION))return;
        if(!active(p)){e.setCanceled(true);return;}
        var data=p.getPersistentData();data.putInt(SHOTS,Math.min(1000000,data.getInt(SHOTS)+1));
    }
    public static void hit(EntityHurtByGunEvent.Pre e){
        if(e.getLogicalSide().isClient()||!(e.getHurtEntity() instanceof ArmorStand stand)||!stand.getPersistentData().hasUUID(OWNER))return;
        e.setCanceled(true);
        if(!(e.getAttacker() instanceof ServerPlayer p)||!active(p)||!stand.getPersistentData().getUUID(OWNER).equals(p.getUUID()))return;
        var d=p.getPersistentData();int hits=Math.min(1000000,d.getInt(HITS)+1);d.putInt(HITS,hits);
        int distance=stand.getPersistentData().getInt(DIST);
        d.putInt(HITS+"_"+distance,Math.min(1000000,d.getInt(HITS+"_"+distance)+1));
        if(e.isHeadShot())d.putInt(HEADS,Math.min(1000000,d.getInt(HEADS)+1));
        p.displayClientMessage(Component.literal("Cible "+distance+" m · "+(e.isHeadShot()?"Tête":"Touché")+" · "+hits+" impacts"),true);
    }
    public static void interact(PlayerInteractEvent.EntityInteract e){
        if(e.getTarget() instanceof ArmorStand stand&&stand.getPersistentData().hasUUID(OWNER))e.setCanceled(true);
    }
    public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific e){
        if(e.getTarget() instanceof ArmorStand stand&&stand.getPersistentData().hasUUID(OWNER))e.setCanceled(true);
    }
    static void report(ServerPlayer p){
        var d=p.getPersistentData();int shots=d.getInt(SHOTS),hits=d.getInt(HITS),heads=d.getInt(HEADS);
        Hideout.say(p,"Entraînement : "+shots+" tirs · "+hits+" impacts · "+heads+" impacts tête.");
        var a=SupplyContract.account(p);var records=a.getCompound(RECORDS);
        for(int distance:DISTANCES){
            String key=HITS+"_"+distance;int n=d.getInt(key);
            if(n>0)Hideout.say(p,distance+" m : "+n+" impacts.");
            records.putInt(key,Math.max(n,records.getInt(key)));d.remove(key);
        }
        records.putInt("hits",Math.max(hits,records.getInt("hits")));
        records.putInt("heads",Math.max(heads,records.getInt("heads")));
        if(shots>0)records.putInt("sessions",Math.min(Integer.MAX_VALUE-1,Math.max(0,records.getInt("sessions")))+1);
        a.put(RECORDS,records);d.remove(SHOTS);d.remove(HITS);d.remove(HEADS);
    }
    static void records(ServerPlayer p){
        var r=SupplyContract.account(p).getCompound(RECORDS);
        Hideout.say(p,"Stand · "+r.getInt("sessions")+" séances · record impacts "+r.getInt("hits")+" · record tête "+r.getInt("heads"));
        for(int distance:DISTANCES)Hideout.say(p,distance+" m · record "+r.getInt(HITS+"_"+distance)+" impacts.");
    }
    private HideoutRange(){}
}
