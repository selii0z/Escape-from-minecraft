package fr.taczoperations.campaign;

import java.util.Locale;
import java.util.Arrays;

/** Stable campaign identifiers, independent of existing shop enum ordinals. */
public enum CampaignTrader {
    PRAPOR("prapor","Prapor","Armes et munitions"),
    THERAPIST("therapist","Therapist","Soins et provisions"),
    SKIER("skier","Skier","Contrebande et accessoires"),
    PEACEKEEPER("peacekeeper","Peacekeeper","Équipement occidental"),
    MECHANIC("mechanic","Mechanic","Atelier et modifications"),
    RAGMAN("ragman","Ragman","Protections et transport"),
    JAEGER("jaeger","Jaeger","Survie et chasse"),
    FENCE("fence","Fence","Marché et Collectionneur"),
    REF("ref","Ref","Défis"),
    LIGHTKEEPER("lightkeeper","Lightkeeper","Renseignements de fin de jeu");
    public final String id,title,specialty;
    CampaignTrader(String id,String title,String specialty){this.id=id;this.title=title;this.specialty=specialty;}
    public static CampaignTrader byId(String id){
        return Arrays.stream(values()).filter(t->t.id.equals(id.toLowerCase(Locale.ROOT))).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Unknown campaign trader "+id));
    }
}
