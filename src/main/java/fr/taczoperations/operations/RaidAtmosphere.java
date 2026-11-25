package fr.taczoperations.operations;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Random;

/** Weather is visual and private to the raid. No mutation of world weather,
 * time, blocks or other sessions. Day/night remains the player's choice. */
final class RaidAtmosphere {
    private static final int TRANSITION=400;
    private final Random rng;
    private final long started;
    private long next,transitionStart;
    private float fromRain,fromThunder;
    private int target;
    private int changes;
    RaidAtmosphere(long seed,long now,int initial){
        rng=new Random(seed^0x7a4cbdb932L);
        started=now;
        target=Math.clamp(initial,0,2);
        fromRain=rainFor(target);
        fromThunder=thunderFor(target);
        transitionStart=now-TRANSITION;
        next=now+20L*(150+rng.nextInt(151));
    }
    private static float rainFor(int weather){return weather==0?0:weather==1?.65F:1F;}
    private static float thunderFor(int weather){return weather==2?.8F:0;}
    private float blend(long now){return Math.clamp((now-transitionStart)/(float)TRANSITION,0F,1F);}
    float rain(long now){return fromRain+(rainFor(target)-fromRain)*blend(now);}
    float thunder(long now){return fromThunder+(thunderFor(target)-fromThunder)*blend(now);}
    long skyTime(boolean night,long now){
        // Gradual daylight variation but never turn a selected night raid
        // into a day raid or vice versa.
        long elapsed=Math.max(0,now-started);
        return (night?16000:3500)+Math.min(night?4500:6500,elapsed/2);
    }
    void tick(ServerLevel level,List<ServerPlayer> players){
        long now=level.getGameTime();
        if(now<next||changes>=2||players.isEmpty())return;
        fromRain=rain(now);fromThunder=thunder(now);
        target=target==0?1:target==2?1:rng.nextBoolean()?0:2;
        transitionStart=now;
        changes++;
        next=now+20L*(150+rng.nextInt(151));
        String description=switch(target){case 0->"Le ciel se dégage.";case 1->"La pluie change d'intensité.";default->"L'orage approche.";};
        for(var p:players)p.displayClientMessage(Component.literal(description),true);
    }
}
