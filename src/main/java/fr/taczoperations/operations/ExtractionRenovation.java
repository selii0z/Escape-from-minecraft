package fr.taczoperations.operations;

import fr.taczoperations.worldgen.ExtractionLayout;
import fr.taczoperations.worldgen.ForestPlan;
import fr.taczoperations.worldgen.IndustrialZone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
import java.util.LinkedHashMap;

/** Renovate only the old fixed markers, only in loaded protected raid chunks. */
final class ExtractionRenovation {
    private record Pad(BlockPos center,ExtractionLayout.Style style,Block old){}
    private static final List<Pad> PADS=List.of(
        new Pad(ForestLandmarks.position(ForestPlan.EXTRACTION),ExtractionLayout.Style.FOREST,Blocks.GOLD_BLOCK),
        new Pad(ForestLandmarks.position(ForestPlan.CONDITIONAL_EXIT),ExtractionLayout.Style.RELAY,Blocks.GOLD_BLOCK),
        new Pad(ForestExpedition.industrialPosition(IndustrialZone.EXIT),ExtractionLayout.Style.INDUSTRIAL,Blocks.GOLD_BLOCK),
        new Pad(ForestExpedition.industrialPosition(IndustrialZone.KEY_EXIT),ExtractionLayout.Style.KEY,Blocks.IRON_BLOCK),
        new Pad(ForestExpedition.industrialPosition(IndustrialZone.PAID_EXIT),ExtractionLayout.Style.PAID,Blocks.EMERALD_BLOCK));
    static boolean allowed(BlockPos pos){
        for(Pad pad:PADS){
            var c=pad.center();
            if(Math.abs(pos.getX()-c.getX())<=4&&Math.abs(pos.getZ()-c.getZ())<=4&&pos.getY()>=c.getY()-1&&pos.getY()<=c.getY()+3)return true;
        }
        return false;
    }
    static void near(ServerPlayer player,boolean industrial){
        ServerLevel level=player.serverLevel();
        if(!level.dimension().equals(ForestExpedition.FOREST)||!ForestRaid.isParticipant(player))return;
        for(Pad pad:PADS){
            if(IndustrialZone.inside(pad.center().getX(),pad.center().getZ())!=industrial)continue;
            if(pad.center().distToCenterSqr(player.position())>48*48)continue;
            renovate(level,pad);
        }
    }
    private static void renovate(ServerLevel level,Pad pad){
        BlockPos center=pad.center(),marker=center.below();
        if(!level.hasChunkAt(marker)||!level.getBlockState(marker).is(pad.old()))return;
        // Never remodel beneath another player currently standing on the pad.
        if(level.players().stream().anyMatch(p->Math.abs(p.getX()-center.getX()-.5)<5&&Math.abs(p.getZ()-center.getZ()-.5)<5&&Math.abs(p.getY()-center.getY())<4))return;
        var edits=new LinkedHashMap<BlockPos,Block>();
        ExtractionLayout.paint(pad.style(),(x,y,z,block)->edits.put(center.offset(x,y,z),block));
        // All target chunks must already be loaded. Never erase container or other block-entity data.
        for(var pos:edits.keySet())if(!level.hasChunkAt(pos)||level.getBlockEntity(pos)!=null)return;
        // Write the old central marker last, so an incomplete edit can be retried.
        Block finish=edits.remove(marker);
        for(var edit:edits.entrySet())if(!ForestProtection.setExtractionBlock(level,edit.getKey(),edit.getValue().defaultBlockState()))return;
        if(finish!=null)ForestProtection.setExtractionBlock(level,marker,finish.defaultBlockState());
    }
    private ExtractionRenovation(){}
}
