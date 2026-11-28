package fr.taczoperations.campaign;

import java.util.Locale;
import java.util.Arrays;
import java.util.List;

/** PvE reference thresholds from the audited 2026-11-26 snapshot.
 * Reputation uses thousandths, so 0.25 is stored as 250. */
public enum CampaignTrader {
    PRAPOR("prapor","Prapor","Armes et munitions","54cb50c76803fa8b248b4571",66,levels(1,0,6,700,21,2700,36,7900)),
    THERAPIST("therapist","Thérapeute","Soins et provisions","54cb57776803fa99248b456e",53,levels(1,0,5,600,18,2100,37,5800)),
    SKIER("skier","Skieur","Contrebande et accessoires","58330581ace78e27b8b10cee",67,levels(1,0,7,600,22,2100,38,5800)),
    PEACEKEEPER("peacekeeper","Pacificateur","Équipement occidental","5935c25fb3acc3127c3d8cd9",51,levels(1,0,8,500,19,2200,37,6000)),
    MECHANIC("mechanic","Mécanicien","Atelier et modifications","5a7c2eca46aef81a7ca2145d",72,levels(1,0,12,600,26,2300,40,7600)),
    RAGMAN("ragman","Fripier","Protections et transport","5ac3b934156ae10c4430e83c",57,levels(1,0,12,500,27,2000,42,6500)),
    JAEGER("jaeger","Chasseur","Survie et chasse","5c0647fdd443bc2504c2d371",64,levels(1,0,9,600,17,2100,33,7300)),
    FENCE("fence","Receleur","Marché et Collectionneur","579dc571d53a0658a154fbec",16,levels(1,0,1,6000)),
    REF("ref","Arbitre","Défis","6617beeaa9cfa777ca915b7c",20,levels(1,0,15,250,25,500,35,1200)),
    LIGHTKEEPER("lightkeeper","Gardien du phare","Renseignements de fin de jeu","638f541a29ffd1183d187f57",14,levels(1,0)),
    BTR_DRIVER("btr-driver","Chauffeur BTR","Transport et missions de terrain","656f0f98d80a697f855d34b1",19,levels(1,0));
    public record Loyalty(int tier,int minimumLevel,int reputationMilli){}
    public final String id,title,specialty,referenceId;
    public final int questCount;
    public final List<Loyalty> loyalty;
    CampaignTrader(String id,String title,String specialty,String referenceId,int questCount,List<Loyalty> loyalty){
        this.id=id;this.title=title;this.specialty=specialty;this.referenceId=referenceId;this.questCount=questCount;this.loyalty=loyalty;
    }
    private static List<Loyalty> levels(int... pairs){
        var result=new java.util.ArrayList<Loyalty>();
        for(int i=0;i<pairs.length;i+=2)result.add(new Loyalty(i/2+1,pairs[i],pairs[i+1]));
        return List.copyOf(result);
    }
    public int loyaltyLevel(int playerLevel,int reputationMilli,boolean unlocked){
        if(!unlocked)return 0;
        int tier=this==FENCE&&reputationMilli<0?0:1;
        for(var req:loyalty)if(playerLevel>=req.minimumLevel()&&reputationMilli>=req.reputationMilli())tier=req.tier();
        return tier;
    }
    public static CampaignTrader byId(String id){
        return Arrays.stream(values()).filter(t->t.id.equals(id.toLowerCase(Locale.ROOT))||t.referenceId.equals(id)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Unknown campaign trader "+id));
    }
}
