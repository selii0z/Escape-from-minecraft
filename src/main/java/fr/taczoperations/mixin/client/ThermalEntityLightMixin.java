package fr.taczoperations.mixin.client;

import fr.taczoperations.client.ThermalVision;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class ThermalEntityLightMixin {
    @Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true, require = 0)
    private void thermalLight(Entity entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (ThermalVision.detects(entity)) {
            cir.setReturnValue(LightTexture.FULL_BRIGHT);
        }
    }
}
