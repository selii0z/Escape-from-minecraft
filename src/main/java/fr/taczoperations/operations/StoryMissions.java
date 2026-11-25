package fr.taczoperations.operations;

import fr.taczoperations.worldgen.ForestPlan;
import fr.taczoperations.worldgen.IndustrialZone;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Campaign progress is persistent; intermediate objectives belong to one raid. */
public final class StoryMissions {
    private static final String ACCOUNT="efm_story_v1",RUN="efm_story_run_v1";
    enum Mission {
        SILENCE("Opération Silence",false,"Relais forestier",ForestPlan.RELAY,RaidRelic.DOCUMENTS,2,900,180,1),
        INDUSTRIE("Opération Engrenage",true,"Secteur de la sortie principale",IndustrialZone.EXIT,RaidRelic.CIRCUIT,3,1800,320,10),
        OMEGA("Opération Signal perdu",false,"Secteur de la sortie secondaire",ForestPlan.CONDITIONAL_EXIT,RaidRelic.DRIVE,3,3000,500,20);
        final String title,site;final boolean industrial;final ForestPlan.Point point;final RaidRelic relic;
        final int kills,credits,xp,level;
        Mission(String title,boolean industrial,String site,ForestPlan.Point point,RaidRelic relic,int kills,int credits,int xp,int level){
            this.title=title;this.industrial=industrial;this.site=site;this.point=point;this.relic=relic;this.kills=kills;this.credits=credits;this.xp=xp;this.level=level;
        }
        BlockPos pos(){return industrial?ForestExpedition.industrialPosition(point):ForestLandmarks.position(point);}
    }
    private static CompoundTag campaign(ServerPlayer p){
        var a=SupplyContract.account(p);if(!a.contains(ACCOUNT))a.put(ACCOUNT,new CompoundTag());return a.getCompound(ACCOUNT);
    }
    private static int next(ServerPlayer p){return Math.clamp(campaign(p).getInt("next"),0,Mission.values().length);}
    private static Mission current(ServerPlayer p){int n=next(p);return campaign(p).getBoolean("accepted")&&n<Mission.values().length?Mission.values()[n]:null;}
    private static int count(ServerPlayer p,Mission m){return CarriedStorage.of(p).count(s->s.is(m.relic.item())&&RaidOrigin.found(s));}
    private static void say(ServerPlayer p,String s){p.sendSystemMessage(Component.literal(s));}
    static void status(ServerPlayer p){
        int n=next(p);if(n==Mission.values().length){say(p,"Campagne terminée : "+n+"/"+n+" opérations.");return;}
        Mission m=Mission.values()[n];var run=p.getPersistentData().getCompound(RUN);
        int stage=run.getInt("stage");boolean inRaid=ForestRaid.isParticipant(p)&&run.getBoolean("active");
        say(p,m.title+" · "+(n+1)+"/"+Mission.values().length+" · "+(current(p)==null?"À accepter":inRaid?"En cours":"En attente du prochain raid"));
        say(p,(m.industrial?"Zone industrielle":"Forêt Noire")+" · niveau "+m.level+" · "+m.credits+" crédits / "+m.xp+" XP");
        say(p,"1. "+m.site+" (X "+m.pos().getX()+" Z "+m.pos().getZ()+") · 2. Récupérer "+m.relic.title+" · 3. Éliminer "+m.kills+" ennemis · 4. S'extraire.");
        if(inRaid)say(p,"Étape "+Math.min(4,stage+1)+"/4 · éliminations "+run.getInt("kills")+"/"+m.kills);
    }
    static void accept(ServerPlayer p){
        if(!p.isAlive()||p.isCreative()||p.isSpectator()||ForestRaid.isParticipant(p)||p.level().dimension().equals(ForestExpedition.FOREST))return;
        int n=next(p);if(n>=Mission.values().length){status(p);return;}
        Mission m=Mission.values()[n];
        if(MilitaryProgression.level(SupplyContract.experienceEarned(p))<m.level){say(p,"Niveau militaire "+m.level+" requis.");return;}
        campaign(p).putBoolean("accepted",true);status(p);
    }
    static void begin(ServerPlayer p,boolean industrial){
        clear(p);Mission m=current(p);if(m==null||m.industrial!=industrial)return;
        var run=new CompoundTag();run.putBoolean("active",true);run.putInt("mission",next(p));run.putInt("baseline",count(p,m));p.getPersistentData().put(RUN,run);
        say(p,m.title+" · objectif : "+m.site+" (X "+m.pos().getX()+" Z "+m.pos().getZ()+").");
    }
    private static CompoundTag run(ServerPlayer p,Mission m){
        var r=p.getPersistentData().getCompound(RUN);
        return m!=null&&r.getBoolean("active")&&r.getInt("mission")==next(p)?r:null;
    }
    public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p)||p.tickCount%20!=0||!p.isAlive()||p.isCreative()||p.isSpectator()||!ForestRaid.isParticipant(p))return;
        Mission m=current(p);var r=run(p,m);if(r==null)return;
        if(r.getInt("stage")==0&&m.pos().distToCenterSqr(p.position())<=24*24){
            r.putInt("stage",1);say(p,"Secteur atteint · récupère "+m.relic.title+".");
        }
        if(r.getInt("stage")==1&&count(p,m)>r.getInt("baseline")){
            r.putInt("stage",2);say(p,"Renseignement récupéré · élimine "+m.kills+" ennemis.");
        }
    }
    static void kill(ServerPlayer p){
        if(!ForestRaid.isParticipant(p)||p.isCreative()||p.isSpectator())return;
        Mission m=current(p);var r=run(p,m);if(r==null||r.getInt("stage")!=2)return;
        int k=Math.min(m.kills,r.getInt("kills")+1);r.putInt("kills",k);
        if(k==m.kills){r.putInt("stage",3);say(p,"Objectifs accomplis · extrais-toi avec le renseignement.");}
    }
    // Called only after confirmed extraction, before the standard report.
    static void extracted(ServerPlayer p,boolean industrial){
        Mission m=current(p);var r=run(p,m);
        if(r==null){clear(p);return;}
        boolean ready=m.industrial==industrial&&r.getInt("stage")==3&&r.getInt("kills")>=m.kills;
        int baseline=r.getInt("baseline");clear(p);
        if(!ready||count(p,m)<=baseline){say(p,"Opération incomplète · nouvelle tentative au prochain raid.");return;}
        var a=SupplyContract.account(p);
        if((long)SupplyContract.balance(p)+m.credits>Integer.MAX_VALUE){say(p,"Solde maximal · opération non validée.");return;}
        if(!CarriedStorage.of(p).consume(s->s.is(m.relic.item())&&RaidOrigin.found(s),1))return;
        int index=next(p);campaign(p).putInt("next",index+1);campaign(p).putBoolean("accepted",false);
        a.putInt("credits",SupplyContract.balance(p)+m.credits);
        a.putInt("contract_xp_bonus_v2",(int)Math.min(Integer.MAX_VALUE,(long)Math.max(0,a.getInt("contract_xp_bonus_v2"))+m.xp));
        var d=p.getPersistentData();
        d.putInt("taczoperations_run_contracts",d.getInt("taczoperations_run_contracts")+1);
        d.putInt("taczoperations_run_contract_xp",(int)Math.min(Integer.MAX_VALUE,(long)Math.max(0,d.getInt("taczoperations_run_contract_xp"))+m.xp));
        say(p,m.title+" terminée · +"+m.credits+" crédits / +"+m.xp+" XP.");
    }
    static void clear(ServerPlayer p){p.getPersistentData().remove(RUN);}
    public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("efm").then(Commands.literal("missions")
            .executes(ctx->{status(ctx.getSource().getPlayerOrException());return 1;})
            .then(Commands.literal("accepter").executes(ctx->{accept(ctx.getSource().getPlayerOrException());return 1;}))));
    }
    private StoryMissions(){}
}
