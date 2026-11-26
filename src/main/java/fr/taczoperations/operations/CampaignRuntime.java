package fr.taczoperations.operations;

import com.google.gson.*;
import fr.taczoperations.worldgen.ExpressZone;
import fr.taczoperations.campaign.CampaignGraph;
import fr.taczoperations.campaign.CampaignGraph.*;
import fr.taczoperations.campaign.CampaignTrader;
import fr.taczoperations.campaign.TurnInPlanner;
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
    private static final String FUEL="5a0327ba86f77456b9154236";
    private record Catalog(CampaignGraph graph,String revision){}
    private record Reward(int credits,int xp,int reputation){}
    private static Reward reward(String id){
        return switch(id){
            case DEBUT->new Reward(DEBUT_CREDITS,DEBUT_XP,DEBUT_REP);
            case FUEL->new Reward(550,30,100); // provisional: one reference USD per credit
            default->throw new IllegalArgumentException("Unreviewed reward");
        };
    }
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
                if(id.equals(FUEL)&&q.has("implemented")&&q.get("implemented").getAsBoolean()
                    &&trader.equals("peacekeeper")&&prereqs.isEmpty()&&conflicts.isEmpty()&&objectives.size()==2){
                    var a=objectives.get(0);var b=objectives.get(1);
                    reviewed=a.id().equals("5a03289686f7745dbc6c5063")&&b.id().equals("5a0328cb86f77456b91543b8")
                        &&a.amount()==5&&b.amount()==3&&a.scope()==Scope.CAMPAIGN&&b.scope()==Scope.CAMPAIGN
                        &&!a.optional()&&!b.optional()&&turnInRefs(a).size()==18&&turnInRefs(b).size()==17;
                }
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
    private static Set<String> turnInRefs(Objective o){
        if(!o.type().startsWith("turnin-fir:"))return Set.of();
        var refs=new HashSet<String>();
        for(var id:o.type().substring("turnin-fir:".length()).split(",",-1)){
            if(!id.matches("[0-9a-f]{24}")||!refs.add(id))throw new IllegalArgumentException("Invalid item reference");
        }
        return Set.copyOf(refs);
    }
    private static String label(String id){return id.equals(DEBUT)?"Debut":id.equals(FUEL)?"Fuel Shortage":id;}
    private static int level(ServerPlayer p){return MilitaryProgression.level(SupplyContract.experienceEarned(p));}
    private static boolean accessible(ServerPlayer p){
        return CATALOG!=null&&PlayerMarket.available(p)&&!ForestExpedition.hasReturnPoint(p)&&!p.isCreative()&&!p.isSpectator();
    }
    private static void say(ServerPlayer p,String text){p.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));}
    static int status(ServerPlayer p){
        if(!accessible(p)){say(p,"Campagne disponible hors raid, en survie.");return 0;}
        try{
            var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),null);
            for(var q:CATALOG.graph().quests().values()){
                if(!q.implemented())continue;
                var state=s.player().state(q.id(),level(p));
                var progress=s.player().snapshot().get(q.id());
                var r=reward(q.id());
                say(p,CampaignTrader.byId(q.trader()).title+" · "+label(q.id())+" · "+state);
                for(var o:q.objectives()){
                    int count=progress==null?0:progress.counters().getOrDefault(o.id(),0);
                    var refs=turnInRefs(o);
                    if(!refs.isEmpty()&&state==State.ACTIVE)
                        count=CarriedStorage.of(p).count(stack->RaidOrigin.found(stack)&&CampaignSupplies.reference(stack).filter(refs::contains).isPresent());
                    String title=refs.isEmpty()?"Scavs":o.amount()==5?"Fournitures d'entretien":"Combustibles et fournitures techniques";
                    say(p,title+" : "+Math.min(count,o.amount())+"/"+o.amount());
                }
                say(p,"Récompenses : "+r.credits()+" crédits · "+r.xp()+" XP · +"+r.reputation()/1000d+" réputation.");
            }
            return 1;
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign status failed",e);say(p,"Sauvegarde de campagne incompatible.");return 0;}
    }
    static int acceptDebut(ServerPlayer p){return accept(p,DEBUT);}
    static int acceptFuel(ServerPlayer p){return accept(p,FUEL);}
    private static int accept(ServerPlayer p,String id){
        if(!accessible(p))return 0;
        try{
            var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),null);
            if(!s.player().accept(id,level(p))){say(p,"Quête indisponible ou déjà acceptée.");return 0;}
            CampaignStorage.save(p,CATALOG.revision(),s);
            say(p,label(id)+" acceptée.");
            return 1;
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign accept failed",e);return 0;}
    }
    static int claimDebut(ServerPlayer p){return claim(p,DEBUT);}
    static int claimFuel(ServerPlayer p){return claim(p,FUEL);}
    private static int claim(ServerPlayer p,String id){
        if(!accessible(p)||!p.containerMenu.getCarried().isEmpty())return 0;
        try{
            var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),null);
            var q=CATALOG.graph().quests().get(id);
            if(q==null||!q.implemented())return 0;
            var state=s.player().state(id,level(p));
            if(state!=State.READY&&state!=State.ACTIVE){say(p,"Quête non acceptée ou déjà terminée.");return 0;}
            var r=reward(id);
            var account=SupplyContract.account(p);
            int credits=account.getInt("credits"),xp=Math.max(0,account.getInt("contract_xp_bonus_v2"));
            if((long)credits+r.credits()>Integer.MAX_VALUE||(long)xp+r.xp()>Integer.MAX_VALUE){say(p,"Solde maximal atteint.");return 0;}
            var storage=CarriedStorage.of(p);
            var demands=new ArrayList<TurnInPlanner.Demand>();
            var stock=new TreeMap<String,Integer>();
            for(var o:q.objectives()){
                var refs=turnInRefs(o);
                if(refs.isEmpty())continue;
                demands.add(new TurnInPlanner.Demand(o.amount(),refs));
                for(var ref:refs)stock.computeIfAbsent(ref,key->storage.count(stack->RaidOrigin.found(stack)&&CampaignSupplies.matches(stack,key)));
            }
            var plan=TurnInPlanner.plan(stock,demands);
            if(plan==null){say(p,"Objets trouvés en raid insuffisants. Aucun objet remis.");return 0;}
            for(var o:q.objectives())if(!turnInRefs(o).isEmpty())s.player().record(id,o.id(),o.amount());
            if(!s.player().complete(id,level(p))){say(p,"Objectifs incomplets.");return 0;}
            // No mutation until the entire allocation and reward have been validated on the server thread.
            for(var entry:plan.entrySet())
                if(!storage.consume(stack->RaidOrigin.found(stack)&&CampaignSupplies.matches(stack,entry.getKey()),entry.getValue()))
                    throw new IllegalStateException("Inventory changed during turn-in");
            CampaignStorage.save(p,CATALOG.revision(),s);
            account.putInt("credits",credits+r.credits());
            account.putInt("contract_xp_bonus_v2",xp+r.xp());
            CampaignTraderAccount.applyReputation(p,CampaignTrader.byId(q.trader()),r.reputation());
            SupplyContract.rewardLevels(p);
            say(p,label(id)+" terminée · +"+r.credits()+" crédits · +"+r.xp()+" XP · +"+r.reputation()/1000d+" réputation.");
            return 1;
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign claim failed",e);return 0;}
    }
    private CampaignRuntime(){}
}
