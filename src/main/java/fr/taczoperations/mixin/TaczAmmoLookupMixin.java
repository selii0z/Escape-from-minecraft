package fr.taczoperations.mixin;

import fr.taczoperations.operations.FieldAmmoHandler;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets="com.tacz.guns.api.item.gun.AbstractGunItem",remap=false)
public abstract class TaczAmmoLookupMixin {
    // Optional hook: incompatible TaCZ versions retain their native inventory lookup.
    @SuppressWarnings({"rawtypes","unchecked"})
    @Redirect(method={"canReload","hasInventoryAmmo"},
        at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/neoforged/neoforge/capabilities/EntityCapability;Ljava/lang/Object;)Ljava/lang/Object;",remap=false),
        require=0,remap=false)
    private Object efm$ammoInventory(LivingEntity entity,EntityCapability capability,Object context){
        Object result=entity.getCapability(capability,context);
        if(capability==Capabilities.ItemHandler.ENTITY&&result instanceof IItemHandler handler)
            return FieldAmmoHandler.extend(entity,handler);
        return result;
    }
}
