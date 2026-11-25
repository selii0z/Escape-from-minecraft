package fr.taczoperations.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;

/** Optional integration; callers check ModList before entering this class. */
public final class FieldCurios {
    public static IItemHandlerModifiable handler(LivingEntity player,String id){
        return CuriosApi.getCuriosInventory(player).flatMap(inv->inv.getStacksHandler(id))
            .map(h->(IItemHandlerModifiable)h.getStacks()).filter(h->h.getSlots()>0).orElse(null);
    }
    public static ItemStack equipped(LivingEntity player,String id){
        var h=handler(player,id);return h==null?ItemStack.EMPTY:h.getStackInSlot(0);
    }
    private FieldCurios(){}
}
