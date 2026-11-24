package fr.taczoperations.mixin;

import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public abstract class GuardTrackingMixin {
    @Inject(method = "clientTrackingRange", at = @At("RETURN"), cancellable = true, require = 0)
    private void operations$guardTracking(CallbackInfoReturnable<Integer> cir) {
        if ((Object)this == EntityType.PILLAGER) cir.setReturnValue(Math.max(12,cir.getReturnValue()));
    }
}
