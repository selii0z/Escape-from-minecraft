package fr.taczoperations.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import top.theillusivec4.curios.api.CuriosApi;

/** Loaded only if the optional Curios API is installed. */
public final class CuriosGoggles {
    public static String equipped(LivingEntity player) {
        var dedicated=FieldCurios.equipped(player,"efm_goggles");
        String id=BuiltInRegistries.ITEM.getKey(dedicated.getItem()).toString();
        if(id.equals("taczoperations:night_vision_goggles")||id.equals("taczoperations:thermal_goggles"))return id;
        return CuriosApi.getCuriosInventory(player)
            .flatMap(inventory -> inventory.findFirstCurio(stack -> {
                String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                return key.equals("taczoperations:night_vision_goggles") || key.equals("taczoperations:thermal_goggles");
            }))
            .map(result -> BuiltInRegistries.ITEM.getKey(result.stack().getItem()).toString())
            .orElse("");
    }
    private CuriosGoggles() {}
}
