package fr.taczoperations.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = "taczoperations", value = Dist.CLIENT)
public final class RaidVisionPass {
    private static PostChain chain;
    private static String current = "";
    private static int width, height;
    private static Object previousLevel;
    private static String failed = "";

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var mc = Minecraft.getInstance();
        String mode = "";
        if (mc.level != previousLevel) {
            close();
            failed = "";
            previousLevel = mc.level;
        }
        if (mc.level != null && mc.player != null) {
            if (GoggleControls.thermal()) mode = "thermal";
            else if (GoggleControls.nightVision()) mode = "night_vision";
            else if (mc.level.dimension().location().toString().equals("taczoperations:black_forest")) {
                long day = Math.floorMod(mc.level.getDayTime(),24000L);
                if (day >= 13000 && day <= 23000) mode = "dark_raid";
            }
        }
        if (!mode.equals(current)) {
            close();
            current = mode;
            if (!mode.isEmpty() && !mode.equals(failed)) {
                try {
                    chain = new PostChain(mc.getTextureManager(),mc.getResourceManager(),mc.getMainRenderTarget(),
                        ResourceLocation.fromNamespaceAndPath("taczoperations","shaders/post/"+mode+".json"));
                    width = height = 0;
                } catch (Exception e) {
                    failed = mode;
                    LogUtils.getLogger().warn("Raid vision effect unavailable: "+mode,e);
                }
            }
        }
        if (chain == null) return;
        try {
            int w = mc.getMainRenderTarget().width, h = mc.getMainRenderTarget().height;
            if (w != width || h != height) {
                chain.resize(w,h);
                width=w; height=h;
            }
            chain.process(0F);
        } catch (RuntimeException e) {
            failed=mode;
            close();
            current=mode;
            LogUtils.getLogger().warn("Raid vision render failed",e);
        } finally {
            mc.getMainRenderTarget().bindWrite(false);
        }
    }

    private static void close() {
        if (chain != null) chain.close();
        chain = null;
        current = "";
    }
}
