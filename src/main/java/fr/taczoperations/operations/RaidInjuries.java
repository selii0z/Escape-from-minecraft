package fr.taczoperations.operations;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class RaidInjuries {
    private static final String BLEED="efm_bleed", FRACTURE="efm_fracture", ARM="efm_arm_injury", LAST="efm_injury_tick";
    private static final String HIT_ZONE="efm_hit_zone",HIT_TIME="efm_hit_time",BLEED_FIRST="efm_bleed_first",BLEED_SECOND="efm_bleed_second";
    private static final String FRACTURE_ZONE="efm_fracture_zone",ARM_ZONE="efm_arm_zone";
    static boolean active(ServerPlayer p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator()
            && p.level().dimension().equals(ForestExpedition.FOREST) && ForestRaid.canContinue(p);
    }
    static void hit(ServerPlayer p,Entity bullet,boolean headshot,float damage) {
        if(!active(p)||damage<1.5F||!Float.isFinite(damage))return;
        var data=p.getPersistentData();
        long time=p.level().getGameTime();
        EquipmentSlot zone=BallisticVest.hitSlot(p,bullet,headshot);
        int side=impactSide(p,bullet);
        int region=switch(zone){
            case HEAD -> RaidInjuryPayload.HEAD;
            case LEGS -> legRegion(side);
            case FEET -> side>0?RaidInjuryPayload.LEFT_FOOT:side<0?RaidInjuryPayload.RIGHT_FOOT:RaidInjuryPayload.FEET;
            default -> RaidInjuryPayload.TORSO;
        };
        data.putInt(HIT_ZONE,region);data.putLong(HIT_TIME,time);
        if(data.contains(LAST)&&time-data.getLong(LAST)<12){sync(p,true);return;}
        data.putLong(LAST,time);
        var random=p.getRandom();
        if(random.nextFloat()<Math.min(.45F,damage*.045F)) {
            int old=data.getInt(BLEED);
            data.putInt(BLEED,Math.min(2,old+1));
            if(old==0)data.putInt(BLEED_FIRST,region);
            if(old==1)data.putInt(BLEED_SECOND,region);
            if(old<2)p.sendSystemMessage(Component.literal(old==0?"Blessure : saignement. Utilise un bandage.":"Saignement important : deux bandages ou un kit hémostatique."));
        }
        if((zone==EquipmentSlot.LEGS||zone==EquipmentSlot.FEET)&&!data.getBoolean(FRACTURE)&&damage>=2.5F&&random.nextFloat()<.22F){
            data.putBoolean(FRACTURE,true);data.putInt(FRACTURE_ZONE,legRegion(side));p.sendSystemMessage(Component.literal("Fracture à la jambe : attelle requise."));
        }
        if(zone==EquipmentSlot.CHEST&&!headshot&&!data.getBoolean(ARM)&&damage>=3F&&random.nextFloat()<.12F){
            data.putBoolean(ARM,true);data.putInt(ARM_ZONE,side>0?RaidInjuryPayload.LEFT_ARM:side<0?RaidInjuryPayload.RIGHT_ARM:RaidInjuryPayload.ARMS);p.sendSystemMessage(Component.literal("Blessure au bras : kit de premiers secours requis."));
        }
        sync(p,true);
    }
    // Vanilla exposes one bounding box, not anatomical hitboxes. Estimate
    // the lateral half in player-relative space only with a usable impact.
    // Ambiguous central hits deliberately retain the grouped indication.
    private static int impactSide(ServerPlayer p,Entity bullet){
        if(bullet==null)return 0;
        var box=p.getBoundingBox();
        var start=bullet.position();
        var hit=box.contains(start)?java.util.Optional.of(start):box.clip(start,start.add(bullet.getDeltaMovement()));
        if(hit.isEmpty())return 0;
        double yaw=Math.toRadians(p.getYRot());
        var point=hit.get();
        double lateral=(point.x-(box.minX+box.maxX)*.5)*Math.cos(yaw)
            +(point.z-(box.minZ+box.maxZ)*.5)*Math.sin(yaw);
        return lateral>.09?1:lateral<-.09?-1:0;
    }
    private static int legRegion(int side){
        return side>0?RaidInjuryPayload.LEFT_LEG:side<0?RaidInjuryPayload.RIGHT_LEG:RaidInjuryPayload.LEGS;
    }
    private static void sync(ServerPlayer p,boolean active){
        var d=p.getPersistentData();
        int hit=active&&d.contains(HIT_TIME)&&p.level().getGameTime()-d.getLong(HIT_TIME)<30?d.getInt(HIT_ZONE):0;
        int mask=active&&d.getInt(BLEED)>0?d.getInt(BLEED_FIRST)|d.getInt(BLEED_SECOND):0;
        CarriedStorage storage=active?CarriedStorage.of(p):null;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new RaidInjuryPayload(
            active,active?Math.clamp(d.getInt(BLEED),0,2):0,active&&d.getBoolean(FRACTURE),active&&d.getBoolean(ARM),active?RaidSurvival.water(p):100,active?RaidSurvival.energy(p):100,hit,mask,
            active&&d.getBoolean(FRACTURE)?(d.contains(FRACTURE_ZONE)?d.getInt(FRACTURE_ZONE):RaidInjuryPayload.LEGS):0,
            active&&d.getBoolean(ARM)?(d.contains(ARM_ZONE)?d.getInt(ARM_ZONE):RaidInjuryPayload.ARMS):0,
            medicalCount(storage,"bandage"),medicalCount(storage,"hemostat"),medicalCount(storage,"splint"),
            medicalCount(storage,"salewa"),medicalCount(storage,"medkit")));
    }
    private static int medicalCount(CarriedStorage storage,String kind){
        return storage==null?0:Math.min(99,storage.count(s->s.getItem() instanceof FieldMedicine med&&kind.equals(med.kind())));
    }
    public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer p)||!active(p)||p.tickCount%20!=0)return;
        RaidSurvival.second(p);
        var d=p.getPersistentData();
        if(d.getBoolean(FRACTURE))p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,0,false,false,true));
        if(d.getBoolean(ARM))p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,40,0,false,false,true));
        int bleed=d.getInt(BLEED);
        if(bleed>0&&p.tickCount%(bleed==2?80:160)==0)p.hurt(p.damageSources().magic(),1F);
        if(p.isAlive())sync(p,true);
    }
    static boolean canTreat(ServerPlayer p,String kind){
        if(!active(p))return false;
        var d=p.getPersistentData();
        return switch(kind){
            case "bandage","hemostat"->d.getInt(BLEED)>0;
            case "splint"->d.getBoolean(FRACTURE);
            case "salewa"->p.getHealth()<p.getMaxHealth();
            case "medkit"->d.getBoolean(ARM);
            default->false;
        };
    }
    static boolean treat(ServerPlayer p,String kind) {
        if(!canTreat(p,kind))return false;
        var d=p.getPersistentData();
        switch(kind) {
            case "bandage" -> {
                int bleed=d.getInt(BLEED);
                if(bleed<=0)return false;
                if(bleed==1){d.remove(BLEED);d.remove(BLEED_FIRST);d.remove(BLEED_SECOND);}
                else {d.putInt(BLEED,bleed-1);d.remove(BLEED_SECOND);}
            }
            case "hemostat" -> {if(d.getInt(BLEED)<=0)return false;d.remove(BLEED);d.remove(BLEED_FIRST);d.remove(BLEED_SECOND);}
            case "splint" -> {
                if(!d.getBoolean(FRACTURE))return false;
                d.remove(FRACTURE);d.remove(FRACTURE_ZONE);
                // Existing short-duration slowness expires naturally.
                // Never remove effects supplied by other mods.
            }
            case "salewa" -> {
                if(p.getHealth()>=p.getMaxHealth())return false;
                p.heal(8F*(1F+PassiveSkills.bonus(p,PassiveSkills.Skill.MEDICINE)/100F));
            }
            case "medkit" -> {
                if(!d.getBoolean(ARM))return false;
                d.remove(ARM);d.remove(ARM_ZONE);
            }
            default -> {return false;}
        }
        sync(p,true);
        PassiveSkills.medicine(p);
        p.sendSystemMessage(Component.literal("Soin appliqué."));
        return true;
    }
    static void clear(ServerPlayer p) {
        var d=p.getPersistentData();
        d.remove(BLEED);d.remove(FRACTURE);d.remove(ARM);d.remove(LAST);d.remove(HIT_ZONE);d.remove(HIT_TIME);d.remove(BLEED_FIRST);d.remove(BLEED_SECOND);d.remove(FRACTURE_ZONE);d.remove(ARM_ZONE);
        RaidSurvival.clear(p);
        sync(p,false);
    }
    private RaidInjuries(){}
}
