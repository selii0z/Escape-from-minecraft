package fr.taczoperations.operations;

import com.google.gson.*;
import fr.taczoperations.worldgen.ExpressZone;
import fr.taczoperations.campaign.CampaignGraph;
import fr.taczoperations.campaign.CampaignGraph.*;
import fr.taczoperations.campaign.CampaignTrader;
import fr.taczoperations.campaign.TurnInPlanner;
import fr.taczoperations.campaign.FieldAction;
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
    private static final String REVEALED="669fa39ee749756c920d02c8";
    private static final String SAMPLE_FIND="669fb6c859bdae826f7325d4";
    private static final String SAMPLE_GIVE="66a7eebed6bac3ecc16f7d6b";
    private static final String SEARCH="5fd9fad9c1ce6b1a3b486d00", CHEMICAL="5a27bafb86f7741c73584017";
    private static final String CONVOY="5fd9fad9c1ce6b1a3b486d03", CAMP="5fd9fad9c1ce6b1a3b486d05";
    private static final String DEPOSIT="6a5c10e883a51e292d11b976", HOSE="59e35cbb86f7741778269d83";
    private static final String INTRO="5d2495a886f77425cd51e403", FILTH="600302d73b897b11364cd161", ULTRA="5ae448bf86f7744d733e55ee", ACQUAINTANCE="5d24b81486f77439c92d6ba8";
    private static final String HUNT_VISIT="63ac18f4972364554162a25c", HUNT_FIND="5d249a6e86f774791546e952", HUNT_GIVE="5d249aa286f77475e8376399";
    private record Meta(String name,String slug,Reward reward,int loyalty,Map<String,String> labels){}
    private record Catalog(CampaignGraph graph,String revision,Map<String,Meta> meta){}
    private record Reward(int credits,int xp,int reputation){}
    private static Reward reward(String id){
        return switch(id){
            case DEBUT->new Reward(DEBUT_CREDITS,DEBUT_XP,DEBUT_REP);
            case REVEALED->new Reward(1650,100,250);
            case SEARCH->new Reward(800,30,100);
            case CHEMICAL->new Reward(550,30,100);
            case FUEL->new Reward(550,30,100); // provisional: one reference USD per credit
            default->Objects.requireNonNull(CATALOG.meta().get(id).reward());
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
            var meta=new LinkedHashMap<String,Meta>();
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
                if(id.equals(REVEALED)&&q.has("implemented")&&q.get("implemented").getAsBoolean()
                    &&trader.equals("therapist")&&q.get("level").getAsInt()==5&&prereqs.isEmpty()&&conflicts.isEmpty()){
                    reviewed=objectives.equals(List.of(
                        new Objective(SAMPLE_FIND,"quest-find:factory:chemical-sample",1,Scope.SURVIVING_RAID,false),
                        new Objective(SAMPLE_GIVE,"quest-give:chemical-sample",1,Scope.CAMPAIGN,false)));
                }
                if(q.has("implemented")&&q.get("implemented").getAsBoolean()&&prereqs.isEmpty()&&conflicts.isEmpty()&&q.get("level").getAsInt()==1){
                    if(id.equals(SEARCH)&&trader.equals("prapor"))reviewed=objectives.equals(List.of(
                        new Objective(CONVOY,"visit:woods:convoy",1,Scope.CAMPAIGN,false),
                        new Objective(CAMP,"visit:woods:camp",1,Scope.CAMPAIGN,false)));
                    if(id.equals(CHEMICAL)&&trader.equals("peacekeeper"))reviewed=objectives.equals(List.of(
                        new Objective(DEPOSIT,"plant:customs:medical-deposit:"+HOSE,1,Scope.CAMPAIGN,false)));
                }
                if(q.has("implemented")&&q.get("implemented").getAsBoolean()&&prereqs.isEmpty()&&conflicts.isEmpty()&&q.get("level").getAsInt()==1){
                    if(id.equals(INTRO)&&trader.equals("mechanic"))reviewed=objectives.equals(List.of(
                        new Objective(HUNT_VISIT,"visit:woods:hunting-cabin",1,Scope.CAMPAIGN,false),
                        new Objective(HUNT_FIND,"quest-find:woods:jaeger-message",1,Scope.SURVIVING_RAID,false),
                        new Objective(HUNT_GIVE,"quest-give:jaeger-message",1,Scope.CAMPAIGN,false)));
                    if(id.equals(FILTH)&&trader.equals("jaeger"))reviewed=objectives.equals(List.of(new Objective("600303250b79c6604058ce30","kill:woods,customs,interchange:scav",20,Scope.CAMPAIGN,false)));
                    if(id.equals(ULTRA)&&trader.equals("ragman"))reviewed=objectives.equals(List.of(new Objective("5ae44ecd86f77414a13c970e","kill:interchange:any",10,Scope.CAMPAIGN,false)));
                    if(id.equals(ACQUAINTANCE)&&trader.equals("jaeger")&&objectives.size()==1){
                        var o=objectives.getFirst();reviewed=o.id().equals("5d24ba7886f77439c92d6baa")&&o.type().startsWith("turnin-any:")&&turnInRefs(o).size()==24&&o.amount()==10&&o.scope()==Scope.CAMPAIGN&&!o.optional();
                    }
                }
                Reward reward=null;
                if(q.has("reward")){
                    var r=q.getAsJsonObject("reward");reward=new Reward(r.get("credits").getAsInt(),r.get("xp").getAsInt(),r.get("reputation").getAsInt());
                    if(reward.credits()<0||reward.xp()<0||reward.reputation()<0)throw new IllegalArgumentException("Invalid reward");
                }
                int loyalty=q.has("loyalty")?q.get("loyalty").getAsInt():1;
                if(loyalty<1||loyalty>4)throw new IllegalArgumentException("Invalid loyalty");
                var labels=new HashMap<String,String>();
                for(var valueLabel:q.getAsJsonArray("objectives")){
                    var o=valueLabel.getAsJsonObject();
                    if(o.has("label"))labels.put(o.get("id").getAsString(),o.get("label").getAsString());
                }
                if(q.has("adapter")&&q.get("adapter").getAsString().equals("paired-stock-v1")){
                    reviewed=q.get("implemented").getAsBoolean()&&reward!=null&&prereqs.isEmpty()&&conflicts.isEmpty()
                        &&Set.of("59c9392986f7742f6923add2","5ac346a886f7744e1b083d67","5d25e4b786f77408251c4bfc",
                            "5969f90786f77420d2328015","60e71dc67fcf9c556f325056","69ce1de03e15cd80bd06f6c9").contains(id)
                        &&pairedStockValid(objectives);
                }
                String name=q.get("name").getAsString();
                String slug=name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");
                if(meta.values().stream().anyMatch(x->x.slug().equals(slug)))throw new IllegalArgumentException("Duplicate quest slug");
                meta.put(id,new Meta(name,slug,reward,loyalty,Map.copyOf(labels)));
                quests.add(new Quest(id,trader,q.get("level").getAsInt(),prereqs,objectives,conflicts,reviewed));
            }
            return new Catalog(new CampaignGraph(quests),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),Map.copyOf(meta));
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
        ACTIONS.remove(p.getUUID());
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
        ACTIONS.remove(p.getUUID());
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
    private static boolean pairedStockValid(List<Objective> objectives){
        if(objectives.isEmpty()||objectives.size()%2!=0)return false;
        var seen=new HashSet<String>();
        String provenance=null;
        for(int i=0;i<objectives.size();i+=2){
            var find=objectives.get(i);var give=objectives.get(i+1);
            if(!find.type().matches("find-stock-(fir|any):[0-9a-f]{24}")||
                !give.type().equals(find.type().replace("find-stock-","turnin-"))||
                find.amount()!=give.amount()||find.scope()!=Scope.CAMPAIGN||give.scope()!=Scope.CAMPAIGN||
                find.optional()||give.optional()||!seen.add(find.type()))return false;
            String mode=find.type().startsWith("find-stock-fir:")?"fir":"any";
            if(provenance!=null&&!provenance.equals(mode))return false;
            provenance=mode;
        }
        return true;
    }
    private static boolean findStock(Objective o){return o.type().startsWith("find-stock-");}
    private static Set<String> stockRefs(Objective o){
        if(findStock(o))return Set.of(o.type().substring(o.type().indexOf(':')+1));
        return turnInRefs(o);
    }
    private static Set<String> turnInRefs(Objective o){
        if(!o.type().startsWith("turnin-fir:")&&!o.type().startsWith("turnin-any:"))return Set.of();
        var refs=new HashSet<String>();
        for(var id:o.type().substring(o.type().indexOf(':')+1).split(",",-1)){
            if(!id.matches("[0-9a-f]{24}")||!refs.add(id))throw new IllegalArgumentException("Invalid item reference");
        }
        return Set.copyOf(refs);
    }
    private static String label(String id){var m=CATALOG.meta().get(id);return m==null?id:m.name();}
    static List<String> slugs(){return CATALOG==null?List.of():CATALOG.graph().quests().values().stream().filter(Quest::implemented).map(q->CATALOG.meta().get(q.id()).slug()).sorted().toList();}
    static int acceptNamed(ServerPlayer p,String slug){String id=resolve(slug);return id==null?0:accept(p,id);}
    static int claimNamed(ServerPlayer p,String slug){String id=resolve(slug);return id==null?0:claim(p,id);}
    private static String resolve(String slug){
        if(CATALOG==null)return null;
        return CATALOG.meta().entrySet().stream().filter(e->e.getKey().equals(slug)||e.getValue().slug().equalsIgnoreCase(slug)).map(Map.Entry::getKey).findFirst().orElse(null);
    }
    static void traderDialogue(ServerPlayer p,CampaignTrader trader){
        if(accessible(p)&&CampaignTraderAccount.unlocked(p,trader))SupplyContract.account(p).putBoolean("campaign_dialogue_"+trader.id,true);
    }
    private static boolean itemEligible(net.minecraft.world.item.ItemStack stack,Objective o){
        return (!(o.type().startsWith("turnin-fir:")||o.type().startsWith("find-stock-fir:"))||RaidOrigin.found(stack))&&CampaignSupplies.reference(stack).filter(stockRefs(o)::contains).isPresent();
    }
    private static boolean near(ServerPlayer p,net.minecraft.core.BlockPos pos,int radius){
        return pos.distToCenterSqr(p.position())<=radius*radius;
    }
    static void visit(ServerPlayer p,UUID raidId,String map){
        if(CATALOG==null||!map.equals("woods")||!ForestRaid.canContinue(p)||!p.isAlive())return;
        String objective=near(p,ForestLandmarks.position(new fr.taczoperations.worldgen.ForestPlan.Point(484,570)),12)?CONVOY:
            near(p,ForestLandmarks.position(new fr.taczoperations.worldgen.ForestPlan.Point(645,225)),20)?CAMP:null;
        boolean cabin=near(p,ForestLandmarks.position(new fr.taczoperations.worldgen.ForestPlan.Point(130,513)),8);
        if(cabin)change(p,raidId,s->{
            if(!raidId.equals(s.raidId())||s.player().state(INTRO,level(p))!=State.ACTIVE)return null;
            if(s.player().snapshot().get(INTRO).counters().getOrDefault(HUNT_VISIT,0)>0)return null;
            return s.player().record(INTRO,HUNT_VISIT,1)?s:null;
        });
        if(objective==null)return;
        change(p,raidId,s->{
            if(!raidId.equals(s.raidId())||s.player().state(SEARCH,level(p))!=State.ACTIVE)return null;
            var progress=s.player().snapshot().get(SEARCH);
            if(progress.counters().getOrDefault(objective,0)>0)return null;
            if(!s.player().record(SEARCH,objective,1))return null;
            say(p,objective.equals(CONVOY)?"Search Mission · convoi localisé.":"Search Mission · camp localisé.");
            return s;
        });
    }
    // Server thread only; each player can perform one uninterrupted terrain action.
    private record Site(String quest,String objective,String map,String item,net.minecraft.core.BlockPos pos,int radius,int duration){}
    private static final Map<UUID,FieldAction> ACTIONS=new HashMap<>();
    private static final List<Site> SITES=List.of(new Site(CHEMICAL,DEPOSIT,"customs",HOSE,
        new net.minecraft.core.BlockPos(fr.taczoperations.worldgen.IndustrialZone.ORIGIN+95,
            fr.taczoperations.worldgen.IndustrialZone.FLOOR+1,fr.taczoperations.worldgen.IndustrialZone.ORIGIN+425),3,200));
    private static Site site(FieldAction action,ServerPlayer p){
        return SITES.stream().filter(x->x.quest().equals(action.quest())&&x.objective().equals(action.objective())
            &&x.map().equals(action.map())&&x.item().equals(action.item())&&near(p,x.pos(),x.radius())).findFirst().orElse(null);
    }
    static int plant(ServerPlayer p,UUID raidId,String map){
        if(CATALOG==null||raidId==null||!ForestRaid.canContinue(p)||!p.isAlive()||p.isCreative()||p.isSpectator())return 0;
        if(ACTIONS.containsKey(p.getUUID())){say(p,"Une action de terrain est déjà en cours.");return 0;}
        try{
            var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),raidId);
            if(!raidId.equals(s.raidId()))return 0;
            for(var x:SITES){
                if(!x.map().equals(map)||!near(p,x.pos(),x.radius()))continue;
                if(s.player().state(x.quest(),level(p))!=State.ACTIVE)continue;
                var progress=s.player().snapshot().get(x.quest());
                if(progress.counters().getOrDefault(x.objective(),0)>0)continue;
                if(CarriedStorage.of(p).count(stack->CampaignSupplies.matches(stack,x.item()))<1){
                    say(p,"Objet de mission requis : "+(x.item().equals(HOSE)?"tuyau annelé":x.item()));return 0;
                }
                ACTIONS.put(p.getUUID(),new FieldAction(raidId,x.quest(),x.objective(),x.map(),x.item(),x.duration(),0));
                say(p,"Dépôt en cours · reste dans la zone pendant "+(x.duration()/20)+" secondes.");
                return 1;
            }
            say(p,"Aucun dépôt de mission disponible ici.");return 0;
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign plant failed",e);return 0;}
    }
    static void tickActions(net.minecraft.server.MinecraftServer server){
        if(ACTIONS.isEmpty())return;
        var iterator=ACTIONS.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();
            var p=server.getPlayerList().getPlayer(entry.getKey());
            var action=entry.getValue();
            if(p==null){iterator.remove();continue;}
            var x=site(action,p);
            UUID raid=ForestRaid.campaignRaidId(p);
            boolean valid=x!=null&&p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&ForestRaid.canContinue(p);
            var next=action.tick(raid,valid);
            if(next==null){iterator.remove();say(p,"Dépôt interrompu. Objet conservé.");continue;}
            if(!next.finished()){
                entry.setValue(next);
                if(next.elapsed()%20==0)p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Dépôt · "+(next.duration()-next.elapsed())/20+" s"),true);
                continue;
            }
            iterator.remove();
            try{
                var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),raid);
                if(!raid.equals(s.raidId())||s.player().state(x.quest(),level(p))!=State.ACTIVE)continue;
                var progress=s.player().snapshot().get(x.quest());
                if(progress.counters().getOrDefault(x.objective(),0)>0)continue;
                var storage=CarriedStorage.of(p);
                if(storage.count(stack->CampaignSupplies.matches(stack,x.item()))<1){say(p,"Dépôt annulé : objet manquant.");continue;}
                if(!s.player().record(x.quest(),x.objective(),1))continue;
                if(!storage.consume(stack->CampaignSupplies.matches(stack,x.item()),1))continue;
                CampaignStorage.save(p,CATALOG.revision(),s);
                say(p,label(x.quest())+" · dépôt terminé.");
            }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign action failed",e);}
        }
    }
    static int acceptSearch(ServerPlayer p){return accept(p,SEARCH);}
    static int claimSearch(ServerPlayer p){return claim(p,SEARCH);}
    static int acceptChemical(ServerPlayer p){return accept(p,CHEMICAL);}
    static int claimChemical(ServerPlayer p){return claim(p,CHEMICAL);}
    private static boolean eligible(ServerPlayer p,String id){
        var q=CATALOG.graph().quests().get(id);
        if(q==null||CampaignTraderAccount.loyalty(p,CampaignTrader.byId(q.trader()))<CATALOG.meta().get(id).loyalty())return false;
        if(id.equals(INTRO)&&!SupplyContract.account(p).getBoolean("campaign_dialogue_mechanic"))return false;
        if(id.equals(ULTRA)&&!SupplyContract.account(p).getBoolean("campaign_dialogue_ragman"))return false;
        return !id.equals(REVEALED)||CampaignTraderAccount.loyalty(p,CampaignTrader.THERAPIST)>=2;
    }
    static int search(ServerPlayer p,UUID raidId,String map){
        if(CATALOG==null||raidId==null||!p.isAlive()||p.isCreative()||p.isSpectator()||!ForestRaid.canContinue(p))return 0;
        String quest,find;
        if(map.equals("factory")&&near(p,ExpressZone.BUNKER.pos(new fr.taczoperations.worldgen.ForestPlan.Point(52,54)),3)){quest=REVEALED;find=SAMPLE_FIND;}
        else if(map.equals("woods")&&near(p,ForestLandmarks.position(new fr.taczoperations.worldgen.ForestPlan.Point(130,513)),5)){quest=INTRO;find=HUNT_FIND;}
        else{say(p,"Aucun objet de mission à cet endroit.");return 0;}
        try{
            var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),raidId);
            if(!raidId.equals(s.raidId())||s.player().state(quest,level(p))!=State.ACTIVE)return 0;
            var progress=s.player().snapshot().get(quest);
            if(progress.counters().getOrDefault(find,0)>0||s.player().checkpoint().pending().getOrDefault(quest,Map.of()).getOrDefault(find,0)>0){say(p,"Objet déjà récupéré.");return 0;}
            if(quest.equals(INTRO))s.player().record(quest,HUNT_VISIT,1);
            if(!s.player().record(quest,find,1))return 0;
            CampaignStorage.save(p,CATALOG.revision(),s);
            say(p,(quest.equals(INTRO)?"Message de Jaeger":"Échantillon chimique")+" récupéré. Extrais-toi vivant.");
            return 1;
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign search failed",e);return 0;}
    }
    static int acceptRevealed(ServerPlayer p){return accept(p,REVEALED);}
    static int claimRevealed(ServerPlayer p){return claim(p,REVEALED);}
    private static int level(ServerPlayer p){return MilitaryProgression.level(SupplyContract.experienceEarned(p));}
    private static boolean accessible(ServerPlayer p){
        return CATALOG!=null&&PlayerMarket.available(p)&&!ForestExpedition.hasReturnPoint(p)&&!p.isCreative()&&!p.isSpectator();
    }
    private static void say(ServerPlayer p,String text){p.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));}
    record QuestView(String id,String name,String trader,State state,int level,List<String> lines,boolean claimable){}
    static List<QuestView> views(ServerPlayer p){
        if(!accessible(p))return List.of();
        try{
            var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),null);
            var result=new ArrayList<QuestView>();
            var storage=CarriedStorage.of(p);
            for(var q:CATALOG.graph().quests().values()){
                if(!q.implemented())continue;
                var state=s.player().state(q.id(),level(p));
                if(state==State.AVAILABLE&&!eligible(p,q.id()))state=State.LOCKED;
                var progress=s.player().snapshot().get(q.id());
                var r=reward(q.id());
                var lines=new ArrayList<String>();
                lines.add("Niveau "+q.level()+" · Fidélité "+CATALOG.meta().get(q.id()).loyalty()+" · "+stateLabel(state));
                for(var o:q.objectives()){
                    int count=progress==null?0:progress.counters().getOrDefault(o.id(),0);
                    var refs=stockRefs(o);
                    if(!refs.isEmpty()&&state==State.ACTIVE)count=storage.count(stack->itemEligible(stack,o));
                    String title=o.id().equals(HUNT_VISIT)?"Cabane de chasse localisée":o.id().equals(HUNT_FIND)?"Message de Jaeger extrait":o.id().equals(HUNT_GIVE)?"Message remis":o.type().startsWith("turnin-any:")?"Provisions":o.type().endsWith(":any")?"Ennemis":o.id().equals(CONVOY)?"Convoi localisé":o.id().equals(CAMP)?"Camp localisé":o.id().equals(DEPOSIT)?"Tuyau annelé déposé":o.id().equals(SAMPLE_FIND)?"Échantillon chimique extrait":o.id().equals(SAMPLE_GIVE)?"Échantillon remis":refs.isEmpty()?"Scavs":o.amount()==5?"Fournitures d'entretien":"Combustibles et fournitures techniques";
                    title=CATALOG.meta().get(q.id()).labels().getOrDefault(o.id(),title);
                    lines.add(title+" : "+Math.min(count,o.amount())+"/"+o.amount());
                }
                if(q.id().equals(REVEALED))lines.add("Bunker · salle nord-ouest · fidélité Therapist 2");
                if(q.id().equals(INTRO))lines.add("Forêt Noire · cabane de chasse");
                if(q.id().equals(SEARCH))lines.add("Forêt Noire · convoi et camp");
                if(q.id().equals(CHEMICAL))lines.add("Zone industrielle · dépôt logistique ouest");
                if(q.id().equals(ULTRA))lines.add("Entrepôt");
                lines.add(r.credits()+" crédits · "+r.xp()+" XP · +"+r.reputation()/1000d+" réputation");
                boolean claimable=state==State.READY||state==State.ACTIVE&&canClaimPreview(p,s,q,storage);
                result.add(new QuestView(q.id(),label(q.id()),q.trader(),state,q.level(),List.copyOf(lines),claimable));
            }
            return List.copyOf(result);
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign preview failed",e);return List.of();}
    }
    private static boolean canClaimPreview(ServerPlayer p,CampaignStorage.Session s,Quest q,CarriedStorage storage){
        var trial=CATALOG.graph().restore(s.player().snapshot());
        var demands=new ArrayList<TurnInPlanner.Demand>();var stock=new TreeMap<String,Integer>();
        boolean fir=q.objectives().stream().anyMatch(o->o.type().startsWith("turnin-fir:"));
        if(fir&&q.objectives().stream().anyMatch(o->o.type().startsWith("turnin-any:")))return false;
        for(var o:q.objectives()){
            var refs=turnInRefs(o);if(refs.isEmpty())continue;
            demands.add(new TurnInPlanner.Demand(o.amount(),refs));
            for(var ref:refs)stock.computeIfAbsent(ref,key->storage.count(stack->(!fir||RaidOrigin.found(stack))&&CampaignSupplies.matches(stack,key)));
            trial.record(q.id(),o.id(),o.amount());
        }
        if(TurnInPlanner.plan(stock,demands)==null)return false;
        for(var o:q.objectives())if(findStock(o))trial.record(q.id(),o.id(),o.amount());
        if(q.id().equals(INTRO)||q.id().equals(REVEALED)){
            String find=q.id().equals(INTRO)?HUNT_FIND:SAMPLE_FIND,give=q.id().equals(INTRO)?HUNT_GIVE:SAMPLE_GIVE;
            if(trial.snapshot().get(q.id()).counters().getOrDefault(find,0)<1)return false;
            trial.record(q.id(),give,1);
        }
        return trial.state(q.id(),level(p))==State.READY;
    }
    static String stateLabel(State state){return switch(state){
        case AVAILABLE->"Disponible";case LOCKED->"Verrouillée";case ACTIVE->"En cours";case READY->"À valider";case COMPLETED->"Terminée";case FAILED->"Échouée";
    };}
    static int status(ServerPlayer p){
        if(!accessible(p)){say(p,"Campagne disponible hors raid, en survie.");return 0;}
        for(var q:views(p)){say(p,CampaignTrader.byId(q.trader()).title+" · "+q.name());for(var line:q.lines())say(p,line);}
        return 1;
    }
    static int acceptDebut(ServerPlayer p){return accept(p,DEBUT);}
    static int acceptFuel(ServerPlayer p){return accept(p,FUEL);}
    private static int accept(ServerPlayer p,String id){
        if(!accessible(p))return 0;
        try{
            var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),null);
            if(!eligible(p,id)){say(p,"Marchand, dialogue ou fidélité requis. Consulte /efm marchands.");return 0;}
            if(id.equals(SEARCH)&&p.getInventory().getFreeSlot()<0){say(p,"Libère une case pour la boussole.");return 0;}
            if(!s.player().accept(id,level(p))){say(p,"Quête indisponible ou déjà acceptée.");return 0;}
            CampaignStorage.save(p,CATALOG.revision(),s);
            if(id.equals(SEARCH))p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS));
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
            boolean fir=q.objectives().stream().anyMatch(o->o.type().startsWith("turnin-fir:"));
            if(fir&&q.objectives().stream().anyMatch(o->o.type().startsWith("turnin-any:")))return 0;
            for(var o:q.objectives()){
                var refs=turnInRefs(o);
                if(refs.isEmpty())continue;
                demands.add(new TurnInPlanner.Demand(o.amount(),refs));
                for(var ref:refs)stock.computeIfAbsent(ref,key->storage.count(stack->(!fir||RaidOrigin.found(stack))&&CampaignSupplies.matches(stack,key)));
            }
            var plan=TurnInPlanner.plan(stock,demands);
            if(plan==null){say(p,"Objets trouvés en raid insuffisants. Aucun objet remis.");return 0;}
            for(var o:q.objectives())if(!turnInRefs(o).isEmpty()||findStock(o))s.player().record(id,o.id(),o.amount());
            if(id.equals(REVEALED)||id.equals(INTRO)){
                String find=id.equals(INTRO)?HUNT_FIND:SAMPLE_FIND,give=id.equals(INTRO)?HUNT_GIVE:SAMPLE_GIVE;
                var progress=s.player().snapshot().get(id);
                if(progress==null||progress.counters().getOrDefault(find,0)<1){
                    say(p,"Récupère l'échantillon et extrais-toi vivant.");return 0;
                }
                s.player().record(id,give,1);
            }
            if(!s.player().complete(id,level(p))){say(p,"Objectifs incomplets.");return 0;}
            // No mutation until the entire allocation and reward have been validated on the server thread.
            for(var entry:plan.entrySet())
                if(!storage.consume(stack->(!fir||RaidOrigin.found(stack))&&CampaignSupplies.matches(stack,entry.getKey()),entry.getValue()))
                    throw new IllegalStateException("Inventory changed during turn-in");
            CampaignStorage.save(p,CATALOG.revision(),s);
            account.putInt("credits",credits+r.credits());
            account.putInt("contract_xp_bonus_v2",xp+r.xp());
            CampaignTraderAccount.applyReputation(p,CampaignTrader.byId(q.trader()),r.reputation());
            if(id.equals(INTRO))CampaignTraderAccount.unlockFromCompletedQuest(p,CampaignTrader.JAEGER,id);
            SupplyContract.rewardLevels(p);
            say(p,label(id)+" terminée · +"+r.credits()+" crédits · +"+r.xp()+" XP · +"+r.reputation()/1000d+" réputation.");
            return 1;
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign claim failed",e);return 0;}
    }
    private CampaignRuntime(){}
}
