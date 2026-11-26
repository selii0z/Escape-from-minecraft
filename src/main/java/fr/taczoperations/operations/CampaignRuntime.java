package fr.taczoperations.operations;

import com.google.gson.*;
import fr.taczoperations.worldgen.ExpressZone;
import fr.taczoperations.campaign.CampaignGraph;
import fr.taczoperations.campaign.CampaignGraph.*;
import fr.taczoperations.campaign.CampaignTrader;
import net.minecraft.server.level.ServerPlayer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Server-only bridge. No catalog means no campaign events and no changes to the legacy campaign. */
final class CampaignRuntime {
    private static final System.Logger LOG=System.getLogger("taczoperations.campaign");
    private static final String RESOURCE="/data/taczoperations/campaign/quests.json";
    private static final String DEBUT="5936d90786f7742b1420ba5b";
    private record Catalog(CampaignGraph graph,String revision){}
    // Provisional economy conversion: 100 reference rubles per EFM credit,
    // 100 reference XP per EFM XP. Keep original values in the reference catalog.
    private static final int DEBUT_CREDITS=800,DEBUT_XP=30,DEBUT_REP=100;
    private static final Catalog CATALOG=load();
    private static final Set<String> MAPS=Set.of("*","woods","customs","factory","interchange","village","streets");

