package fr.taczoperations.operations;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public final class ForestProtection {
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
