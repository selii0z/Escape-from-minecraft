package fr.taczoperations.worldgen;

import java.util.List;
import net.minecraft.core.BlockPos;

/** Three existing cabins, with deterministic doors and protected loot anchors. */
public final class ForestVaults {
    public record Vault(String title,ForestPlan.Site site){
        public BlockPos door(){return new BlockPos(ForestPlan.ORIGIN+site.x()+site.width()/2,site.floor()+1,ForestPlan.ORIGIN+site.z());}
        public ForestRaidAnchors.Mark loot(int index){return new ForestRaidAnchors.Mark(site.x()+site.width()/2,site.floor()+1,site.z()+site.length()-3,4+index);}
    }
    public static final List<Vault> VAULTS=List.of(
        new Vault("Cabane du garde forestier",ForestPlan.SITES.get(9)),
        new Vault("Refuge des contrebandiers",ForestPlan.SITES.get(10)),
        new Vault("Chalet des renseignements",ForestPlan.SITES.get(24)));
    public static boolean door(BlockPos pos){return VAULTS.stream().anyMatch(v->pos.equals(v.door())||pos.equals(v.door().above()));}
    private ForestVaults(){}
}
