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
    static boolean active(ServerPlayer p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator()
            && p.level().dimension().equals(ForestExpedition.FOREST) && ForestRaid.isParticipant(p);
    }
    static void hit(ServerPlayer p,Entity bullet,boolean headshot,float damage) {
        if(!active(p)||damage<1.5F||!Float.isFinite(damage))return;
        var data=p.getPersistentData();
        long time=p.level().getGameTime();
        EquipmentSlot zone=BallisticVest.hitSlot(p,bullet,headshot);
        int region=switch(zone){
            case HEAD -> RaidInjuryPayload.HEAD;
            case LEGS -> RaidInjuryPayload.LEGS;
            case FEET -> RaidInjuryPayload.FEET;
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
            data.putBoolean(FRACTURE,true);p.sendSystemMessage(Component.literal("Fracture à la jambe : attelle requise."));
        }
        if(zone==EquipmentSlot.CHEST&&!headshot&&!data.getBoolean(ARM)&&damage>=3F&&random.nextFloat()<.12F){
            data.putBoolean(ARM,true);p.sendSystemMessage(Component.literal("Blessure au bras : kit de premiers secours requis."));
        }
        sync(p,true);
    }
    private static void sync(ServerPlayer p,boolean active){
        var d=p.getPersistentData();
        int hit=active&&d.contains(HIT_TIME)&&p.level().getGameTime()-d.getLong(HIT_TIME)<30?d.getInt(HIT_ZONE):0;
        int mask=active&&d.getInt(BLEED)>0?d.getInt(BLEED_FIRST)|d.getInt(BLEED_SECOND):0;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new RaidInjuryPayload(
            active,active?Math.clamp(d.getInt(BLEED),0,2):0,active&&d.getBoolean(FRACTURE),active&&d.getBoolean(ARM),active?RaidSurvival.water(p):100,active?RaidSurvival.energy(p):100,hit,mask));
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
            case "splint" -> {if(!d.getBoolean(FRACTURE))return false;d.remove(FRACTURE);}
            case "salewa" -> {
                if(p.getHealth()>=p.getMaxHealth())return false;
                p.heal(8F*(1F+PassiveSkills.bonus(p,PassiveSkills.Skill.MEDICINE)/100F));
            }
            case "medkit" -> {
                if(!d.getBoolean(ARM))return false;
                d.remove(ARM);
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
        d.remove(BLEED);d.remove(FRACTURE);d.remove(ARM);d.remove(LAST);d.remove(HIT_ZONE);d.remove(HIT_TIME);d.remove(BLEED_FIRST);d.remove(BLEED_SECOND);
        RaidSurvival.clear(p);
        sync(p,false);
    }
    private RaidInjuries(){}
}
