package fr.taczoperations.operations;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Lifetime EFM counters only; wipe removes the account compound. */
public final class DetailedStats {
    private static final java.util.Map<ServerPlayer,Vec3> LAST=new java.util.WeakHashMap<>();
    private static void add(ServerPlayer p,String key,long amount){
        if(!ForestRaid.isParticipant(p)||amount<=0)return;
        var totals=SupplyContract.raidTotals(p);
        long old=Math.max(0,totals.getLong(key));
        totals.putLong(key,old+Math.min(Long.MAX_VALUE-old,amount));
    }
    static void kill(ServerPlayer p,GuardRole role){
        add(p,role==GuardRole.PMC?"pmc_kills":role.boss()?"boss_kills":"scav_kills",1);
    }
    public static void fire(com.tacz.guns.api.event.common.GunFireEvent e){
        if(e.getLogicalSide().isClient()||e.isCanceled()||!(e.getShooter() instanceof ServerPlayer p))return;
        add(p,"shots",1);
    }
    static void hit(com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre e){
        if(e.getLogicalSide().isClient()||!(e.getAttacker() instanceof ServerPlayer p)
            ||!(e.getHurtEntity() instanceof net.minecraft.world.entity.monster.Pillager guard)
            ||!(guard.level() instanceof net.minecraft.server.level.ServerLevel level)
            ||!ForestRaid.sameSession(level,guard,p))return;
        add(p,"impacts",1);
        if(e.isHeadShot())add(p,"headshots",1);
    }
    public static void tick(PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        if(!ForestRaid.isParticipant(p)||!p.isAlive()){LAST.remove(p);return;}
        if(p.tickCount%5!=0)return;
        Vec3 now=p.position(),last=LAST.put(p,now);
        if(last==null)return;
        double distance=Math.hypot(now.x-last.x,now.z-last.z);
        if(Double.isFinite(distance)&&distance<=8)add(p,"distance_cm",Math.round(distance*100));
    }
    private DetailedStats(){}
}
