package fr.taczoperations.operations;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

// Vanilla sound assets for now: no extra audio dependency, no globally broadcast
// alerts. Gun sounds remain entirely under TaCZ control.
public final class RaidAudio {
    private static final String NEXT="efm_next_ambient"; // legacy player timer
    static void ambient(net.minecraft.server.level.ServerLevel level,java.util.List<ServerPlayer> players,
                        boolean industrial,RaidAtmosphere atmosphere,java.util.Random rng){
        if(players.isEmpty())return;
        ServerPlayer listener=players.get(rng.nextInt(players.size()));
        long now=level.getGameTime();
        boolean storm=atmosphere!=null&&atmosphere.thunder(now)>.3F;
        boolean rainy=atmosphere!=null&&atmosphere.rain(now)>.2F;
        boolean night=ForestExpedition.isNightRaid(listener);
        boolean indoor=!level.canSeeSky(listener.blockPosition().above());
        // Use vanilla environmental sounds, never gunshots or footsteps.
        String[] pool=industrial?(indoor
            ?new String[]{"block.chain.break","block.pointed_dripstone.drip_water","block.metal.hit"}
            :new String[]{"block.chain.break","block.metal.hit","block.bamboo.hit"})
            :(indoor?new String[]{"block.wood.hit","block.pointed_dripstone.drip_water","block.chain.break"}
            :night?new String[]{"block.grass.break","block.bamboo.hit","block.azalea_leaves.break"}
            :new String[]{"block.azalea_leaves.break","block.grass.break","block.bamboo.hit"});
        String sound=pool[rng.nextInt(pool.length)];
        boolean thunder=storm&&!indoor&&rng.nextInt(3)==0;
        if(thunder)sound="entity.lightning_bolt.thunder";
        else if(rainy&&indoor&&rng.nextBoolean())sound="block.pointed_dripstone.drip_water";
        var origin=listener.blockPosition();
        for(int attempt=0;attempt<6;attempt++){
            double a=rng.nextDouble()*Math.PI*2;
            int radius=thunder?18+rng.nextInt(9):6+rng.nextInt(9);
            var pos=origin.offset((int)Math.round(Math.cos(a)*radius),rng.nextInt(5)-2,(int)Math.round(Math.sin(a)*radius));
            if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos))continue;
            if(industrial?!fr.taczoperations.worldgen.IndustrialZone.inside(pos.getX(),pos.getZ())
                :!fr.taczoperations.worldgen.ForestPlan.inside(pos.getX(),pos.getZ()))continue;
            if(!thunder&&!level.getBlockState(pos).isAir())continue;
            if(!thunder&&level.canSeeSky(pos)==indoor)continue;
            var key=net.minecraft.resources.ResourceLocation.withDefaultNamespace(sound);
            if(!net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(key))return;
            var event=net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(key);
            if(event==null)return;
            // Identical positional source for nearby teammates, private packets
            // prevent another raid or spectators hearing this session's cue.
            float volume=thunder?.20F:indoor?.28F:.23F;
            float pitch=.75F+rng.nextFloat()*.4F;
            long soundSeed=rng.nextLong();
            for(var p:players)if(pos.distToCenterSqr(p.position())<=32*32){
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    net.minecraft.core.Holder.direct(event),SoundSource.AMBIENT,
                    pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,volume,pitch,soundSeed));
            }
            return;
        }
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
            case "salewa"->SoundEvents.GENERIC_DRINK;
            default->SoundEvents.BUNDLE_INSERT;
        };
        privateSound(p,sound,.35F,.9F,SoundSource.PLAYERS);
    }
    static void clear(ServerPlayer p){p.getPersistentData().remove(NEXT);}
    private RaidAudio(){}
}
