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
    static boolean active(ServerPlayer p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator()
            && p.level().dimension().equals(ForestExpedition.FOREST) && ForestRaid.isParticipant(p);
    }
    static void hit(ServerPlayer p,Entity bullet,boolean headshot,float damage) {
        if(!active(p)||damage<1.5F||!Float.isFinite(damage))return;
        var data=p.getPersistentData();
        long time=p.level().getGameTime();
        if(data.contains(LAST)&&time-data.getLong(LAST)<12)return;
        data.putLong(LAST,time);
        var random=p.getRandom();
        EquipmentSlot zone=BallisticVest.hitSlot(p,bullet,headshot);
        if(random.nextFloat()<Math.min(.45F,damage*.045F)) {
            int old=data.getInt(BLEED);
            data.putInt(BLEED,Math.min(2,old+1));
            if(old<2)p.sendSystemMessage(Component.literal(old==0?"Blessure : saignement. Utilise un bandage.":"Saignement important : kit hémostatique requis."));
        }
        if((zone==EquipmentSlot.LEGS||zone==EquipmentSlot.FEET)&&!data.getBoolean(FRACTURE)&&damage>=2.5F&&random.nextFloat()<.22F){
            data.putBoolean(FRACTURE,true);p.sendSystemMessage(Component.literal("Fracture à la jambe : attelle requise."));
        }
        if(zone==EquipmentSlot.CHEST&&!headshot&&!data.getBoolean(ARM)&&damage>=3F&&random.nextFloat()<.12F){
            data.putBoolean(ARM,true);p.sendSystemMessage(Component.literal("Blessure au bras : kit de premiers secours requis."));
        }
    }
    public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer p)||!active(p)||p.tickCount%20!=0)return;
        var d=p.getPersistentData();
        if(d.getBoolean(FRACTURE))p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,0,false,false,true));
        if(d.getBoolean(ARM))p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,40,0,false,false,true));
        int bleed=d.getInt(BLEED);
        if(bleed>0&&p.tickCount%(bleed==2?80:160)==0)p.hurt(p.damageSources().magic(),1F);
        if((bleed>0||d.getBoolean(FRACTURE)||d.getBoolean(ARM))&&p.tickCount%60==0)
            p.displayClientMessage(Component.literal((bleed==2?"Hémorragie  ":bleed==1?"Saignement  ":"")+(d.getBoolean(FRACTURE)?"Fracture  ":"")+(d.getBoolean(ARM)?"Bras blessé":"")),true);
    }
    static boolean treat(ServerPlayer p,String kind) {
        if(!active(p))return false;
        var d=p.getPersistentData();
        switch(kind) {
            case "bandage" -> {if(d.getInt(BLEED)!=1)return false;d.remove(BLEED);}
            case "hemostat" -> {if(d.getInt(BLEED)<=0)return false;d.remove(BLEED);}
            case "splint" -> {if(!d.getBoolean(FRACTURE))return false;d.remove(FRACTURE);}
            case "medkit" -> {
                if(!d.getBoolean(ARM)&&p.getHealth()>=p.getMaxHealth())return false;
                d.remove(ARM);p.heal(6F);
            }
            default -> {return false;}
        }
        p.sendSystemMessage(Component.literal("Soin appliqué."));
        return true;
    }
    static void clear(ServerPlayer p) {
        var d=p.getPersistentData();
        d.remove(BLEED);d.remove(FRACTURE);d.remove(ARM);d.remove(LAST);
    }
    private RaidInjuries(){}
}
