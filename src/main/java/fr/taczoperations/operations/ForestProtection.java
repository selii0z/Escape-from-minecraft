package fr.taczoperations.operations;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public final class ForestProtection {
    private static final ThreadLocal<BlockPos> RAID_EDIT = new ThreadLocal<>();

    static boolean setRaidContainer(ServerLevel level, BlockPos pos, BlockState state) {
        RAID_EDIT.set(pos);
        try {
            return level.setBlockAndUpdate(pos, state);
        } finally {
            RAID_EDIT.remove();
        }
    }

    static boolean setVaultDoor(ServerLevel level,BlockPos pos,boolean closed) {
        if(!vaultDoor(pos))return false;
        RAID_EDIT.set(pos);
        try{
            BlockState state=(closed?Blocks.IRON_BLOCK:Blocks.AIR).defaultBlockState();
            return level.getBlockState(pos).equals(state)||level.setBlockAndUpdate(pos,state);
        }finally{RAID_EDIT.remove();}
    }
    private static boolean vaultDoor(BlockPos pos) {
        return fr.taczoperations.worldgen.IndustrialZone.VAULTS.stream().anyMatch(v->pos.equals(v.door())||pos.equals(v.door().above()));
    }
    public static boolean allowsRaidContainer(BlockPos pos, BlockState state) {
        return pos.equals(RAID_EDIT.get()) && (state.is(Blocks.BARREL) || state.is(Blocks.CHEST) || state.isAir() || state.is(Blocks.IRON_BLOCK)&&vaultDoor(pos));
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
