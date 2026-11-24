package fr.taczoperations.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class ThermalVision {
    private static final double RANGE_SQUARED = 64.0 * 64.0;

    public static boolean detects(Entity entity) {
        var player = Minecraft.getInstance().player;
        return GoggleControls.thermal() && player != null && entity != player
            && entity instanceof LivingEntity living && living.isAlive()
            && !living.isInvisibleTo(player) && player.distanceToSqr(living) <= RANGE_SQUARED
            && player.hasLineOfSight(living);
    }

    public static int tint(int color) {
        int red = (color >>> 16) & 255;
        int green = (color >>> 8) & 255;
        int blue = color & 255;
        return (color & 0xFF000000) | (red << 16) | ((green * 185 / 255) << 8) | (blue * 95 / 255);
    }

    private ThermalVision() {}
}
