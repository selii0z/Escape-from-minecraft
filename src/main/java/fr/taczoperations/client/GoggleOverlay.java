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
        if (!GoggleControls.nightVision()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        g.fill(0, 0, w, h, 0x4530C040);
    }
}
