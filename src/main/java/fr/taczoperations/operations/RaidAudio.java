package fr.taczoperations.operations;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Vanilla sound assets for now: no extra audio dependency, no globally broadcast
// alerts. Gun sounds remain entirely under TaCZ control.
public final class RaidAudio {
    private static final String NEXT="efm_next_ambient";
    public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!p.isAlive()||p.isSpectator()||!ForestRaid.isParticipant(p)||p.tickCount%20!=0)return;
        long now=p.level().getGameTime();
        var data=p.getPersistentData();
        if(!data.contains(NEXT)){data.putLong(NEXT,now+400+p.getRandom().nextInt(401));return;}
        if(now<data.getLong(NEXT))return;
        data.putLong(NEXT,now+500+p.getRandom().nextInt(901));
        boolean industrial=fr.taczoperations.worldgen.IndustrialZone.inside(p.getBlockX(),p.getBlockZ());
        SoundEvent sound=industrial?SoundEvents.CHAIN_STEP:SoundEvents.GRASS_STEP;
        if(p.getRandom().nextInt(4)==0)sound=SoundEvents.AMBIENT_CAVE.value();
        privateSound(p,sound,industrial?.22F:.16F,.72F+p.getRandom().nextFloat()*.45F,SoundSource.AMBIENT);
    }
    static void privateSound(ServerPlayer p,SoundEvent sound,float volume,float pitch,SoundSource source){
        p.playNotifySound(sound,source,volume,pitch);
    }
    static void alert(ServerPlayer p){privateSound(p,SoundEvents.NOTE_BLOCK_PLING.value(),.45F,.65F,SoundSource.PLAYERS);}
    static void extract(ServerPlayer p){privateSound(p,SoundEvents.EXPERIENCE_ORB_PICKUP,.5F,.85F,SoundSource.PLAYERS);}
    static void search(ServerPlayer p){privateSound(p,SoundEvents.BUNDLE_INSERT,.23F,.85F,SoundSource.PLAYERS);}
    static void medicine(ServerPlayer p,String kind){
        SoundEvent sound=switch(kind){
            case "bandage","medkit"->SoundEvents.ARMOR_EQUIP_LEATHER.value();
            case "splint"->SoundEvents.WOODEN_BUTTON_CLICK_ON;
            case "salewa"->SoundEvents.HONEY_BOTTLE_DRINK;
            default->SoundEvents.BUNDLE_INSERT;
        };
        privateSound(p,sound,.35F,.9F,SoundSource.PLAYERS);
    }
    static void clear(ServerPlayer p){p.getPersistentData().remove(NEXT);}
    private RaidAudio(){}
}
