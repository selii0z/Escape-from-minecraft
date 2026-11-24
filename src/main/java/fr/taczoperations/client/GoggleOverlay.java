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
        g.drawString(mc.font, thermal ? "THERMAL" : "NVG", 8, 8, color, true);
        for (int y = 0; y < h; y += 4) {
            g.fill(0, y, w, y + 1, 0x10000000);
        }
    }
}
