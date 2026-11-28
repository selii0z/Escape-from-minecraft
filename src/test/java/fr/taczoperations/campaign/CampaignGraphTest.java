package fr.taczoperations.campaign;
import java.util.*;
import static fr.taczoperations.campaign.CampaignGraph.*;

public final class CampaignGraphTest {
    static void check(boolean condition){if(!condition)throw new AssertionError();}
    static Quest q(String id,List<List<Requirement>> requirements,List<Objective> objectives,Set<String> conflicts,boolean implemented){
        return new Quest(id,"prapor",1,requirements,objectives,conflicts,implemented);
    }
    public static void main(String[] args){
        var kills=new Objective("kills","kill",3,Scope.CAMPAIGN,false);
        var loot=new Objective("loot","find",2,Scope.SURVIVING_RAID,false);
        var single=new Objective("single","visit",2,Scope.CURRENT_RAID,false);
        var requirement=new Requirement("a",Set.of(Outcome.COMPLETED));
        var graph=new CampaignGraph(List.of(
            q("a",List.of(),List.of(kills,loot,single),Set.of(),true),
            q("b",List.of(List.of(requirement)),List.of(kills),Set.of("c"),true),
            q("c",List.of(List.of(requirement)),List.of(kills),Set.of("b"),true),
            q("unimplemented",List.of(),List.of(kills),Set.of(),false)));
        var p=graph.newPlayer();
        check(p.state("unimplemented",1)==State.LOCKED);
        check(!p.accept("b",1));check(p.accept("a",1));check(!p.accept("a",1));
        p.beginRaid();p.record("a","kills",3);p.record("a","loot",2);p.record("a","single",2);p.endRaid(false);
        check(p.snapshot().get("a").counters().equals(Map.of("kills",3)));
        for(int i=0;i<2;i++){p.beginRaid();p.record("a","loot",1);p.record("a","single",1);p.endRaid(true);}
        check(p.state("a",1)==State.ACTIVE);
        p.beginRaid();p.record("a","single",2);
        // A reconnect during the same raid must retain its pending objectives.
        var checkpoint=p.checkpoint();
        var resumed=graph.restore(checkpoint);
        check(resumed.checkpoint().inRaid());
        check(resumed.checkpoint().pending().get("a").get("single")==2);
        resumed.endRaid(true);
        p=resumed;
        check(p.state("a",1)==State.READY);check(p.complete("a",1));check(!p.complete("a",1));
        var restored=graph.restore(p.snapshot());check(restored.state("b",1)==State.AVAILABLE);
        check(restored.accept("b",1));check(!restored.accept("c",1));
        restored.record("b","kills",Integer.MAX_VALUE);check(restored.complete("b",1));check(restored.state("c",1)==State.FAILED);
        try{
            new CampaignGraph(List.of(q("x",List.of(List.of(new Requirement("y",Set.of(Outcome.COMPLETED)))),List.of(kills),Set.of(),true)));
            throw new AssertionError("Missing dependency accepted");
        }catch(IllegalArgumentException expected){}
        try{
            new CampaignGraph(List.of(
                q("x",List.of(List.of(new Requirement("y",Set.of(Outcome.COMPLETED)))),List.of(kills),Set.of(),true),
                q("y",List.of(List.of(new Requirement("x",Set.of(Outcome.COMPLETED)))),List.of(kills),Set.of(),true)));
            throw new AssertionError("Cycle accepted");
        }catch(IllegalArgumentException expected){}
        var demands=List.of(new TurnInPlanner.Demand(1,Set.of("a","b")),new TurnInPlanner.Demand(1,Set.of("a")));
        check(TurnInPlanner.plan(Map.of("a",1,"b",1),demands).equals(Map.of("a",1,"b",1)));
        check(TurnInPlanner.plan(Map.of("a",1),demands)==null);
        check(TurnInPlanner.plan(Map.of("a",5),List.of(new TurnInPlanner.Demand(3,Set.of("a")))).equals(Map.of("a",3)));
        check(TurnInPlanner.plan(Map.of(),List.of()).isEmpty());
        var sampleGraph=new CampaignGraph(List.of(q("sample",List.of(),List.of(
            new Objective("find","quest-find",1,Scope.SURVIVING_RAID,false),
            new Objective("give","quest-give",1,Scope.CAMPAIGN,false)),Set.of(),true)));
        var sample=sampleGraph.newPlayer();
        check(sample.accept("sample",1));
        sample.beginRaid();sample.record("sample","find",1);sample.endRaid(false);
        check(sample.snapshot().get("sample").counters().isEmpty());
        check(!sample.complete("sample",1));
        sample.beginRaid();sample.record("sample","find",1);
        var reconnected=sampleGraph.restore(sample.checkpoint());reconnected.endRaid(true);
        check(reconnected.snapshot().get("sample").counters().get("find")==1);
        check(!reconnected.complete("sample",1));
        reconnected.record("sample","give",1);check(reconnected.complete("sample",1));
        check(!reconnected.complete("sample",1));
        var actionRaid=java.util.UUID.randomUUID();
        var action=new FieldAction(actionRaid,"q","o","woods","item",200,0);
        for(int tick=0;tick<199;tick++)action=action.tick(actionRaid,true);
        check(!action.finished());
        check(action.tick(actionRaid,false)==null);
        check(action.tick(java.util.UUID.randomUUID(),true)==null);
        check(action.tick(actionRaid,true).finished());
        var activeGraph=new CampaignGraph(List.of(q("parent",List.of(),List.of(kills),Set.of(),true),
            q("child",List.of(List.of(new Requirement("parent",Set.of(Outcome.ACTIVE)))),List.of(kills),Set.of(),true)));
        var activePlayer=activeGraph.newPlayer();
        check(activePlayer.state("child",1)==State.LOCKED);
        check(activePlayer.accept("parent",1));check(activePlayer.state("child",1)==State.AVAILABLE);
        activePlayer.record("parent","kills",Integer.MAX_VALUE);
        check(activePlayer.state("child",1)==State.AVAILABLE);
        check(activePlayer.complete("parent",1));check(activePlayer.state("child",1)==State.LOCKED);
        var ll=new TraderRequirement("therapist",TraderRequirement.Kind.LEVEL,TraderRequirement.Operator.GE,2);
        check(!ll.matches(1,1000));check(ll.matches(2,0));
        var rep=new TraderRequirement("fence",TraderRequirement.Kind.REPUTATION,TraderRequirement.Operator.LT,-1000);
        check(rep.matches(1,-1001));check(!rep.matches(1,-1000));
        String refA="aaaaaaaaaaaaaaaaaaaaaaaa",refB="bbbbbbbbbbbbbbbbbbbbbbbb";
        var first=new Objective("find","find-stock-fir:"+refA+","+refB,1,Scope.CAMPAIGN,false);
        var firGive=new Objective("fir","turnin-fir:"+refB+","+refA,1,Scope.CAMPAIGN,false);
        var anyGive=new Objective("any","turnin-any:"+refA,1,Scope.CAMPAIGN,false);
        check(StockObjectives.valid(List.of(first,anyGive,firGive)));
        check(!StockObjectives.valid(List.of(first,anyGive)));
        var mixed=List.of(StockObjectives.demand(firGive),StockObjectives.demand(anyGive));
        check(TurnInPlanner.plan(Map.of(StockObjectives.key(refA,true),1),mixed)==null);
        check(TurnInPlanner.plan(Map.of(StockObjectives.key(refA,true),1,StockObjectives.key(refA,false),1),mixed)
            .equals(Map.of(StockObjectives.key(refA,true),1,StockObjectives.key(refA,false),1)));
        // Additive catalogue upgrades preserve definition signatures and saved counters.
        var oldQuest=q("stable",List.of(),List.of(kills,loot),Set.of(),true);
        var oldGraph=new CampaignGraph(List.of(oldQuest));
        var oldPlayer=oldGraph.newPlayer();
        check(oldPlayer.accept("stable",1));oldPlayer.record("stable","kills",2);
        var expanded=new CampaignGraph(List.of(
            q("stable",List.of(),List.of(loot,kills),Set.of(),true),
            q("new",List.of(List.of(new Requirement("stable",Set.of(Outcome.COMPLETED)))),List.of(kills),Set.of(),true)));
        check(oldPlayer.definitionFingerprint("stable").equals(expanded.definitionFingerprint("stable")));
        var upgraded=expanded.restore(oldPlayer.snapshot());
        check(upgraded.snapshot().get("stable").counters().get("kills")==2);
        check(upgraded.state("new",1)==State.LOCKED);
        var changed=new CampaignGraph(List.of(q("stable",List.of(),List.of(
            new Objective("kills","kill",4,Scope.CAMPAIGN,false),loot),Set.of(),true)));
        check(!oldPlayer.definitionFingerprint("stable").equals(changed.definitionFingerprint("stable")));
        // Multiple deposits at each distinct site must survive subsequent raids.
        var deposits=new CampaignGraph(List.of(q("deposit",List.of(),List.of(
            new Objective("site-a","plant:customs",3,Scope.CAMPAIGN,false),
            new Objective("site-b","plant:woods",3,Scope.CAMPAIGN,false)),Set.of(),true)));
        var depositor=deposits.newPlayer();check(depositor.accept("deposit",1));
        for(int i=0;i<3;i++){depositor.beginRaid();check(depositor.record("deposit","site-a",1));depositor.endRaid(i!=1);}
        check(depositor.snapshot().get("deposit").counters().get("site-a")==3);
        check(depositor.state("deposit",1)==State.ACTIVE);
        depositor=deposits.restore(depositor.snapshot());
        depositor.beginRaid();check(depositor.record("deposit","site-b",2));depositor.endRaid(true);
        check(!depositor.complete("deposit",1));
        depositor.beginRaid();check(depositor.record("deposit","site-b",1));depositor.endRaid(true);
        check(depositor.complete("deposit",1));check(!depositor.complete("deposit",1));
        System.out.println("Campaign graph, turn-in, quest-item, field deposit and upgrade checks passed");
    }
}
