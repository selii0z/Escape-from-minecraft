package fr.taczoperations.campaign;

import java.util.*;

/** Server-side campaign state machine. Minecraft event adapters and persistence are separate. */
public final class CampaignGraph {
    public enum State { AVAILABLE, LOCKED, ACTIVE, READY, COMPLETED, FAILED }
    public enum Scope { CAMPAIGN, SURVIVING_RAID, CURRENT_RAID }
    public enum Outcome { COMPLETED, FAILED }
    public record Requirement(String quest, Set<Outcome> outcomes) {
        public Requirement { Objects.requireNonNull(quest); outcomes=Set.copyOf(outcomes); if(outcomes.isEmpty())throw new IllegalArgumentException("Empty outcomes"); }
    }
    public record Objective(String id,String type,int amount,Scope scope,boolean optional) {
        public Objective {
            Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(scope);
            if(id.isBlank()||type.isBlank()||amount<=0)throw new IllegalArgumentException("Invalid objective");
        }
    }
    public record Quest(String id,String trader,int level,List<List<Requirement>> prerequisites,
                        List<Objective> objectives,Set<String> conflicts,boolean implemented) {
        public Quest {
            Objects.requireNonNull(id); Objects.requireNonNull(trader);
            prerequisites=prerequisites.stream().map(List::copyOf).toList();
            objectives=List.copyOf(objectives); conflicts=Set.copyOf(conflicts);
            if(id.isBlank()||trader.isBlank()||level<1||objectives.isEmpty()||objectives.stream().allMatch(Objective::optional))throw new IllegalArgumentException("Invalid quest "+id);
            if(objectives.stream().map(Objective::id).distinct().count()!=objectives.size())throw new IllegalArgumentException("Duplicate objective "+id);
            if(prerequisites.stream().anyMatch(List::isEmpty))throw new IllegalArgumentException("Empty prerequisite group "+id);
        }
    }
    /** Each outer prerequisite group is required; any entry within that group can satisfy it. */
    public record Progress(State state,Map<String,Integer> counters) {
        public Progress {
            if(state!=State.ACTIVE&&state!=State.COMPLETED&&state!=State.FAILED)throw new IllegalArgumentException("Non-persistent state");
            counters=Map.copyOf(counters);
            if(counters.values().stream().anyMatch(v->v<0))throw new IllegalArgumentException("Negative counter");
        }
    }
    private final Map<String,Quest> quests;
    public CampaignGraph(Collection<Quest> definitions) {
        var map=new LinkedHashMap<String,Quest>();
        for(var q:definitions)if(map.putIfAbsent(q.id(),q)!=null)throw new IllegalArgumentException("Duplicate quest "+q.id());
        for(var q:map.values()){
            for(var group:q.prerequisites())for(var r:group)if(!map.containsKey(r.quest())||r.quest().equals(q.id()))throw new IllegalArgumentException("Bad prerequisite "+q.id()+": "+r.quest());
            for(var conflict:q.conflicts())if(conflict.equals(q.id())||!map.containsKey(conflict)||!map.get(conflict).conflicts().contains(q.id()))throw new IllegalArgumentException("Asymmetric conflict "+q.id()+": "+conflict);
        }
        var visited=new HashSet<String>(); var visiting=new HashSet<String>();
        for(var id:map.keySet())visit(id,map,visited,visiting);
        quests=Collections.unmodifiableMap(map);
    }
    private static void visit(String id,Map<String,Quest> map,Set<String> visited,Set<String> visiting){
        if(visited.contains(id))return;
        if(!visiting.add(id))throw new IllegalArgumentException("Cyclic quest dependency: "+id);
        for(var group:map.get(id).prerequisites())for(var r:group)visit(r.quest(),map,visited,visiting);
        visiting.remove(id);visited.add(id);
    }
    public Map<String,Quest> quests(){return quests;}
    public Player newPlayer(){return new Player(Map.of());}
    public Player restore(Map<String,Progress> saved){return new Player(saved);}