    static String map(boolean industrial,ExpressZone express){
        if(express==null)return industrial?"customs":"woods";
        return switch(express){case BUNKER->"factory";case WAREHOUSE->"interchange";case VILLAGE->"village";case CITY->"streets";};
    }
    private static Catalog load(){
        try(var in=CampaignRuntime.class.getResourceAsStream(RESOURCE)){
            if(in==null)return null;
            byte[] bytes=in.readAllBytes();
            var root=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unknown catalog schema");
            var quests=new ArrayList<Quest>();
            for(var value:root.getAsJsonArray("quests")){
                var q=value.getAsJsonObject();
                String id=q.get("id").getAsString(),trader=CampaignTrader.byId(q.get("trader").getAsString()).id;
                var prereqs=new ArrayList<List<Requirement>>();
                for(var g:q.getAsJsonArray("prerequisites")){
                    var group=new ArrayList<Requirement>();
                    for(var v:g.getAsJsonArray()){
                        var r=v.getAsJsonObject();var outcomes=EnumSet.noneOf(Outcome.class);
                        for(var o:r.getAsJsonArray("outcomes"))outcomes.add(Outcome.valueOf(o.getAsString()));
                        group.add(new Requirement(r.get("quest").getAsString(),outcomes));
                    }
                    prereqs.add(group);
                }
                var conflicts=new HashSet<String>();
                for(var v:q.getAsJsonArray("conflicts"))conflicts.add(v.getAsString());
                var objectives=new ArrayList<Objective>();
                // Catalog authors must explicitly certify the quest AND reward adapter.
                // Until a reward/turn-in adapter exists no quest is enabled by this bridge.
                for(var v:q.getAsJsonArray("objectives")){
                    var o=v.getAsJsonObject();
                    String type=o.get("type").getAsString();
                    objectives.add(new Objective(o.get("id").getAsString(),type,o.get("amount").getAsInt(),
                        Scope.valueOf(o.get("scope").getAsString()),o.get("optional").getAsBoolean()));
                }
                boolean reviewed=id.equals(DEBUT)&&q.has("implemented")&&q.get("implemented").getAsBoolean()
                    &&trader.equals("prapor")&&prereqs.isEmpty()&&conflicts.isEmpty()&&objectives.size()==1
                    &&objectives.getFirst().equals(new Objective("5967379186f77463860dadd6","kill:woods,customs,interchange:scav",5,Scope.CAMPAIGN,false));
                quests.add(new Quest(id,trader,q.get("level").getAsInt(),prereqs,objectives,conflicts,reviewed));
            }
            return new Catalog(new CampaignGraph(quests),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        }catch(Exception e){
            LOG.log(System.Logger.Level.ERROR,"Campaign catalog rejected; legacy raids remain enabled",e);
            return null;
        }
    }
    private interface Change {CampaignStorage.Session apply(CampaignStorage.Session session);}
    private static void change(ServerPlayer p,UUID raidId,Change change){
        if(CATALOG==null||p.isCreative()||p.isSpectator())return;
        try{
            var session=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),raidId);
            var updated=change.apply(session);
            if(updated!=null)CampaignStorage.save(p,CATALOG.revision(),updated);
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign save/event rejected for "+p.getUUID(),e);}
    }
    static void begin(ServerPlayer p,UUID raidId){
        change(p,raidId,s->{
            if(s.player().checkpoint().inRaid())return null;
            s.player().beginRaid();
            return new CampaignStorage.Session(s.player(),raidId);
        });
    }
    static void kill(ServerPlayer p,UUID raidId,String map,GuardRole role){
        if(!ForestRaid.isParticipant(p)||!p.isAlive())return;
        change(p,raidId,s->{
            if(!raidId.equals(s.raidId()))return null;
            return record(s,"kill",map,role)?s:null;
        });
    }
    static void end(ServerPlayer p,UUID raidId,String map,boolean success){
        if(raidId==null)return;
        change(p,raidId,s->{
            if(!raidId.equals(s.raidId()))return null;
            if(success)record(s,"extract",map,null);
            s.player().endRaid(success);
            return new CampaignStorage.Session(s.player(),null);
        });
    }
    private static boolean record(CampaignStorage.Session s,String event,String map,GuardRole role){
        boolean changed=false;
        for(var q:CATALOG.graph().quests().values()){
            if(!q.implemented())continue;
            var progress=s.player().snapshot().get(q.id());
            if(progress==null||progress.state()!=State.ACTIVE)continue;
            for(var objective:q.objectives()){
                if(matches(objective.type(),event,map,role))changed|=s.player().record(q.id(),objective.id(),1);
            }
        }
        return changed;
    }
    // Normalized explicit rules: kill:<map>:<role> or extract:<map>.
    // Unrecognized predicates are never treated as unrestricted objectives.
    static boolean matches(String type,String event,String map,GuardRole role){
        var parts=type.split(":",-1);
        if(parts.length<2||!parts[0].equals(event))return false;
        var allowed=Arrays.asList(parts[1].split(",",-1));
        if(!MAPS.containsAll(allowed)||!(allowed.contains("*")||allowed.contains(map)))return false;
        if(event.equals("extract"))return parts.length==2;
        if(!event.equals("kill")||parts.length!=3||role==null)return false;
        return switch(parts[2]){
            case "any"->true;
            case "pmc"->role==GuardRole.PMC;
            case "boss"->role.boss();
            case "scav"->role==GuardRole.RAIDER||role==GuardRole.SCOUT||role==GuardRole.SOLDIER||role==GuardRole.MARKSMAN;
            default->parts[2].equals(role.name().toLowerCase(Locale.ROOT));
        };
    }
    private static int level(ServerPlayer p){return MilitaryProgression.level(SupplyContract.experienceEarned(p));}
    private static boolean accessible(ServerPlayer p){
        return CATALOG!=null&&PlayerMarket.available(p)&&!ForestExpedition.hasReturnPoint(p)&&!p.isCreative()&&!p.isSpectator();
    }
    private static void say(ServerPlayer p,String text){p.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));}
    static int status(ServerPlayer p){
        if(!accessible(p)){say(p,"Campagne disponible hors raid, en survie.");return 0;}
        try{
            var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),null);
            if(!CATALOG.graph().quests().containsKey(DEBUT))return 0;
            var progress=s.player().snapshot().get(DEBUT);
            int kills=progress==null?0:progress.counters().getOrDefault("5967379186f77463860dadd6",0);
            say(p,"Prapor · Debut · "+s.player().state(DEBUT,level(p))+" · Scavs "+kills+"/5");
            say(p,"Cartes : Forêt Noire, Zone industrielle, Entrepôt. Récompense : "+DEBUT_CREDITS+" crédits, "+DEBUT_XP+" XP et +0,1 réputation.");
            return 1;
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign status failed",e);say(p,"Sauvegarde de campagne incompatible.");return 0;}
    }
    static int acceptDebut(ServerPlayer p){
        if(!accessible(p))return 0;
        try{
            var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),null);
            if(!s.player().accept(DEBUT,level(p))){say(p,"Quête indisponible ou déjà acceptée.");return 0;}
            CampaignStorage.save(p,CATALOG.revision(),s);
            say(p,"Prapor · Debut acceptée : élimine 5 Scavs sur les cartes autorisées.");
            return 1;
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign accept failed",e);return 0;}
    }
    static int claimDebut(ServerPlayer p){
        if(!accessible(p))return 0;
        try{
            var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),null);
            if(s.player().state(DEBUT,level(p))!=State.READY){say(p,"Objectifs incomplets ou récompense déjà récupérée.");return 0;}
            var a=SupplyContract.account(p);
            int credits=a.getInt("credits"),xp=Math.max(0,a.getInt("contract_xp_bonus_v2"));
            if((long)credits+DEBUT_CREDITS>Integer.MAX_VALUE||(long)xp+DEBUT_XP>Integer.MAX_VALUE){say(p,"Solde maximal atteint.");return 0;}
            if(!s.player().complete(DEBUT,level(p)))return 0;
            // All writes target the same server-side persisted account on the server thread.
            CampaignStorage.save(p,CATALOG.revision(),s);
            a.putInt("credits",credits+DEBUT_CREDITS);
            a.putInt("contract_xp_bonus_v2",xp+DEBUT_XP);
            CampaignTraderAccount.applyReputation(p,CampaignTrader.PRAPOR,DEBUT_REP);
            SupplyContract.rewardLevels(p);
            say(p,"Debut terminée · +"+DEBUT_CREDITS+" crédits · +"+DEBUT_XP+" XP · +0,1 Prapor.");
            return 1;
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign claim failed",e);return 0;}
    }
    private CampaignRuntime(){}
}
