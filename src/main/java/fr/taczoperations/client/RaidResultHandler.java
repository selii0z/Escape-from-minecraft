package fr.taczoperations.client;

import fr.taczoperations.operations.RaidResultPayload;
import fr.taczoperations.operations.ForestExpedition;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = "taczoperations", value = Dist.CLIENT)
public final class RaidResultHandler {
    private static final java.util.ArrayDeque<RaidResultPayload> PENDING=new java.util.ArrayDeque<>();
    private static final int MAX_PENDING=10;
    private static int stableTicks;
    private RaidResultHandler() {}

    public static void handle(RaidResultPayload report, IPayloadContext context) {
        context.enqueueWork(() -> {
            if(PENDING.size()>=MAX_PENDING)PENDING.removeFirst();
            PENDING.addLast(report);
            stableTicks = 0;
        });
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            PENDING.clear();
            stableTicks = 0;
            return;
        }
        if (PENDING.isEmpty()) return;
        if (mc.level == null || mc.player == null || !mc.player.isAlive()
            || mc.level.dimension().equals(ForestExpedition.FOREST) || mc.screen != null) {
            stableTicks = 0;
            return;
        }
        if (++stableTicks < 10) return;
        var report = PENDING.removeFirst();
        stableTicks = 0;
        mc.setScreen(new RaidResultScreen(report.success(), report.duration(), report.kills(),
            report.crates(), report.contracts(), report.combatXp(), report.searchXp(),
            report.contractXp(), report.adjustment(), report.xpBefore(), report.xpAfter(),report.lootValue(),report.lootCount(),report.industrial()));
    }
}
