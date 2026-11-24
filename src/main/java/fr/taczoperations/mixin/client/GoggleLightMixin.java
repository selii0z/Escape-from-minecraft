package fr.taczoperations.mixin.client;

import fr.taczoperations.client.GoggleControls;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GoggleLightMixin {
    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gogglesLight(LivingEntity entity, float partialTick, CallbackInfoReturnable<Float> cir) {
        if (GoggleControls.nightVision() && entity == net.minecraft.client.Minecraft.getInstance().player) cir.setReturnValue(1.0F);
    }
}
