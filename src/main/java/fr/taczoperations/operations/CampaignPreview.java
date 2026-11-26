package fr.taczoperations.operations;

import com.google.gson.JsonParser;
import fr.taczoperations.campaign.CampaignGraph.State;
import fr.taczoperations.campaign.CampaignTrader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reference-only quests: never accepted, never persisted, never grant rewards. */
final class CampaignPreview {
    private static final System.Logger LOG=System.getLogger("taczoperations.campaign.preview");
    private static final String RESOURCE="/data/taczoperations/campaign/campaign-install-index.json";
    private static final List<CampaignRuntime.QuestView> INDEX=load();
    private static List<CampaignRuntime.QuestView> load(){
        try(var stream=CampaignPreview.class.getResourceAsStream(RESOURCE)){
            if(stream==null)return List.of();
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1||!root.get("mode").getAsString().equals("pve"))
                throw new IllegalArgumentException("Invalid reference catalogue");
            var list=new ArrayList<CampaignRuntime.QuestView>();
            var ids=new HashSet<String>();
            for(var entry:root.getAsJsonArray("quests")){
                var q=entry.getAsJsonObject();
                String id=q.get("id").getAsString();
                if(!id.matches("[0-9a-f]{24}")||!ids.add(id))throw new IllegalArgumentException("Duplicate or invalid quest ID");
                String trader=CampaignTrader.byId(q.get("trader").getAsString()).id;
                int level=q.get("level").getAsInt();
                if(level<1)throw new IllegalArgumentException("Invalid quest level");
                var lines=new ArrayList<String>();
                lines.add("Non jouable · adaptation en cours");
                lines.add("Niveau de référence : "+level);
                if(q.get("kappa").getAsBoolean())lines.add("Progression Kappa");
                int prerequisites=q.get("prerequisiteCount").getAsInt();
                if(prerequisites>0)lines.add(prerequisites+" prérequis");
                if(q.get("unresolvedPrerequisites").getAsInt()>0)lines.add("Prérequis à vérifier");
                for(var kind:q.getAsJsonObject("objectiveKinds").entrySet())
                    lines.add(kindLabel(kind.getKey())+" : "+kind.getValue().getAsInt()+" objectif(s)");
                list.add(new CampaignRuntime.QuestView(id,q.get("name").getAsString(),trader,State.LOCKED,level,List.copyOf(lines),false));
            }
            if(list.size()!=root.get("count").getAsInt())throw new IllegalArgumentException("Incomplete reference catalogue");
            return List.copyOf(list);
        }catch(Exception e){LOG.log(System.Logger.Level.WARNING,"Reference preview unavailable; playable campaign unchanged",e);return List.of();}
    }
    private static String kindLabel(String kind){
        return switch(kind){
            case "shoot"->"Combat";case "extract"->"Extraction";
            case "findItem","findQuestItem"->"Recherche";case "giveItem","giveQuestItem"->"Livraison";
            case "visit"->"Exploration";case "mark"->"Balisage";case "plantItem"->"Dépôt";
            case "buildWeapon"->"Modification d'équipement";
            default->"Objectif spécial";
        };
    }
    static List<CampaignRuntime.QuestView> merge(List<CampaignRuntime.QuestView> live){
        var ids=new HashSet<String>();
        var result=new ArrayList<CampaignRuntime.QuestView>();
        for(var q:live){ids.add(q.id());result.add(q);}
        for(var q:INDEX)if(ids.add(q.id()))result.add(q);
        return List.copyOf(result);
    }
    private CampaignPreview(){}
}
