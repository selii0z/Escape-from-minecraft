package fr.taczoperations.operations;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Pillager;
import java.util.UUID;
import java.util.WeakHashMap;

/** In-game TaCZ events, server only; qualified kills are not inferred from generic death counts. */
public final class CampaignGunEvents {
    private record Impact(UUID shooter,ResourceLocation weapon,long tick,String part,double distance){}
    private static final WeakHashMap<Pillager,Impact> LAST=new WeakHashMap<>();
    /** Explicit stock TaCZ IDs only; no guessing from display names or attachments. */
    static boolean matchesWeapon(String group,ResourceLocation id){
        if(group.equals("any"))return true;
        if(id==null||!id.getNamespace().equals("tacz"))return false;
        String gun=id.getPath();
        return switch(group){
            case "bolt"->gun.equals("ai_awp");
            case "pistol"->gun.equals("glock_17");
            case "ak"->gun.equals("ak47");
            case "western"->gun.equals("m4a1");
            case "rifle"->java.util.Set.of("ak47","m4a1").contains(gun);
            case "smg"->gun.equals("hk_mp5a5");
            case "shotgun"->gun.equals("m870");
            case "lmg"->gun.equals("m249");
            default->false;
        };
    }
    private static boolean eligible(ServerPlayer p,Pillager guard,ServerLevel level){
        return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!ScavRun.active(p)
            &&ForestRaid.isParticipant(p)&&ForestRaid.currentGuard(level,guard)&&ForestRaid.sameSession(level,guard,p);
    }
    public static void hit(EntityHurtByGunEvent.Pre event){
        if(event.getLogicalSide().isClient()||event.isCanceled()||!(event.getBaseAmount()>0)
            ||!(event.getAttacker() instanceof ServerPlayer p)||!(event.getHurtEntity() instanceof Pillager guard)
            ||!(guard.level() instanceof ServerLevel level)||!eligible(p,guard,level))return;
        var slot=BallisticVest.hitSlot(guard,event.getBullet(),event.isHeadShot());
        String part=event.isHeadShot()?"head":slot==EquipmentSlot.LEGS||slot==EquipmentSlot.FEET?"legs":"body";
        var id=event.getGunId();
        var impact=new Impact(p.getUUID(),id,level.getGameTime(),part,p.distanceTo(guard));
        LAST.put(guard,impact);
        CampaignRuntime.gunEvent(p,ForestRaid.campaignRaidId(p),ForestRaid.campaignMap(p),GuardRole.get(guard),"hit",part,id,impact.distance());
    }
    public static void kill(EntityKillByGunEvent event){
        if(event.getLogicalSide().isClient()||!(event.getAttacker() instanceof ServerPlayer p)
            ||!(event.getKilledEntity() instanceof Pillager guard)||!(guard.level() instanceof ServerLevel level)
            ||!eligible(p,guard,level))return;
        var impact=LAST.remove(guard);
        if(impact==null||!impact.shooter().equals(p.getUUID())||!java.util.Objects.equals(impact.weapon(),event.getGunId())
            ||level.getGameTime()-impact.tick()>1)return;
        CampaignRuntime.gunEvent(p,ForestRaid.campaignRaidId(p),ForestRaid.campaignMap(p),GuardRole.get(guard),"kill",impact.part(),impact.weapon(),impact.distance());
    }
    private CampaignGunEvents(){}
}
