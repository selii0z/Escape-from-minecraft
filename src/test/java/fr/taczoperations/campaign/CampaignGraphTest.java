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
        System.out.println("Campaign graph, turn-in and quest-item checks passed");
    }
}
