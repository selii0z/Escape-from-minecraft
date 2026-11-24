package fr.taczoperations.client;

import fr.taczoperations.operations.ForestExpedition;
import fr.taczoperations.operations.RaidDropPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = "taczoperations", value = Dist.CLIENT)
public final class RaidDropHud {
    private static RaidDropPayload drop;
    private static long updated;
    public static void handle(RaidDropPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            drop = payload.active() ? payload : null;
            updated = System.nanoTime();
        });
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.level.dimension().equals(ForestExpedition.FOREST)) {
            drop = null;
            return;
        }
        if (mc.options.hideGui || drop == null || System.nanoTime() - updated > 3500000000L) return;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        long distance = Math.round(Math.hypot(mc.player.getX()-drop.x(), mc.player.getZ()-drop.z()));
        event.getGuiGraphics().drawCenteredString(mc.font,
            (drop.landed() ? "Largage" : "Largage en approche") + " · X " + drop.x() + "  Y " + drop.y()
                + "  Z " + drop.z() + " · " + distance + " m", w/2, h-84, 0xffe5ce88);
    }
    private RaidDropHud() {}
}
