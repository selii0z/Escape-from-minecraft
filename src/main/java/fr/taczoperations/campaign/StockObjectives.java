package fr.taczoperations.campaign;

import java.util.*;
import fr.taczoperations.campaign.CampaignGraph.*;

/** Shared adapter for arbitrary item alternatives and mixed FIR/non-FIR handovers.
 * Each physical unit belongs to exactly one stock bucket. */
public final class StockObjectives {
    public static boolean find(Objective o){
        return o.type().startsWith("find-stock-fir:")||o.type().startsWith("find-stock-any:");
    }
    public static boolean give(Objective o){
        return o.type().startsWith("turnin-fir:")||o.type().startsWith("turnin-any:");
    }
    public static boolean fir(Objective o){
        return o.type().startsWith("find-stock-fir:")||o.type().startsWith("turnin-fir:");
    }
    public static Set<String> refs(Objective o){
        if(!find(o)&&!give(o))return Set.of();
        var result=new TreeSet<String>();
        for(var id:o.type().substring(o.type().indexOf(':')+1).split(",",-1)){
            if(!id.matches("[0-9a-f]{24}")||!result.add(id))throw new IllegalArgumentException("Invalid stock reference");
        }
        return Set.copyOf(result);
    }
    public static boolean valid(List<Objective> objectives){
        if(objectives.isEmpty()||objectives.stream().noneMatch(StockObjectives::give))return false;
        for(var o:objectives){
            if((!find(o)&&!give(o))||o.optional()||o.scope()!=Scope.CAMPAIGN||refs(o).isEmpty())return false;
            if(find(o)&&objectives.stream().noneMatch(g->give(g)&&fir(g)==fir(o)
                &&g.amount()==o.amount()&&refs(g).equals(refs(o))))return false;
        }
        return true;
    }
    public static String key(String ref,boolean fir){return (fir?"fir:":"any:")+ref;}
    public static TurnInPlanner.Demand demand(Objective o){
        if(!give(o))throw new IllegalArgumentException("Not a handover objective");
        var accepted=new HashSet<String>();
        for(var ref:refs(o)){
            accepted.add(key(ref,true));
            if(!fir(o))accepted.add(key(ref,false));
        }
        return new TurnInPlanner.Demand(o.amount(),accepted);
    }
    public static String reference(String key){
        if(!key.matches("(fir|any):[0-9a-f]{24}"))throw new IllegalArgumentException("Invalid stock key");
        return key.substring(4);
    }
    public static boolean foundBucket(String key){return key.startsWith("fir:");}
    private StockObjectives(){}
}
