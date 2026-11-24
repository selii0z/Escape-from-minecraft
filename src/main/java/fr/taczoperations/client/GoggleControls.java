package fr.taczoperations.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "taczoperations", value = Dist.CLIENT)
public final class GoggleControls {
    private static final KeyMapping TOGGLE = new KeyMapping("key.taczoperations.goggles", GLFW.GLFW_KEY_N, "key.categories.taczoperations");
    private static String active = "";
    private static net.minecraft.client.player.LocalPlayer previousPlayer;
    private static net.minecraft.client.multiplayer.ClientLevel previousLevel;
    @EventBusSubscriber(modid = "taczoperations", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(TOGGLE); }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != previousPlayer || mc.level != previousLevel) {
            active = "";
            previousPlayer = mc.player;
            previousLevel = mc.level;
        }
        String equipped = "";
        if (mc.player != null && mc.level != null && mc.player.isAlive()) {
            equipped = equippedGoggles(mc.player);
        }
        if (!active.equals(equipped)) active = "";
        while (TOGGLE.consumeClick()) {
            if (mc.player == null || mc.screen != null || equipped.isEmpty()) continue;
            active = active.isEmpty() ? equipped : "";
            String message = active.isEmpty() ? "message.taczoperations.goggles.off"
                : active.equals("taczoperations:thermal_goggles")
                    ? "message.taczoperations.goggles.thermal" : "message.taczoperations.goggles.nvg";
            mc.player.displayClientMessage(Component.translatable(message), true);
        }
    }
    private static String equippedGoggles(net.minecraft.world.entity.LivingEntity player) {
        if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
            String curio = fr.taczoperations.compat.CuriosGoggles.equipped(player);
            if (!curio.isEmpty()) return curio;
        }
        String id = BuiltInRegistries.ITEM.getKey(player.getItemBySlot(EquipmentSlot.HEAD).getItem()).toString();
        return isGoggles(id) ? id : "";
    }
    public static boolean isGoggles(String id) {
        return id.equals("taczoperations:night_vision_goggles") || id.equals("taczoperations:thermal_goggles");
    }
    private static boolean isActive(String id) {
        Minecraft mc = Minecraft.getInstance();
        return active.equals(id) && mc.player != null && mc.level != null
            && mc.player == previousPlayer && mc.level == previousLevel && mc.player.isAlive()
            && equippedGoggles(mc.player).equals(id);
    }
    public static boolean nightVision() { return isActive("taczoperations:night_vision_goggles"); }
    public static boolean thermal() { return isActive("taczoperations:thermal_goggles"); }
}
