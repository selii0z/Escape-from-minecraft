package fr.taczoperations.mixin.client;

import fr.taczoperations.client.GoggleControls;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightTexture.class)
public abstract class GoggleLightTextureMixin {
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"), require = 0)
    private boolean gogglesNightVision(LocalPlayer player, Holder<MobEffect> effect) {
        return player.hasEffect(effect) || (effect.equals(MobEffects.NIGHT_VISION) && GoggleControls.nightVision());
    }
}
