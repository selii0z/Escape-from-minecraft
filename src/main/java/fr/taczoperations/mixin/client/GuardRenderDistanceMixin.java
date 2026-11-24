package fr.taczoperations.mixin.client;

import fr.taczoperations.client.ThermalVision;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class GuardRenderDistanceMixin {
    @Inject(method = "shouldRenderAtSqrDistance", at = @At("RETURN"), cancellable = true, require = 0)
    private void operations$thermalRange(double distance, CallbackInfoReturnable<Boolean> cir) {
        if (distance <= 150.0*150.0 && ThermalVision.detects((Entity)(Object)this)) cir.setReturnValue(true);
    }
}
