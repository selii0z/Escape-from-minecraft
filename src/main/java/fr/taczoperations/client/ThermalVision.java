package fr.taczoperations.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class ThermalVision {
    private static final double RANGE_SQUARED = 150.0 * 150.0;

    public static boolean detects(Entity entity) {
        var player = Minecraft.getInstance().player;
        return GoggleControls.thermal() && player != null && entity != player
            && entity instanceof LivingEntity living && living.isAlive()
            && !living.isInvisibleTo(player) && player.distanceToSqr(living) <= RANGE_SQUARED;
        // The entity is rendered with a depth-tested cutout, so walls and terrain
        // hide its covered pixels. Do not reject the entire model when its eyes
        // are hidden: a visible arm or leg must still appear warm.
    }

    public static int tint(int color) {
        return 0xFFFFFFFF;
    }

    private ThermalVision() {}
}
