package fr.taczoperations.operations;

import fr.taczoperations.worldgen.ForestPlan;
import net.minecraft.core.BlockPos;

final class ForestLandmarks {
    static BlockPos position(ForestPlan.Point point) {
        return new BlockPos(ForestPlan.ORIGIN + point.x(), ForestPlan.surfaceY(point), ForestPlan.ORIGIN + point.z());
    }

    private ForestLandmarks() {}
}
