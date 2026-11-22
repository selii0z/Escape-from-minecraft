package fr.taczoperations.operations;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public final class ForestProtection {
    private static final ThreadLocal<BlockPos> RAID_EDIT = new ThreadLocal<>();

    static boolean setRaidBarrel(ServerLevel level, BlockPos pos, BlockState state) {
        RAID_EDIT.set(pos);
        try {
            return level.setBlockAndUpdate(pos, state);
        } finally {
            RAID_EDIT.remove();
        }
    }

    public static boolean allowsRaidBarrel(BlockPos pos, BlockState state) {
        return pos.equals(RAID_EDIT.get()) && (state.is(Blocks.BARREL) || state.isAir());
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(ForestExpedition.FOREST)) {
            event.setCanceled(true);
        }
    }

    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(ForestExpedition.FOREST)) {
            event.getAffectedBlocks().clear();
        }
    }

    private ForestProtection() {}
}
