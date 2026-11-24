package fr.taczoperations.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.minecraft.client.Minecraft;

@EventBusSubscriber(modid = "taczoperations", value = Dist.CLIENT)
public final class GoggleOverlay {
    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        if (!GoggleControls.nightVision() && !GoggleControls.thermal()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        boolean thermal = GoggleControls.thermal();
        g.fill(0, 0, w, h, thermal ? 0x660A0A0A : 0x4530C040);
        int color = thermal ? 0xFFDDDDDD : 0xFF80DD80;
        int edge = Math.max(8, Math.min(w, h) / 12);
        for (int i = 0; i < edge; i++) {
            int alpha = (edge - i) * 100 / edge;
            int shade = alpha << 24;
            g.fill(i, 0, i + 1, h, shade);
            g.fill(w - i - 1, 0, w - i, h, shade);
            g.fill(0, i, w, i + 1, shade);
            g.fill(0, h - i - 1, w, h - i, shade);
        }
        String mode = thermal ? "THERMAL" : "NVG";
        g.fill(7, 7, 23 + mc.font.width(mode), 23, 0x99000000);
        g.fill(11, 12, 15, 16, color);
        g.drawString(mc.font, mode, 19, 11, color, false);
        if (thermal) {
            var hit = mc.hitResult;
            if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit
                && entityHit.getEntity() instanceof net.minecraft.world.entity.LivingEntity living
                && living.isAlive() && !living.isInvisibleTo(mc.player)
                && mc.player.hasLineOfSight(living)) {
                int cx = w / 2, cy = h / 2;
                int hot = 0xFFFFB060;
                g.fill(cx - 14, cy - 14, cx - 7, cy - 13, hot);
                g.fill(cx + 7, cy - 14, cx + 14, cy - 13, hot);
                g.fill(cx - 14, cy + 13, cx - 7, cy + 14, hot);
                g.fill(cx + 7, cy + 13, cx + 14, cy + 14, hot);
                g.drawCenteredString(mc.font, "HEAT", cx, cy + 20, hot);
            }
        }
        for (int y = 0; y < h; y += 4) {
            g.fill(0, y, w, y + 1, 0x10000000);
        }
    }
}
