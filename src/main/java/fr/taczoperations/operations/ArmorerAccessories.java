package fr.taczoperations.operations;

import com.tacz.guns.api.TimelessAPI;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** Curated game catalogue. Unknown and top-rarity attachments remain raid loot. */
final class ArmorerAccessories {
    record Entry(ResourceLocation id,String title,int tier,int price,RaidRelic key){}
    static List<Entry> catalogue(){
        var result=new ArrayList<Entry>();
        var counts=new HashMap<String,Integer>();
        var candidates=TimelessAPI.getAllCommonAttachmentIndex().stream()
            .filter(e->e.getValue().getType()!=null)
            .sorted(Comparator.comparing(e->e.getKey().toString())).toList();
        // Preserve existing access keys and prices when these items exist.
        for(var candidate:candidates){
            var id=candidate.getKey();
            if(!id.getNamespace().equals("tacz"))continue;
            if(id.getPath().equals("extended_mag_1"))result.add(new Entry(id,"Chargeur étendu I",2,460,RaidRelic.CIRCUIT));
            if(id.getPath().equals("sight_552"))result.add(new Entry(id,"Viseur 552",2,900,RaidRelic.OPTIC));
        }
        for(var candidate:candidates){
            var id=candidate.getKey();
            if(result.stream().anyMatch(e->e.id().equals(id)))continue;
            String type=candidate.getValue().getType().name();
            String name=id.getPath().toLowerCase(Locale.ROOT);
            int tier=tier(type,name);
            if(tier==0)continue;
            String group=type+"/"+tier;
            // Two entries per category and loyalty level, plus legacy offers.
            if(counts.getOrDefault(group,0)>=2)continue;
            counts.merge(group,1,Integer::sum);
            int price=switch(tier){case 1->180;case 2->480;case 3->1100;default->2600;};
            result.add(new Entry(id,name.replace('_',' ').replace('/',' '),tier,price,null));
        }
        result.sort(Comparator.comparingInt(Entry::tier).thenComparing(e->e.id().toString()));
        return List.copyOf(result);
    }
    private static int tier(String type,String id){
        // Classification of virtual TaCZ items only. No unknown accessory
        // is made buyable by default, and the best tier is reserved for loot.
        if(id.matches(".*(thermal|infrared|night_vision|legendary|prototype|boss|experimental|ammo_mod|overpressure|drum|extended_mag.*_3|extended_mag.*iii|scope.*(8x|10x|12x|16x)).*"))return 0;
        return switch(type){
            case "EXTENDED_MAG" -> id.matches(".*(extended_mag|magazine).*(^|_)(1|i)$")?2:
                id.matches(".*(extended_mag|magazine).*(^|_)(2|ii)$")?4:0;
            case "SCOPE" -> id.matches(".*(iron|reflex|red_dot|reddot|acro|okp|rmr).*")?1:
                id.matches(".*(552|holographic|holo|sight_1x).*")?2:
                id.matches(".*(acog|ta31|4x|3x|2x).*")?3:
                id.matches(".*(6x|lpvo|variable).*")?4:0;
            case "GRIP" -> id.matches(".*(rk_?6|rk6|basic|vertical).*")?1:
                id.matches(".*(angled|rk_?1|rk1|tactical).*")?2:
                id.matches(".*(b25|b_25|heavy|ranger).*")?3:0;
            case "STOCK" -> id.matches(".*(basic|wood|standard|factory|light).*")?1:
                id.matches(".*(tactical|izh|izhmash|magpul).*")?2:
                id.matches(".*(ctr|precision|marksman).*")?3:
                id.matches(".*(heavy|psg).*")?4:0;
            case "MUZZLE" -> id.matches(".*(flash|basic|choke).*")?1:
                id.matches(".*(brake|compensator).*")?2:
                id.matches(".*(silencer|suppressor).*")?3:0;
            case "LASER" -> id.matches(".*(laser|light).*")?2:0;
            default -> 0;
        };
    }
    private ArmorerAccessories(){}
}
