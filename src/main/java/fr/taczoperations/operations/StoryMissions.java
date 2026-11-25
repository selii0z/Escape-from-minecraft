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
    enum Target {ANY("ennemis"),PMC("PMC"),ELITE("ennemis d’élite"),BOSS("boss");
        final String label;Target(String label){this.label=label;}
        boolean matches(GuardRole role){return switch(this){case ANY->true;case PMC->role==GuardRole.PMC;case ELITE->role.elite();case BOSS->role.boss();};}
    }
    enum Mission {
        SILENCE("Opération Silence",false,"Relais forestier",ForestPlan.RELAY,RaidRelic.DOCUMENTS,2,900,180,1),
        INDUSTRIE("Opération Engrenage",true,"Secteur de la sortie principale",IndustrialZone.EXIT,RaidRelic.CIRCUIT,3,1800,320,10),
        OMEGA("Opération Signal perdu",false,"Secteur de la sortie secondaire",ForestPlan.CONDITIONAL_EXIT,RaidRelic.DRIVE,3,3000,500,20),
        LOGISTIQUE("Opération Ravitaillement",true,"Réserve logistique",new ForestPlan.Point(208,182),RaidRelic.RADIO,3,3600,650,25,2,Target.ANY,false),
        VEILLE("Opération Veille nocturne",false,"Secteur du relais",ForestPlan.RELAY,RaidRelic.WATCH,3,4800,850,30,1,Target.ANY,true),
        FER("Opération Ligne de fer",true,"Dépôt ferroviaire",new ForestPlan.Point(560,390),RaidRelic.CIRCUIT,4,6000,1100,40,2,Target.ANY,false),
        FANTOME("Opération Fantôme",false,"Patrouille du nord",new ForestPlan.Point(652,197),RaidRelic.CASE,2,8000,1500,50,1,Target.PMC,true),
        ARCHIVES("Opération Archives",true,"Archives de l'usine",new ForestPlan.Point(410,175),RaidRelic.DRIVE,3,10000,1900,60,2,Target.ELITE,false),
        VIGIE("Opération Vigie",false,"Secteur des patrouilles ouest",new ForestPlan.Point(245,342),RaidRelic.OPTIC,4,14000,2600,75,1,Target.ELITE,false),
        NEMESIS("Opération Némésis",true,"Complexe industriel central",new ForestPlan.Point(405,320),RaidRelic.CHIP,1,18000,3500,90,1,Target.BOSS,false),
        CREPUSCULE("Opération Crépuscule",false,"Secteur de la sortie secondaire",ForestPlan.CONDITIONAL_EXIT,RaidRelic.CASE,3,24000,4500,110,2,Target.PMC,true),
        ECLIPSE("Opération Éclipse",true,"Archives de l'usine",new ForestPlan.Point(410,175),RaidRelic.OMEGA,1,35000,7000,150,1,Target.BOSS,false);
        final String title,site;final boolean industrial;final ForestPlan.Point point;final RaidRelic relic;
        final int kills,credits,xp,level,quantity;final Target target;final boolean night;
        Mission(String title,boolean industrial,String site,ForestPlan.Point point,RaidRelic relic,int kills,int credits,int xp,int level){
            this(title,industrial,site,point,relic,kills,credits,xp,level,1,Target.ANY,false);
        }
        Mission(String title,boolean industrial,String site,ForestPlan.Point point,RaidRelic relic,int kills,int credits,int xp,int level,int quantity,Target target,boolean night){
            this.title=title;this.industrial=industrial;this.site=site;this.point=point;this.relic=relic;this.kills=kills;this.credits=credits;this.xp=xp;this.level=level;this.quantity=quantity;this.target=target;this.night=night;
        }
        BlockPos pos(){return industrial?ForestExpedition.industrialPosition(point):ForestLandmarks.position(point);}
    }
    private static CompoundTag campaign(ServerPlayer p){
        var a=SupplyContract.account(p);if(!a.contains(ACCOUNT))a.put(ACCOUNT,new CompoundTag());return a.getCompound(ACCOUNT);
    }
    static int next(ServerPlayer p){return Math.clamp(campaign(p).getInt("next"),0,Mission.values().length);}
    static Mission current(ServerPlayer p){int n=next(p);return campaign(p).getBoolean("accepted")&&n<Mission.values().length?Mission.values()[n]:null;}
    private static int count(ServerPlayer p,Mission m){return CarriedStorage.of(p).count(s->s.is(m.relic.item())&&RaidOrigin.found(s));}
    private static void say(ServerPlayer p,String s){p.sendSystemMessage(Component.literal(s));}
    static void status(ServerPlayer p){
        int n=next(p);if(n==Mission.values().length){say(p,"Campagne terminée : "+n+"/"+n+" opérations.");return;}
        Mission m=Mission.values()[n];var run=p.getPersistentData().getCompound(RUN);
        int stage=run.getInt("stage");boolean inRaid=ForestRaid.isParticipant(p)&&run.getBoolean("active");
        say(p,m.title+" · "+(n+1)+"/"+Mission.values().length+" · "+(current(p)==null?"À accepter":inRaid?"En cours":"En attente du prochain raid"));
        say(p,(m.industrial?"Zone industrielle":"Forêt Noire")+(m.night?" · Nuit":"")+" · niveau "+m.level+" · "+m.credits+" crédits / "+m.xp+" XP");
        say(p,"1. "+m.site+" (X "+m.pos().getX()+" Z "+m.pos().getZ()+") · Récupérer "+m.quantity+" × "+m.relic.title+" et éliminer "+m.kills+" "+m.target.label+" · Extraction.");
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
        clear(p);Mission m=current(p);if(m==null||m.industrial!=industrial||m.night&&!ForestExpedition.isNightRaid(p))return;
        var run=new CompoundTag();run.putBoolean("active",true);run.putInt("mission",next(p));run.putInt("baseline",count(p,m));p.getPersistentData().put(RUN,run);
        say(p,m.title+" · objectif : "+m.site+" (X "+m.pos().getX()+" Z "+m.pos().getZ()+").");
    }
    static CompoundTag run(ServerPlayer p,Mission m){
        var r=p.getPersistentData().getCompound(RUN);
        return m!=null&&r.getBoolean("active")&&r.getInt("mission")==next(p)?r:null;
    }
    public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p)||p.tickCount%20!=0||!p.isAlive()||p.isCreative()||p.isSpectator()||!ForestRaid.isParticipant(p))return;
        Mission m=current(p);var r=run(p,m);if(r==null)return;
        if(r.getInt("stage")==0&&m.pos().distToCenterSqr(p.position())<=24*24){
            r.putInt("stage",1);say(p,"Secteur atteint · récupère "+m.quantity+" × "+m.relic.title+" et élimine "+m.kills+" "+m.target.label+".");
        }
        updateObjectives(p,m,r);
    }
    private static void updateObjectives(ServerPlayer p,Mission m,CompoundTag r){
        if(r.getInt("stage")==0)return;
        boolean relics=(long)count(p,m)-r.getInt("baseline")>=m.quantity;
        boolean kills=r.getInt("kills")>=m.kills;
        int stage=relics&&kills?3:relics||kills?2:1;
        int before=r.getInt("stage");r.putInt("stage",stage);
        if(stage==3&&before!=3)say(p,"Objectifs accomplis · extrais-toi avec le renseignement.");
        else if(before==3&&stage!=3)say(p,"Renseignement manquant · récupère-le avant l'extraction.");
        else if(stage==2&&before==1)say(p,relics?"Renseignement récupéré · termine les éliminations.":"Éliminations terminées · récupère le renseignement.");
    }
    static void kill(ServerPlayer p,GuardRole role){
        if(!ForestRaid.isParticipant(p)||p.isCreative()||p.isSpectator())return;
        Mission m=current(p);var r=run(p,m);if(r==null||!m.target.matches(role))return;
        if(r.getInt("stage")==0&&m.pos().distToCenterSqr(p.position())<=24*24){r.putInt("stage",1);say(p,"Secteur atteint.");}
        if(r.getInt("stage")==0||r.getInt("kills")>=m.kills)return;
        int k=Math.min(m.kills,r.getInt("kills")+1);r.putInt("kills",k);
        updateObjectives(p,m,r);
    }
    // Called only after confirmed extraction, before the standard report.
    static void extracted(ServerPlayer p,boolean industrial){
        var data=p.getPersistentData();
        if(!data.contains("taczoperations_run_started")||data.getBoolean("taczoperations_run_xp_finalized")){clear(p);return;}
        Mission m=current(p);var r=run(p,m);
        if(r==null){clear(p);return;}
        boolean ready=m.industrial==industrial&&r.getInt("stage")>=1&&r.getInt("kills")>=m.kills;
        int baseline=r.getInt("baseline");clear(p);
        if(!ready||(long)count(p,m)-baseline<m.quantity){say(p,"Opération incomplète · nouvelle tentative au prochain raid.");return;}
        var a=SupplyContract.account(p);
        if((long)SupplyContract.balance(p)+m.credits>Integer.MAX_VALUE){say(p,"Solde maximal · opération non validée.");return;}
        if(!CarriedStorage.of(p).consume(s->s.is(m.relic.item())&&RaidOrigin.found(s),m.quantity))return;
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
            .then(Commands.literal("menu").executes(ctx->{ProgressionMenus.missions(ctx.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("accepter").executes(ctx->{accept(ctx.getSource().getPlayerOrException());return 1;}))));
    }
    private StoryMissions(){}
}
