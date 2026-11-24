package fr.taczoperations.client;

import fr.taczoperations.operations.RaidResultPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class RaidResultHandler {
    private RaidResultHandler() {}

    public static void handle(RaidResultPayload report, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new RaidResultScreen(
            report.success(), report.duration(), report.kills(), report.crates(),
            report.contracts(), report.combatXp(), report.searchXp(), report.contractXp(), report.adjustment())));
    }
}
