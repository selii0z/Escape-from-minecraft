package fr.taczoperations.operations;

import com.google.gson.*;
import fr.taczoperations.worldgen.ExpressZone;
import fr.taczoperations.campaign.CampaignGraph;
import fr.taczoperations.campaign.CampaignGraph.*;
import fr.taczoperations.campaign.CampaignTrader;
import fr.taczoperations.campaign.TraderRequirement;
import fr.taczoperations.campaign.TurnInPlanner;
import fr.taczoperations.campaign.StockObjectives;
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
    private static final String BLACK_SWAN="669fa39b91b0a8c9680fc467", CHUMMING="5b4795fb86f7745876267770", EASY_MONEY="6834145ebc1f443d7603c8a7";
    private static final String MARKER="5991b51486f77447b112d44f", POSTER="675a0288c3102563bd01c9c3", GOLD_CHAIN="5734758f24597738025ee253";
    private static final String BLACK_SWAN_MARK="669fb647a5be751be095da27", CHUMMING_CUSTOMS="5b4796c086f7745877352c2c", CHUMMING_WOODS="5b47971086f774587877ad34", EASY_MONEY_PLANT="6834145ebc1f443d7603c8ab";
    private static final String DEPOSIT="6a5c10e883a51e292d11b976", HOSE="59e35cbb86f7741778269d83";
    private static final String INTRO="5d2495a886f77425cd51e403", FILTH="600302d73b897b11364cd161", ULTRA="5ae448bf86f7744d733e55ee", ACQUAINTANCE="5d24b81486f77439c92d6ba8";
    private static final String HUNT_VISIT="63ac18f4972364554162a25c", HUNT_FIND="5d249a6e86f774791546e952", HUNT_GIVE="5d249aa286f77475e8376399";
    private record QuestSite(String map,int x,int z,int radius,String title,String find,String give){}
    private record Meta(String name,String slug,Reward reward,int loyalty,Map<String,String> labels,String dialogue,QuestSite site,List<TraderRequirement> traderRequirements,Map<String,Integer> starters){}
    private record Catalog(CampaignGraph graph,String revision,Map<String,Meta> meta){}
    private record Reward(int credits,int xp,int reputation,Map<String,Integer> items,Map<String,Integer> otherStanding,Map<PassiveSkills.Skill,Integer> skills){
        Reward(int credits,int xp,int reputation){this(credits,xp,reputation,Map.of(),Map.of(),Map.of());}
    }
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
    private static final Set<String> MAPS=Set.of("*","woods","customs","factory","interchange","village","streets");
    private static final Catalog CATALOG=load();

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
                    var r=q.getAsJsonObject("reward");
                    var itemRewards=new TreeMap<String,Integer>();
                    if(r.has("items"))for(var e:r.getAsJsonObject("items").entrySet()){
                        int count=e.getValue().getAsInt();
                        if(count<=0||!CampaignSupplies.rewardKnown(e.getKey()))throw new IllegalArgumentException("Invalid material reward");
                        itemRewards.put(e.getKey(),count);
                    }
                    var standing=new TreeMap<String,Integer>();
                    if(r.has("otherStanding"))for(var e:r.getAsJsonObject("otherStanding").entrySet()){
                        var owner=CampaignTrader.byId(e.getKey());int delta=e.getValue().getAsInt();
                        if(owner.id.equals(trader)||delta< -100000||delta>100000)throw new IllegalArgumentException("Invalid secondary standing");
                        standing.put(owner.id,delta);
                    }
                    var skills=new EnumMap<PassiveSkills.Skill,Integer>(PassiveSkills.Skill.class);
                    if(r.has("skills"))for(var e:r.getAsJsonObject("skills").entrySet()){
                        int count=e.getValue().getAsInt();
                        if(count<1||count>50)throw new IllegalArgumentException("Invalid skill reward");
                        skills.put(PassiveSkills.Skill.valueOf(e.getKey()),count);
                    }
                    reward=new Reward(r.get("credits").getAsInt(),r.get("xp").getAsInt(),r.get("reputation").getAsInt(),Map.copyOf(itemRewards),Map.copyOf(standing),Map.copyOf(skills));
                    if(reward.credits()<0||reward.xp()<0||reward.reputation()< -100000||reward.reputation()>100000)throw new IllegalArgumentException("Invalid reward");
                }
                int loyalty=q.has("loyalty")?q.get("loyalty").getAsInt():1;
                if(loyalty<0||loyalty>4)throw new IllegalArgumentException("Invalid loyalty");
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
                if(q.has("adapter")&&q.get("adapter").getAsString().equals("bulk-stock-v2")){
                    reviewed=q.get("implemented").getAsBoolean()&&q.has("review")&&q.get("review").getAsString().equals("approved")
                        &&q.has("blockers")&&q.getAsJsonArray("blockers").isEmpty()&&reward!=null&&StockObjectives.valid(objectives);
                }
                if(q.has("adapter")&&q.get("adapter").getAsString().equals("bulk-combat-v1")){
                    reviewed=q.get("implemented").getAsBoolean()&&q.has("review")&&q.get("review").getAsString().equals("approved")
                        &&q.has("blockers")&&q.getAsJsonArray("blockers").isEmpty()&&reward!=null&&combatObjectivesValid(objectives);
                }
                String dialogue=q.has("dialogueTrader")&&!q.get("dialogueTrader").isJsonNull()?CampaignTrader.byId(q.get("dialogueTrader").getAsString()).id:null;
                QuestSite site=null;
                if(q.has("adapter")&&Set.of("quest-item-v1","quest-item-city-v1").contains(q.get("adapter").getAsString())){
                    var adapted=q.getAsJsonObject("adaptation");
                    String map=adapted.get("map").getAsString();
                    int x=adapted.get("localX").getAsInt(),z=adapted.get("localZ").getAsInt(),radius=adapted.get("radius").getAsInt();
                    var finds=objectives.stream().filter(o->o.type().startsWith("quest-find:")).toList();
                    var gives=objectives.stream().filter(o->o.type().startsWith("quest-give:")).toList();
                    boolean city=q.get("adapter").getAsString().equals("quest-item-city-v1");
                    boolean coordinates=city?map.equals("streets")&&x>=0&&x<2&&z==0:
                        Set.of("woods","customs","factory").contains(map)&&x>=0&&z>=0
                        &&x<(map.equals("factory")?224:map.equals("customs")?650:850)&&z<(map.equals("factory")?224:map.equals("customs")?650:850);
                    boolean valid=finds.size()==1&&gives.size()==1&&coordinates&&radius>=1&&radius<=8;
                    if(valid){
                        var find=finds.getFirst();var give=gives.getFirst();
                        valid=find.type().matches("quest-find:"+map+":[0-9a-f]{24}")&&give.type().equals("quest-give:"+find.type().substring(find.type().lastIndexOf(':')+1))
                            &&find.amount()==1&&give.amount()==1&&find.scope()==Scope.SURVIVING_RAID&&give.scope()==Scope.CAMPAIGN&&!find.optional()&&!give.optional()
                            &&objectives.stream().allMatch(o->o==find||o==give||o.type().equals("extract:"+map)&&o.amount()==1&&o.scope()==Scope.SURVIVING_RAID&&!o.optional());
                        if(valid)site=new QuestSite(map,x,z,radius,adapted.get("site").getAsString(),find.id(),give.id());
                    }
                    reviewed=valid&&q.get("implemented").getAsBoolean()&&reward!=null&&prereqs.isEmpty()&&conflicts.isEmpty()
                        &&(city?Set.of("639135d89444fb141f4e6eea","638fcd23dc65553116701d33").contains(id):Set.of("596a0e1686f7741ddf17dbee","669fa399033a3ce9870338a8","675c1570526ff496850895d9").contains(id))
                        &&(dialogue==null||dialogue.equals(trader));
                }
                if(id.equals("66b38e144f2ab7cc530c3fe7")&&trader.equals("jaeger")&&prereqs.isEmpty()&&conflicts.isEmpty()){
                    reviewed=q.get("implemented").getAsBoolean()&&reward!=null&&objectives.equals(List.of(
                        new Objective("66b38e144f2ab7cc530c3fe8","visit:factory:mine-check",1,Scope.CAMPAIGN,false),
                        new Objective("66b38e144f2ab7cc530c3fea","visit:customs:mine-check",1,Scope.CAMPAIGN,false)));
                }
                if(q.has("adapter")&&q.get("adapter").getAsString().equals("field-deposit-v2")){
                    var expected=switch(id){
                        case BLACK_SWAN->List.of(new Objective(BLACK_SWAN_MARK,"plant:factory:black-swan:"+MARKER,1,Scope.CAMPAIGN,false));
                        case CHUMMING->List.of(new Objective(CHUMMING_CUSTOMS,"plant:customs:chumming:"+GOLD_CHAIN,3,Scope.CAMPAIGN,false),
                            new Objective(CHUMMING_WOODS,"plant:woods:chumming:"+GOLD_CHAIN,3,Scope.CAMPAIGN,false));
                        case EASY_MONEY->List.of(new Objective(EASY_MONEY_PLANT,"plant:customs:arena-poster:"+POSTER,1,Scope.CAMPAIGN,false));
                        default->List.<Objective>of();
                    };
                    String owner=switch(id){case BLACK_SWAN->"mechanic";case CHUMMING,EASY_MONEY->"skier";default->"";};
                    reviewed=q.get("implemented").getAsBoolean()&&reward!=null&&prereqs.isEmpty()&&conflicts.isEmpty()
                        &&trader.equals(owner)&&!expected.isEmpty()&&objectives.equals(expected)&&starterItems(id).keySet().stream().allMatch(CampaignSupplies::rewardKnown);
                }
                var traderRequirements=new ArrayList<TraderRequirement>();
                if(q.has("traderRequirements"))for(var valueReq:q.getAsJsonArray("traderRequirements")){
                    var req=valueReq.getAsJsonObject();
                    var t=CampaignTrader.byId(req.get("trader").getAsString());
                    var kind=TraderRequirement.Kind.valueOf(req.get("kind").getAsString());
                    var operator=TraderRequirement.Operator.valueOf(req.get("operator").getAsString());
                    traderRequirements.add(new TraderRequirement(t.id,kind,operator,req.get("value").getAsInt()));
                }
                var starters=new TreeMap<String,Integer>();
                if(q.has("starterItems"))for(var e:q.getAsJsonObject("starterItems").entrySet()){
                    int count=e.getValue().getAsInt();
                    if(count<=0||!CampaignSupplies.rewardKnown(e.getKey()))throw new IllegalArgumentException("Invalid starter item");
                    starters.put(e.getKey(),count);
                }
                String name=q.get("name").getAsString();
                String slug=name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");
                if(meta.values().stream().anyMatch(x->x.slug().equals(slug)))throw new IllegalArgumentException("Duplicate quest slug");
                meta.put(id,new Meta(name,slug,reward,loyalty,Map.copyOf(labels),dialogue,site,List.copyOf(traderRequirements),Map.copyOf(starters)));
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
        if(CATALOG==null||ScavRun.active(p)||p.isCreative()||p.isSpectator())return;
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
    private static final String TOUGH_GUY="5d25cf2686f77443e75488d4";
    private static final String HEALED_RAID="efm_campaign_healed_raid";
    static void medicalTreatment(ServerPlayer p){
        UUID raidId=ForestRaid.campaignRaidId(p);
        if(raidId==null||ScavRun.active(p)||!ForestRaid.isParticipant(p)||!"woods".equals(ForestRaid.campaignMap(p)))return;
        p.getPersistentData().putUUID(HEALED_RAID,raidId);
        change(p,raidId,s->{
            if(!raidId.equals(s.raidId()))return null;
            s.player().discardPending(TOUGH_GUY);return s;
        });
    }
    static void kill(ServerPlayer p,UUID raidId,String map,GuardRole role,UUID enemy,String escortOf){
        if(!ForestRaid.isParticipant(p)||!p.isAlive())return;
        change(p,raidId,s->{
            if(!raidId.equals(s.raidId()))return null;
            boolean reset=false;
            if(role!=GuardRole.PMC&&role!=GuardRole.KNIGHT&&role!=GuardRole.BIG_PIPE&&role!=GuardRole.BIRDEYE)
                reset|=s.player().resetActive("66631489acf8442f8b05319f");
            if(role.boss())reset|=s.player().resetActive("6663149196a9349baa021baa");
            if(reset)say(p,"Défi de chasse : progression remise à zéro après une élimination interdite.");
            var data=p.getPersistentData();
            boolean healed=data.hasUUID(HEALED_RAID)&&raidId.equals(data.getUUID(HEALED_RAID));
            boolean noheal=!healed&&map.equals("woods")&&matches("kill:woods:scav","kill",map,role,enemy,escortOf)
                &&record(s,"noheal",map,role,enemy,escortOf);
            return (record(s,"kill",map,role,enemy,escortOf)|reset|noheal)?s:null;
        });
    }
    static boolean resetFailedRaidChallenges(CampaignGraph.Player player){
        boolean reset=false;
        for(String id:List.of("5bc4893c86f774626f5ebf3e","5c0be13186f7746f016734aa"))
            reset|=player.resetActive(id);
        return reset;
    }
    static void end(ServerPlayer p,UUID raidId,String map,boolean success){
        ACTIONS.remove(p.getUUID());
        if(raidId==null)return;
        change(p,raidId,s->{
            if(!raidId.equals(s.raidId()))return null;
            if(success)record(s,"extract",map,null,null,"");
            s.player().endRaid(success);
            if(!success&&resetFailedRaidChallenges(s.player()))say(p,"Défis de survie : progression remise à zéro après un raid échoué.");
            return new CampaignStorage.Session(s.player(),null);
        });
    }
    static void gunEvent(ServerPlayer p,UUID raidId,String map,GuardRole role,String mode,String part,net.minecraft.resources.ResourceLocation weapon,double distance,UUID enemy,String escortOf){
        if(raidId==null||!Double.isFinite(distance)||distance<0)return;
        change(p,raidId,s->{
            if(!raidId.equals(s.raidId()))return null;
            boolean changed=false;
            for(var q:CATALOG.graph().quests().values()){
                var progress=s.player().snapshot().get(q.id());
                if(!q.implemented()||progress==null||progress.state()!=State.ACTIVE)continue;
                for(var o:q.objectives()){
                    if(!o.type().startsWith("gunshot:")||progress.counters().getOrDefault(o.id(),0)>=o.amount())continue;
                    var a=o.type().split(":",-1);
                    if((a.length<8||a.length>10)||a.length>=9&&a[8].equals("night")&&!ForestExpedition.isNightRaid(p)||a.length==10&&!CampaignGunEvents.matchesGear(p,a[9])||!matches("kill:"+a[1]+":"+a[2],"kill",map,role,enemy,escortOf)||!a[3].equals(mode)
                        ||!a[4].equals("any")&&!a[4].equals(part)||!CampaignGunEvents.matchesWeapon(a[5],weapon))continue;
                    int target=Integer.parseInt(a[7]);
                    boolean range=switch(a[6]){
                        case "ge"->distance>=target;case "le"->distance<=target;
                        case "gt"->distance>target;case "lt"->distance<target;default->false;
                    };
                    if(range)changed|=s.player().record(q.id(),o.id(),1);
                }
            }
            return changed?s:null;
        });
    }
    static void recordEquipmentSale(ServerPlayer p,int quantity){
        if(quantity<=0||!accessible(p))return;
        change(p,null,s->{
            var q=CATALOG.graph().quests().get("6663149f1d3ec95634095e75");
            if(q==null||!q.implemented())return null;
            var progress=s.player().snapshot().get(q.id());
            if(progress==null||progress.state()!=State.ACTIVE)return null;
            return s.player().record(q.id(),"666973ee1d80fbbbfeaf46c9",quantity)?s:null;
        });
    }
    private static boolean record(CampaignStorage.Session s,String event,String map,GuardRole role,UUID enemy,String escortOf){
        boolean changed=false;
        for(var q:CATALOG.graph().quests().values()){
            if(!q.implemented())continue;
            var progress=s.player().snapshot().get(q.id());
            if(progress==null||progress.state()!=State.ACTIVE)continue;
            for(var objective:q.objectives()){
                if(matches(objective.type(),event,map,role,enemy,escortOf))changed|=s.player().record(q.id(),objective.id(),1);
            }
        }
        return changed;
    }
    // Normalized explicit rules: kill:<map>:<role> or extract:<map>.
    // Unrecognized predicates are never treated as unrestricted objectives.
    static boolean matches(String type,String event,String map,GuardRole role){
        return matches(type,event,map,role,null,"");
    }
    static boolean matches(String type,String event,String map,GuardRole role,UUID enemy,String escortOf){
        var parts=type.split(":",-1);
        if(parts.length<2||!parts[0].equals(event))return false;
        var allowed=Arrays.asList(parts[1].split(",",-1));
        if(!MAPS.containsAll(allowed)||!(allowed.contains("*")||allowed.contains(map)))return false;
        if(event.equals("extract"))return parts.length==2;
        if(!(event.equals("kill")||event.equals("noheal"))||parts.length!=3||role==null)return false;
        return switch(parts[2]){
            case "any"->true;
            case "pmc"->role==GuardRole.PMC;
            case "usec"->role==GuardRole.PMC&&enemy!=null&&(enemy.getLeastSignificantBits()&1)==0;
            case "bear"->role==GuardRole.PMC&&enemy!=null&&(enemy.getLeastSignificantBits()&1)==1;
            case "boss"->role.boss();
            case "classic_boss"->Set.of(GuardRole.KABAN,GuardRole.RESHALA,GuardRole.GLUKHAR,GuardRole.KILLA,GuardRole.TRACKER,GuardRole.KOLLONTAY,GuardRole.SANITAR,GuardRole.FOREMAN,GuardRole.ZRYACHIY,GuardRole.VENGEFUL_KILLA,GuardRole.SHADOW_TAGILLA,GuardRole.INFECTED_TAGILLA).contains(role);
            case "cultist"->role==GuardRole.CULTIST_PRIEST||"CULTIST_PRIEST".equals(escortOf);
            case "reshala_escort"->"RESHALA".equals(escortOf)&&!role.boss();
            case "boss_escort"->!role.boss()&&Set.of("RESHALA","GLUKHAR","TRACKER","KABAN","KOLLONTAY","SANITAR").contains(escortOf);
            case "goons"->role==GuardRole.KNIGHT||role==GuardRole.BIG_PIPE||role==GuardRole.BIRDEYE;
            case "scav"->role==GuardRole.RAIDER||role==GuardRole.SCOUT||role==GuardRole.SOLDIER||role==GuardRole.MARKSMAN;
            default->parts[2].equals(role.name().toLowerCase(Locale.ROOT));
        };
    }
    /** Strict event adapter: no implicit wildcard targets or unsupported map restrictions. */
    private static boolean combatObjectivesValid(List<Objective> objectives){
        var stock=objectives.stream().filter(o->StockObjectives.find(o)||StockObjectives.give(o)).toList();
        if(!stock.isEmpty()&&!StockObjectives.valid(stock))return false;
        int combat=0;
        for(var o:objectives){
            if(StockObjectives.find(o)||StockObjectives.give(o))continue;
            String[] parts=o.type().split(":",-1);
            if(o.optional())return false;
            if(o.type().equals("pay-credits")){
                if(o.scope()!=Scope.CAMPAIGN||o.amount()<=0)return false;
                combat++;continue;
            }
            if(parts.length<2)return false;
            if(o.type().equals("sale:field-equipment")){
                if(o.scope()!=Scope.CAMPAIGN||o.amount()!=250||!o.id().equals("666973ee1d80fbbbfeaf46c9"))return false;
                combat++;continue;
            }
            if(parts[0].equals("milestone")){
                if(!milestoneValid(o))return false;
                combat++;continue;
            }
            if(parts[0].equals("quest-give-adapted")){
                if(parts.length!=2||!parts[1].matches("[0-9a-f]{24}")||o.scope()!=Scope.CAMPAIGN||o.amount()!=1
                    ||objectives.stream().filter(f->f.type().startsWith("quest-find-adapted:")&&f.type().endsWith(":"+parts[1])).count()!=1)return false;
                combat++;continue;
            }
            var maps=Arrays.asList(parts[1].split(",",-1));
            if(!MAPS.containsAll(maps)||maps.contains("*")&&maps.size()!=1)return false;
            if(parts[0].equals("noheal")){
                if(!o.id().equals("5d25d09286f77444001e284c")||!o.type().equals("noheal:woods:scav")||o.scope()!=Scope.CURRENT_RAID||o.amount()!=3)return false;
            }else if(parts[0].equals("gunshot")){
                if((parts.length<8||parts.length>10)||parts.length>=9&&!Set.of("night","anytime").contains(parts[8])||parts.length==10&&!Set.of("any","uniform2","armor2","noarmor","unarmored","heavyuniform","injured","novision","scavuniform","headset_noarmor","helmet_noheadset","elite_rig").contains(parts[9])||o.scope()!=Scope.CAMPAIGN||!Set.of("hit","kill").contains(parts[3])
                    ||!Set.of("any","head","legs").contains(parts[4])||!Set.of("any","bolt","pistol","ak","western","rifle","smg","shotgun","lmg","civilianlong").contains(parts[5])
                    ||!Set.of("ge","le","gt","lt").contains(parts[6])||!parts[7].matches("[0-9]{1,4}")
                    ||Integer.parseInt(parts[7])>1000)return false;
                String role=parts[2];
                if(!Set.of("any","pmc","scav","boss","usec","bear","reshala_escort","goons","boss_escort","cultist","classic_boss").contains(role)&&Arrays.stream(GuardRole.values())
                    .noneMatch(x->x.name().toLowerCase(Locale.ROOT).equals(role)))return false;
            }else if(parts[0].equals("quest-find-adapted")){
                if(parts.length!=4||maps.size()!=1||maps.contains("*")||!parts[2].matches("[0-9a-f]{16}")||
                    !parts[3].matches("[0-9a-f]{24}")||o.scope()!=Scope.SURVIVING_RAID||o.amount()!=1||
                    objectives.stream().filter(g->g.type().equals("quest-give-adapted:"+parts[3])).count()!=1)return false;
            }else if(parts[0].equals("visit-adapted")||parts[0].equals("plant-adapted")){
                boolean plant=parts[0].equals("plant-adapted");
                if(parts.length!=(plant?4:3)||maps.size()!=1||maps.contains("*")||!parts[2].matches("[0-9a-f]{16}")
                    ||o.scope()!=Scope.CAMPAIGN||(!plant&&o.amount()!=1)
                    ||(plant&&(!parts[3].matches("[0-9a-f]{24}")||!CampaignSupplies.rewardKnown(parts[3]))))return false;
            }else if(parts[0].equals("extract")){
                if(parts.length!=2||o.scope()!=Scope.SURVIVING_RAID)return false;
            }else if(parts[0].equals("kill")){
                if(parts.length!=3||o.scope()!=Scope.CAMPAIGN)return false;
                String role=parts[2];
                if(!Set.of("any","pmc","scav","boss","usec","bear","reshala_escort","goons","boss_escort","cultist","classic_boss").contains(role)&&Arrays.stream(GuardRole.values())
                    .noneMatch(x->x.name().toLowerCase(Locale.ROOT).equals(role)))return false;
            }else return false;
            combat++;
        }
        return combat>0;
    }
    private static boolean pairedStockValid(List<Objective> objectives){return StockObjectives.valid(objectives);}
    private static boolean findStock(Objective o){return StockObjectives.find(o);}
    private static Set<String> stockRefs(Objective o){return StockObjectives.refs(o);}
    private static Set<String> turnInRefs(Objective o){return StockObjectives.give(o)?StockObjectives.refs(o):Set.of();}
    /** Both the menu and the actual claim use the same physical stock allocation. */
    private static boolean stockCondition(Quest q,net.minecraft.world.item.ItemStack stack){
        if(!q.id().equals("60e71ccb5688f6424c7bfec4"))return true;
        if(!MilitaryTag.isTag(stack))return false;
        var custom=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if(custom==null)return false;
        var data=custom.copyTag();
        return data.hasUUID("operator")&&data.getInt("rank")>=20&&data.getInt("rank")<=50;
    }
    private static Map<String,Integer> planTurnIns(Quest q,CarriedStorage storage){
        var demands=new ArrayList<TurnInPlanner.Demand>();
        var stock=new TreeMap<String,Integer>();
        var refs=new TreeSet<String>();
        for(var o:q.objectives())if(StockObjectives.give(o)){
            demands.add(StockObjectives.demand(o));refs.addAll(StockObjectives.refs(o));
        }
        for(var ref:refs){
            int found=storage.count(stack->stockCondition(q,stack)&&RaidOrigin.found(stack)&&CampaignSupplies.matches(stack,ref));
            int ordinary=storage.count(stack->stockCondition(q,stack)&&!RaidOrigin.found(stack)&&CampaignSupplies.matches(stack,ref));
            stock.put(StockObjectives.key(ref,true),found);
            stock.put(StockObjectives.key(ref,false),ordinary);
        }
        return TurnInPlanner.plan(stock,demands);
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
        if(CATALOG==null||ScavRun.active(p)||!ForestRaid.canContinue(p)||!p.isAlive())return;
        visitAdapted(p,raidId,map);
        if(map.equals("factory")||map.equals("customs")){
            String id="66b38e144f2ab7cc530c3fe7";
            if(!CATALOG.graph().quests().containsKey(id))return;
            String objective=map.equals("factory")?"66b38e144f2ab7cc530c3fe8":"66b38e144f2ab7cc530c3fea";
            var point=map.equals("factory")?ExpressZone.BUNKER.pos(new fr.taczoperations.worldgen.ForestPlan.Point(112,177)):
                new net.minecraft.core.BlockPos(fr.taczoperations.worldgen.IndustrialZone.ORIGIN+350,fr.taczoperations.worldgen.IndustrialZone.FLOOR+1,fr.taczoperations.worldgen.IndustrialZone.ORIGIN+520);
            if(!near(p,point,8))return;
            change(p,raidId,s->{
                if(!raidId.equals(s.raidId())||s.player().state(id,level(p))!=State.ACTIVE)return null;
                if(s.player().snapshot().get(id).counters().getOrDefault(objective,0)>0)return null;
                if(!s.player().record(id,objective,1))return null;
                say(p,"Every Hunter Knows This · secteur inspecté.");return s;
            });
            return;
        }
        if(!map.equals("woods"))return;
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
    // Generic field adapters use known encounter anchors, not Tarkov's unrelated coordinates.
    private static net.minecraft.core.BlockPos fieldAnchor(String map,String key){
        List<fr.taczoperations.worldgen.ForestPlan.Point> points;
        ExpressZone express=null;
        switch(map){
            case "woods" -> points=fr.taczoperations.worldgen.ForestRaidAnchors.ENEMIES;
            case "customs" -> points=fr.taczoperations.worldgen.IndustrialZone.GUARDS;
            case "factory" -> {express=ExpressZone.BUNKER;points=express.encounters();}
            case "interchange" -> {express=ExpressZone.WAREHOUSE;points=express.encounters();}
            case "village" -> {express=ExpressZone.VILLAGE;points=express.encounters();}
            case "streets" -> {express=ExpressZone.CITY;if(!express.available())return null;points=express.encounters();}
            default -> {return null;}
        }
        if(express!=null){
            final ExpressZone area=express;
            points=points.stream().filter(area::valid).distinct().toList();
        }
        if(points.isEmpty())return null;
        int n=(int)Long.remainderUnsigned(Long.parseUnsignedLong(key,16),points.size());
        var point=points.get(n);
        return express!=null?express.pos(point):map.equals("woods")?ForestLandmarks.position(point):industrialPoint(point.x(),point.z());
    }
    private static void visitAdapted(ServerPlayer p,UUID raidId,String map){
        change(p,raidId,s->{
            if(!raidId.equals(s.raidId()))return null;
            boolean changed=false;
            for(var q:CATALOG.graph().quests().values()){
                var progress=s.player().snapshot().get(q.id());
                if(!q.implemented()||progress==null||progress.state()!=State.ACTIVE)continue;
                for(var o:q.objectives()){
                    if(!o.type().startsWith("visit-adapted:"+map+":")||progress.counters().getOrDefault(o.id(),0)>=o.amount())continue;
                    var point=fieldAnchor(map,o.type().split(":")[2]);
                    if(point!=null&&near(p,point,6)&&s.player().record(q.id(),o.id(),1)){
                        changed=true;say(p,label(q.id())+" · secteur reconnu.");
                    }
                }
            }
            return changed?s:null;
        });
    }
    // Server thread only; each player can perform one uninterrupted terrain action.
    private record Site(String quest,String objective,String map,String item,net.minecraft.core.BlockPos pos,int radius,int duration){}
    private static final Map<UUID,FieldAction> ACTIONS=new HashMap<>();
    private static net.minecraft.core.BlockPos industrialPoint(int x,int z){
        return new net.minecraft.core.BlockPos(fr.taczoperations.worldgen.IndustrialZone.ORIGIN+x,
            fr.taczoperations.worldgen.IndustrialZone.FLOOR+1,fr.taczoperations.worldgen.IndustrialZone.ORIGIN+z);
    }
    private static final List<Site> SITES=List.of(
        new Site(CHEMICAL,DEPOSIT,"customs",HOSE,industrialPoint(95,425),3,200),
        new Site(BLACK_SWAN,BLACK_SWAN_MARK,"factory",MARKER,
            ExpressZone.BUNKER.pos(new fr.taczoperations.worldgen.ForestPlan.Point(112,112)),4,200),
        new Site(CHUMMING,CHUMMING_CUSTOMS,"customs",GOLD_CHAIN,industrialPoint(350,520),5,200),
        new Site(CHUMMING,CHUMMING_WOODS,"woods",GOLD_CHAIN,
            ForestLandmarks.position(new fr.taczoperations.worldgen.ForestPlan.Point(645,225)),6,200),
        new Site(EASY_MONEY,EASY_MONEY_PLANT,"customs",POSTER,industrialPoint(125,540),5,200));
    private static List<Site> allSites(){
        var result=new ArrayList<>(SITES);
        if(CATALOG!=null)for(var q:CATALOG.graph().quests().values()){
            if(!q.implemented())continue;
            for(var o:q.objectives()){
                if(!o.type().startsWith("plant-adapted:"))continue;
                var parts=o.type().split(":");
                var point=fieldAnchor(parts[1],parts[2]);
                if(point!=null)result.add(new Site(q.id(),o.id(),parts[1],parts[3],point,5,200));
            }
        }
        return result;
    }
    private static Map<String,Integer> starterItems(String id){
        return switch(id){
            case BLACK_SWAN->Map.of(MARKER,1);
            case CHUMMING->Map.of(GOLD_CHAIN,4);
            case EASY_MONEY->Map.of(POSTER,3);
            default->CATALOG==null||!CATALOG.meta().containsKey(id)?Map.of():CATALOG.meta().get(id).starters();
        };
    }
    private static int fieldTarget(Site x){
        var q=CATALOG.graph().quests().get(x.quest());
        if(q==null||!q.implemented())return 0;
        return q.objectives().stream().filter(o->o.id().equals(x.objective())).mapToInt(Objective::amount).findFirst().orElse(0);
    }
    private static String supplyTitle(String id){
        var stack=CampaignSupplies.rewardStack(id);
        return stack.isEmpty()?id:stack.getHoverName().getString();
    }
    private static Site site(FieldAction action,ServerPlayer p){
        return allSites().stream().filter(x->x.quest().equals(action.quest())&&x.objective().equals(action.objective())
            &&x.map().equals(action.map())&&x.item().equals(action.item())&&near(p,x.pos(),x.radius())).findFirst().orElse(null);
    }
    static int plant(ServerPlayer p,UUID raidId,String map){
        if(CATALOG==null||ScavRun.active(p)||raidId==null||!ForestRaid.canContinue(p)||!p.isAlive()||p.isCreative()||p.isSpectator())return 0;
        if(ACTIONS.containsKey(p.getUUID())){say(p,"Une action de terrain est déjà en cours.");return 0;}
        try{
            var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),raidId);
            if(!raidId.equals(s.raidId()))return 0;
            for(var x:allSites()){
                if(!x.map().equals(map)||!near(p,x.pos(),x.radius()))continue;
                int target=fieldTarget(x);
                if(target<=0||s.player().state(x.quest(),level(p))!=State.ACTIVE)continue;
                var progress=s.player().snapshot().get(x.quest());
                if(progress.counters().getOrDefault(x.objective(),0)>=target)continue;
                if(CarriedStorage.of(p).count(stack->CampaignSupplies.matches(stack,x.item()))<1){
                    say(p,"Objet de mission requis : "+supplyTitle(x.item()));return 0;
                }
                ACTIONS.put(p.getUUID(),new FieldAction(raidId,x.quest(),x.objective(),x.map(),x.item(),x.duration(),0));
                say(p,(x.item().equals(MARKER)?"Balisage":"Dépôt")+" en cours · reste dans la zone pendant "+(x.duration()/20)+" secondes.");
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
                int target=fieldTarget(x);
                if(target<=0||!raid.equals(s.raidId())||s.player().state(x.quest(),level(p))!=State.ACTIVE)continue;
                var progress=s.player().snapshot().get(x.quest());
                int previous=progress.counters().getOrDefault(x.objective(),0);
                if(previous>=target)continue;
                var storage=CarriedStorage.of(p);
                if(storage.count(stack->CampaignSupplies.matches(stack,x.item()))<1){say(p,"Dépôt annulé : objet manquant.");continue;}
                if(!s.player().record(x.quest(),x.objective(),1))continue;
                if(!storage.consume(stack->CampaignSupplies.matches(stack,x.item()),1))continue;
                CampaignStorage.save(p,CATALOG.revision(),s);
                say(p,label(x.quest())+" · "+(x.item().equals(MARKER)?"balise posée":"objet déposé")+" ("+(previous+1)+"/"+target+").");
            }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign action failed",e);}
        }
    }
    static int acceptSearch(ServerPlayer p){return accept(p,SEARCH);}
    static int claimSearch(ServerPlayer p){return claim(p,SEARCH);}
    static int acceptChemical(ServerPlayer p){return accept(p,CHEMICAL);}
    static int claimChemical(ServerPlayer p){return claim(p,CHEMICAL);}
    private static boolean eligible(ServerPlayer p,String id){
        var q=CATALOG.graph().quests().get(id);
        if(q==null)return false;
        for(var o:q.objectives()){
            // A city-only mission cannot be completed without the private city resources.
            // Multi-map alternatives remain available on the other maps.
            var type=o.type().split(":",-1);
            if(type.length>=2&&Set.of("kill","extract","gunshot","visit-adapted","plant-adapted","quest-find-adapted","quest-find").contains(type[0])
                &&type[1].equals("streets")&&!ExpressZone.CITY.available())return false;
            if(o.type().startsWith("visit-adapted:")||o.type().startsWith("plant-adapted:")||o.type().startsWith("quest-find-adapted:")){
                var parts=o.type().split(":");
                if(fieldAnchor(parts[1],parts[2])==null)return false;
            }
        }
        var questSite=CATALOG.meta().get(id).site();
        if(questSite!=null&&questSite.map().equals("streets")&&sitePos(questSite)==null)return false;
        var owner=CampaignTrader.byId(q.trader());
        if(!CampaignTraderAccount.unlocked(p,owner)||CampaignTraderAccount.loyalty(p,owner)<CATALOG.meta().get(id).loyalty())return false;
        for(var req:CATALOG.meta().get(id).traderRequirements()){
            var trader=CampaignTrader.byId(req.trader());
            if(!req.matches(CampaignTraderAccount.loyalty(p,trader),CampaignTraderAccount.reputation(p,trader)))return false;
        }
        // EFM uses direct quest acceptance; dialogue is not a progression requirement.
        return !id.equals(REVEALED)||CampaignTraderAccount.loyalty(p,CampaignTrader.THERAPIST)>=2;
    }
    private static net.minecraft.core.BlockPos sitePos(QuestSite site){
        if(site.map().equals("streets")){
            var anchors=ExpressZone.CITY.encounters().stream().filter(ExpressZone.CITY::valid)
                .distinct().sorted(Comparator.comparingInt(fr.taczoperations.worldgen.ForestPlan.Point::x)
                    .thenComparingInt(fr.taczoperations.worldgen.ForestPlan.Point::z)).toList();
            if(anchors.size()<2)return null;
            var anchor=anchors.get(site.x()==0?0:anchors.size()-1);
            return ExpressZone.CITY.pos(anchor);
        }
        var point=new fr.taczoperations.worldgen.ForestPlan.Point(site.x(),site.z());
        return switch(site.map()){
            case "woods"->ForestLandmarks.position(point);
            case "factory"->ExpressZone.BUNKER.pos(point);
            case "customs"->new net.minecraft.core.BlockPos(fr.taczoperations.worldgen.IndustrialZone.ORIGIN+site.x(),fr.taczoperations.worldgen.IndustrialZone.FLOOR+1,fr.taczoperations.worldgen.IndustrialZone.ORIGIN+site.z());
            default->throw new IllegalArgumentException("Unreviewed map");
        };
    }
    static int search(ServerPlayer p,UUID raidId,String map){
        if(CATALOG==null||ScavRun.active(p)||raidId==null||!p.isAlive()||p.isCreative()||p.isSpectator()||!ForestRaid.canContinue(p))return 0;
        try{
            var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),raidId);
            if(!raidId.equals(s.raidId()))return 0;
            for(var q:CATALOG.graph().quests().values()){
                if(!q.implemented()||s.player().state(q.id(),level(p))!=State.ACTIVE)continue;
                for(var o:q.objectives()){
                    if(!o.type().startsWith("quest-find-adapted:"+map+":"))continue;
                    var parts=o.type().split(":");
                    var position=fieldAnchor(map,parts[2]);
                    if(position==null||!near(p,position,5))continue;
                    var progress=s.player().snapshot().get(q.id());
                    if(progress.counters().getOrDefault(o.id(),0)>0||
                        s.player().checkpoint().pending().getOrDefault(q.id(),Map.of()).getOrDefault(o.id(),0)>0)continue;
                    if(!s.player().record(q.id(),o.id(),1))continue;
                    CampaignStorage.save(p,CATALOG.revision(),s);
                    say(p,label(q.id())+" · objet de mission récupéré. Extrais-toi vivant.");
                    return 1;
                }
                QuestSite site=CATALOG.meta().get(q.id()).site();
                if(q.id().equals(REVEALED))site=new QuestSite("factory",52,54,3,"Échantillon chimique",SAMPLE_FIND,SAMPLE_GIVE);
                if(q.id().equals(INTRO))site=new QuestSite("woods",130,513,5,"Message de Jaeger",HUNT_FIND,HUNT_GIVE);
                if(site==null||!site.map().equals(map))continue;
                var position=sitePos(site);
                if(position==null||!near(p,position,site.radius()))continue;
                String find=site.find();var progress=s.player().snapshot().get(q.id());
                if(progress.counters().getOrDefault(find,0)>0||s.player().checkpoint().pending().getOrDefault(q.id(),Map.of()).getOrDefault(find,0)>0)continue;
                if(q.id().equals(INTRO))s.player().record(q.id(),HUNT_VISIT,1);
                if(!s.player().record(q.id(),find,1))continue;
                CampaignStorage.save(p,CATALOG.revision(),s);
                say(p,label(q.id())+" · objet récupéré. Extrais-toi vivant.");
                return 1;
            }
            say(p,"Aucun objet de mission actif à cet endroit.");return 0;
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign search failed",e);return 0;}
    }
    private static boolean handOverQuestItem(CampaignGraph.Player player,Quest q){
        var saved=player.snapshot().get(q.id());
        for(var give:q.objectives()){
            if(!give.type().startsWith("quest-give-adapted:"))continue;
            String ref=give.type().substring("quest-give-adapted:".length());
            var find=q.objectives().stream().filter(o->o.type().startsWith("quest-find-adapted:")&&o.type().endsWith(":"+ref)).findFirst().orElse(null);
            if(find==null||saved==null||saved.counters().getOrDefault(find.id(),0)<1)return false;
        }
        for(var give:q.objectives()){
            if(give.type().startsWith("quest-give-adapted:")&&saved.counters().getOrDefault(give.id(),0)<1
                &&!player.record(q.id(),give.id(),1))return false;
        }
        QuestSite site=CATALOG.meta().get(q.id()).site();
        if(q.id().equals(INTRO))site=new QuestSite("woods",130,513,5,"",HUNT_FIND,HUNT_GIVE);
        if(q.id().equals(REVEALED))site=new QuestSite("factory",52,54,3,"",SAMPLE_FIND,SAMPLE_GIVE);
        if(site==null)return true;
        var progress=player.snapshot().get(q.id());
        if(progress==null||progress.counters().getOrDefault(site.find(),0)<1)return false;
        if(progress.counters().getOrDefault(site.give(),0)>=1)return true;
        return player.record(q.id(),site.give(),1);
    }
    static int acceptRevealed(ServerPlayer p){return accept(p,REVEALED);}
    static int claimRevealed(ServerPlayer p){return claim(p,REVEALED);}
    private static int level(ServerPlayer p){return MilitaryProgression.level(SupplyContract.experienceEarned(p));}
    private static boolean accessible(ServerPlayer p){
        return CATALOG!=null&&PlayerMarket.available(p)&&!ForestExpedition.hasReturnPoint(p)&&!p.isCreative()&&!p.isSpectator();
    }
    private static void say(ServerPlayer p,String text){p.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));}
    record QuestView(String id,String name,String trader,State state,int level,List<String> lines,boolean claimable){}
    static Set<String> completedQuests(ServerPlayer p){
        if(!accessible(p))return Set.of();
        try{
            var s=CampaignStorage.load(p,CATALOG.graph(),CATALOG.revision(),null);
            var done=new HashSet<String>();
            s.player().snapshot().forEach((id,progress)->{
                if(progress.state()==State.COMPLETED)done.add(id);
            });
            return Set.copyOf(done);
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign offer unlock check failed",e);return Set.of();}
    }
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
                    if(state==State.ACTIVE&&o.type().startsWith("milestone:"))count=milestoneReached(p,s.player(),o)?1:0;
                    if(state==State.ACTIVE&&o.type().equals("pay-credits"))count=Math.max(0,SupplyContract.account(p).getInt("credits"));
                    var refs=stockRefs(o);
                    if(!refs.isEmpty()&&state==State.ACTIVE)count=storage.count(stack->stockCondition(q,stack)&&itemEligible(stack,o));
                    String title=o.id().equals(HUNT_VISIT)?"Cabane de chasse localisée":o.id().equals(HUNT_FIND)?"Message de Jaeger extrait":o.id().equals(HUNT_GIVE)?"Message remis":o.type().startsWith("turnin-any:")?"Provisions":o.type().endsWith(":any")?"Ennemis":o.id().equals(CONVOY)?"Convoi localisé":o.id().equals(CAMP)?"Camp localisé":o.id().equals(DEPOSIT)?"Tuyau annelé déposé":o.id().equals(SAMPLE_FIND)?"Échantillon chimique extrait":o.id().equals(SAMPLE_GIVE)?"Échantillon remis":refs.isEmpty()?"Scavs":o.amount()==5?"Fournitures d'entretien":"Combustibles et fournitures techniques";
                    title=CATALOG.meta().get(q.id()).labels().getOrDefault(o.id(),title);
                    lines.add(title+" : "+Math.min(count,o.amount())+"/"+o.amount());
                }
                if(q.id().equals(REVEALED))lines.add("Bunker · salle nord-ouest · fidélité Therapist 2");
                if(q.id().equals(INTRO))lines.add("Forêt Noire · cabane de chasse");
                if(q.id().equals(SEARCH))lines.add("Forêt Noire · convoi et camp");
                if(q.id().equals(CHEMICAL))lines.add("Zone industrielle · dépôt logistique ouest");
                if(q.id().equals(ULTRA))lines.add("Entrepôt");
                for(var o:q.objectives())if(o.type().startsWith("quest-find-adapted:")){
                    var parts=o.type().split(":");var point=fieldAnchor(parts[1],parts[2]);
                    if(point!=null)lines.add("Objet de mission · "+parts[1]+" · X "+point.getX()+" Z "+point.getZ()+" · /efm fouiller");
                }
                for(var o:q.objectives())if(o.type().startsWith("visit-adapted:")){
                    var parts=o.type().split(":");var point=fieldAnchor(parts[1],parts[2]);
                    if(point!=null)lines.add("Secteur à reconnaître · "+parts[1]+" · X "+point.getX()+" Z "+point.getZ());
                }
                for(var field:allSites())if(field.quest().equals(q.id())&&!q.id().equals(CHEMICAL)){
                    lines.add((field.item().equals(MARKER)?"Baliser":"Déposer")+" : "+supplyTitle(field.item())+" · "+field.map());
                    lines.add("Coordonnées de raid : "+field.pos().getX()+", "+field.pos().getY()+", "+field.pos().getZ());
                }
                if(!starterItems(q.id()).isEmpty()&&state==State.AVAILABLE)lines.add("Matériel de départ fourni via la réserve");
                var site=CATALOG.meta().get(q.id()).site();if(site!=null){
                    lines.add(site.title()+" · fouiller puis extraire");
                    if(site.map().equals("streets")){
                        var position=sitePos(site);
                        if(position!=null)lines.add("Coordonnées de raid : "+position.getX()+", "+position.getY()+", "+position.getZ());
                        else lines.add("Zombiepolis indisponible");
                    }
                }
                lines.add(r.credits()+" crédits · "+r.xp()+" XP · "+(r.reputation()>=0?"+":"")+r.reputation()/1000d+" réputation");
                if(!r.items().isEmpty())lines.add(r.items().values().stream().mapToLong(Integer::longValue).sum()+" objet(s) en récompense");
                r.skills().forEach((skill,amount)->lines.add(skill.title+" : +"+amount+" niveau(x)"));
                for(var standing:r.otherStanding().entrySet())lines.add(CampaignTrader.byId(standing.getKey()).title+" : "+(standing.getValue()>=0?"+":"")+standing.getValue()/1000d+" réputation");
                boolean claimable=state==State.READY||state==State.ACTIVE&&canClaimPreview(p,s,q,storage);
                result.add(new QuestView(q.id(),label(q.id()),q.trader(),state,q.level(),List.copyOf(lines),claimable));
            }
            return List.copyOf(result);
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign preview failed",e);return List.of();}
    }
    private static boolean milestoneValid(Objective o){
        if(o.scope()!=Scope.CAMPAIGN||o.amount()!=1||o.optional())return false;
        String[] parts=o.type().split(":",-1);
        try{
            if(parts.length==3&&parts[1].equals("task"))return parts[2].matches("[0-9a-f]{24}");
            if(parts.length!=4)return false;
            int value=Integer.parseInt(parts[3]);
            return switch(parts[1]){
                case "loyalty"->CampaignTrader.byId(parts[2])!=null&&value>=1&&value<=4;
                case "reputation"->CampaignTrader.byId(parts[2])!=null&&value>=-100000&&value<=100000;
                case "skill"->PassiveSkills.Skill.valueOf(parts[2])!=null&&value>=1&&value<=50;
                default->false;
            };
        }catch(RuntimeException e){return false;}
    }
    private static boolean milestoneReached(ServerPlayer p,CampaignGraph.Player progress,Objective o){
        if(!milestoneValid(o))return false;
        String[] parts=o.type().split(":");
        if(parts[1].equals("task")){
            var other=progress.snapshot().get(parts[2]);
            return other!=null&&other.state()==State.COMPLETED;
        }
        int value=Integer.parseInt(parts[3]);
        return switch(parts[1]){
            case "loyalty"->CampaignTraderAccount.loyalty(p,CampaignTrader.byId(parts[2]))>=value;
            case "reputation"->CampaignTraderAccount.reputation(p,CampaignTrader.byId(parts[2]))>=value;
            case "skill"->PassiveSkills.level(p,PassiveSkills.Skill.valueOf(parts[2]))>=value;
            default->false;
        };
    }
    private static boolean markMilestones(ServerPlayer p,CampaignGraph.Player player,Quest q){
        for(var o:q.objectives())if(o.type().startsWith("milestone:")&&!milestoneReached(p,player,o))return false;
        for(var o:q.objectives())if(o.type().startsWith("milestone:")){
            var progress=player.snapshot().get(q.id());
            if(progress==null)return false;
            if(progress.counters().getOrDefault(o.id(),0)==0&&!player.record(q.id(),o.id(),1))return false;
        }
        return true;
    }
    private static long creditCost(Quest q){
        return q.objectives().stream().filter(o->o.type().equals("pay-credits")).mapToLong(Objective::amount).sum();
    }
    private static boolean recordPayment(CampaignGraph.Player player,Quest q){
        for(var o:q.objectives())if(o.type().equals("pay-credits")){
            var progress=player.snapshot().get(q.id());
            if(progress==null)return false;
            if(progress.counters().getOrDefault(o.id(),0)<o.amount()&&!player.record(q.id(),o.id(),o.amount()))return false;
        }
        return true;
    }
    private static boolean canClaimPreview(ServerPlayer p,CampaignStorage.Session s,Quest q,CarriedStorage storage){
        var trial=CATALOG.graph().restore(s.player().snapshot());
        if(planTurnIns(q,storage)==null||creditCost(q)>Math.max(0,SupplyContract.account(p).getInt("credits"))||!recordPayment(trial,q))return false;
        for(var o:q.objectives())if(StockObjectives.give(o)||findStock(o))trial.record(q.id(),o.id(),o.amount());
        if(!markMilestones(p,trial,q)||!handOverQuestItem(trial,q))return false;
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
            if(!eligible(p,id)){say(p,"Marchand ou fidélité requis. Consulte /efm marchands.");return 0;}
            if(id.equals(SEARCH)&&p.getInventory().getFreeSlot()<0){say(p,"Libère une case pour la boussole.");return 0;}
            var starters=starterItems(id);
            if(!CampaignRewardInbox.canQueue(p,starters)){say(p,"Réserve de matériel saturée.");return 0;}
            if(!s.player().accept(id,level(p))){say(p,"Quête indisponible ou déjà acceptée.");return 0;}
            CampaignStorage.save(p,CATALOG.revision(),s);
            if(id.equals(SEARCH))p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS));
            CampaignRewardInbox.queue(p,starters);
            if(!starters.isEmpty())say(p,"Matériel de départ disponible dans la réserve des marchands.");
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
            long cost=creditCost(q);
            if(cost>Math.max(0,credits)){say(p,"Crédits insuffisants · "+cost+" requis. Aucun débit.");return 0;}
            if((long)credits-cost+r.credits()>Integer.MAX_VALUE||(long)xp+r.xp()>Integer.MAX_VALUE){say(p,"Solde maximal atteint.");return 0;}
            if(!CampaignRewardInbox.canQueue(p,r.items())){say(p,"Réserve de récompenses saturée ou objet indisponible.");return 0;}
            var storage=CarriedStorage.of(p);
            var plan=planTurnIns(q,storage);
            if(plan==null){say(p,"Objets requis insuffisants. Aucun objet remis.");return 0;}
            for(var o:q.objectives())if(!turnInRefs(o).isEmpty()||findStock(o))s.player().record(id,o.id(),o.amount());
            if(!recordPayment(s.player(),q))return 0;
            if(!markMilestones(p,s.player(),q)){say(p,"Conditions de niveau, fidélité ou mission non remplies.");return 0;}
            if(!handOverQuestItem(s.player(),q)){say(p,"Récupère l’objet de mission et extrais-toi vivant.");return 0;}
            if(!s.player().complete(id,level(p))){say(p,"Objectifs incomplets.");return 0;}
            // No mutation until the entire allocation and reward have been validated on the server thread.
            for(var entry:plan.entrySet()){
                String reference=StockObjectives.reference(entry.getKey());
                boolean found=StockObjectives.foundBucket(entry.getKey());
                if(!storage.consume(stack->stockCondition(q,stack)&&RaidOrigin.found(stack)==found&&CampaignSupplies.matches(stack,reference),entry.getValue()))
                    throw new IllegalStateException("Inventory changed during turn-in");
            }
            CampaignStorage.save(p,CATALOG.revision(),s);
            account.putInt("credits",(int)(credits-cost+r.credits()));
            if(cost>0)say(p,"Paiement de mission · "+cost+" crédits.");
            account.putInt("contract_xp_bonus_v2",xp+r.xp());
            CampaignTraderAccount.applyReputation(p,CampaignTrader.byId(q.trader()),r.reputation());
            r.otherStanding().forEach((owner,delta)->CampaignTraderAccount.applyReputation(p,CampaignTrader.byId(owner),delta));
            CampaignRewardInbox.queue(p,r.items());
            r.skills().forEach((skill,amount)->PassiveSkills.questReward(p,skill,amount));
            if(!r.items().isEmpty())say(p,"Récompenses matérielles disponibles dans la réserve des marchands.");
            if(id.equals(INTRO))CampaignTraderAccount.unlockFromCompletedQuest(p,CampaignTrader.JAEGER,id);
            if(id.equals(EASY_MONEY))CampaignTraderAccount.unlockFromCompletedQuest(p,CampaignTrader.REF,id);
            SupplyContract.rewardLevels(p);
            say(p,label(id)+" terminée · +"+r.credits()+" crédits · +"+r.xp()+" XP · +"+r.reputation()/1000d+" réputation.");
            return 1;
        }catch(RuntimeException e){LOG.log(System.Logger.Level.ERROR,"Campaign claim failed",e);return 0;}
    }
    private CampaignRuntime(){}
}
