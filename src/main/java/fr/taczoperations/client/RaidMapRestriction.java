package fr.taczoperations.client;

import fr.taczoperations.operations.ForestExpedition;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.Locale;

@EventBusSubscriber(modid = "taczoperations", value = Dist.CLIENT)
public final class RaidMapRestriction {
    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(ForestExpedition.FOREST)) return;
        var screen = event.getNewScreen();
        if (screen == null) return;
        String type = screen.getClass().getName().toLowerCase(Locale.ROOT);
        if (type.startsWith("xaero.") || type.startsWith("journeymap.") || type.startsWith("mapfrontiers.")) {
            event.setCanceled(true);
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("Carte indisponible pendant le raid."), true);
        }
    }
    private RaidMapRestriction() {}
}
