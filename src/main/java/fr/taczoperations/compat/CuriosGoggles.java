package fr.taczoperations.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import top.theillusivec4.curios.api.CuriosApi;

/** Loaded only if the optional Curios API is installed. */
public final class CuriosGoggles {
    public static String equipped(LivingEntity player) {
        return CuriosApi.getCuriosInventory(player)
            .flatMap(inventory -> inventory.findFirstCurio(stack -> {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                return id.equals("taczoperations:night_vision_goggles") || id.equals("taczoperations:thermal_goggles");
            }))
            .map(result -> BuiltInRegistries.ITEM.getKey(result.stack().getItem()).toString())
            .orElse("");
    }
    private CuriosGoggles() {}
}