    public final class Player {
        private final Map<String,Progress> saved=new HashMap<>();
        private final Map<String,Map<String,Integer>> raid=new HashMap<>();
        private boolean inRaid;
        private Player(Map<String,Progress> data){
            for(var entry:data.entrySet()){
                var q=require(entry.getKey());var p=entry.getValue();
                for(var count:p.counters().entrySet()){
                    var o=objective(q,count.getKey());
                    if(count.getValue()>o.amount())throw new IllegalArgumentException("Counter exceeds amount");
                }
                if(p.state()==State.COMPLETED&&!q.objectives().stream().filter(o->!o.optional()).allMatch(o->p.counters().getOrDefault(o.id(),0)>=o.amount()))throw new IllegalArgumentException("Incomplete completed quest");
                saved.put(q.id(),p);
            }
        }
        public Map<String,Progress> snapshot(){return Map.copyOf(saved);}
        private boolean satisfies(Requirement r){
            var p=saved.get(r.quest());
            return p!=null&&((p.state()==State.COMPLETED&&r.outcomes().contains(Outcome.COMPLETED))||(p.state()==State.FAILED&&r.outcomes().contains(Outcome.FAILED)));
        }
        public State state(String id,int level){
            var q=require(id);var p=saved.get(id);
            if(p!=null){
                if(p.state()!=State.ACTIVE)return p.state();
                return ready(q,p)?State.READY:State.ACTIVE;
            }
            if(!q.implemented()||level<q.level()||q.prerequisites().stream().anyMatch(g->g.stream().noneMatch(this::satisfies)))return State.LOCKED;
            for(var conflict:q.conflicts())if(saved.containsKey(conflict)&&saved.get(conflict).state()!=State.FAILED)return State.LOCKED;
            return State.AVAILABLE;
        }
        private boolean ready(Quest q,Progress p){
            return q.implemented()&&q.objectives().stream().filter(o->!o.optional()).allMatch(o->p.counters().getOrDefault(o.id(),0)>=o.amount());
        }
        public boolean accept(String id,int level){
            if(inRaid||state(id,level)!=State.AVAILABLE)return false;
            saved.put(id,new Progress(State.ACTIVE,Map.of()));return true;
        }
        public void beginRaid(){
            if(inRaid)throw new IllegalStateException("Raid already active");
            raid.clear();inRaid=true;
        }
        /** Call only from trusted server event handlers after checking every map/item/equipment predicate. */
        public boolean record(String id,String objective,int amount){
            var q=require(id);var o=objective(q,objective);var p=saved.get(id);
            if(amount<=0||!q.implemented()||p==null||p.state()!=State.ACTIVE)return false;
            if(o.scope()==Scope.CAMPAIGN){
                var next=new HashMap<>(p.counters());add(next,o,amount);saved.put(id,new Progress(State.ACTIVE,next));return true;
            }
            if(!inRaid)return false;
            add(raid.computeIfAbsent(id,k->new HashMap<>()),o,amount);return true;
        }
        /** Failed raids discard uncommitted progress. CURRENT_RAID must reach its full target in one surviving raid. */
        public void endRaid(boolean survived){
            if(!inRaid)return;
            if(survived)for(var e:raid.entrySet()){
                var p=saved.get(e.getKey());if(p==null||p.state()!=State.ACTIVE)continue;
                var q=require(e.getKey());var next=new HashMap<>(p.counters());
                for(var c:e.getValue().entrySet()){
                    var o=objective(q,c.getKey());
                    if(o.scope()==Scope.SURVIVING_RAID||o.scope()==Scope.CURRENT_RAID&&c.getValue()>=o.amount())add(next,o,c.getValue());
                }
                saved.put(q.id(),new Progress(State.ACTIVE,next));
            }
            raid.clear();inRaid=false;
        }
        /** Completion only changes quest state. The caller must atomically persist rewards with snapshot(). */
        public boolean complete(String id,int level){
            if(inRaid||state(id,level)!=State.READY)return false;
            var q=require(id);saved.put(id,new Progress(State.COMPLETED,saved.get(id).counters()));
            for(var conflict:q.conflicts()){
                var p=saved.get(conflict);
                if(p==null||p.state()==State.ACTIVE)saved.put(conflict,new Progress(State.FAILED,p==null?Map.of():p.counters()));
            }
            return true;
        }
        public boolean fail(String id){
            var p=saved.get(id);if(p==null||p.state()!=State.ACTIVE)return false;
            saved.put(id,new Progress(State.FAILED,p.counters()));raid.remove(id);return true;
        }
        private void add(Map<String,Integer> counters,Objective o,int amount){
            counters.put(o.id(),(int)Math.min(o.amount(),(long)counters.getOrDefault(o.id(),0)+amount));
        }
    }
    private Quest require(String id){
        var q=quests.get(id);if(q==null)throw new IllegalArgumentException("Unknown quest: "+id);return q;
    }
    private static Objective objective(Quest q,String id){
        return q.objectives().stream().filter(o->o.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown objective: "+id));
    }
}
