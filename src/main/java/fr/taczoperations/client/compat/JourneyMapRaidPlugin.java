package fr.taczoperations.client.compat;

import com.mojang.logging.LogUtils;
import fr.taczoperations.OperationsMod;
import fr.taczoperations.operations.ForestExpedition;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapRaidPlugin implements IClientPlugin {
    private IClientAPI api;
    private boolean listening;
    private boolean restricted;
    private boolean restoreMinimap;
    private boolean warned;

    @Override
    public String getModId() {
        return OperationsMod.ID;
    }

    @Override
    public void initialize(IClientAPI clientApi) {
        api = clientApi;
        if (!listening) {
            NeoForge.EVENT_BUS.addListener(this::tick);
            listening = true;
        }
    }

    private void tick(ClientTickEvent.Post event) {
        if (api == null) return;
        Minecraft mc = Minecraft.getInstance();
        boolean raid = mc.level != null && mc.level.dimension().equals(ForestExpedition.FOREST);
        try {
            if (raid) {
                if (!restricted) {
                    restoreMinimap = api.minimapEnabled();
                    restricted = true;
                }
                // Reapply if a player uses the JourneyMap toggle key or switches preset.
                if (api.minimapEnabled()) api.toggleMinimap(false);
            } else if (restricted) {
                if (restoreMinimap && !api.minimapEnabled()) api.toggleMinimap(true);
                restricted = false;
            }
            warned = false;
        } catch (RuntimeException | LinkageError exception) {
            if (!warned) {
                LogUtils.getLogger().warn("JourneyMap raid restriction could not be applied", exception);
                warned = true;
            }
        }
    }
}
