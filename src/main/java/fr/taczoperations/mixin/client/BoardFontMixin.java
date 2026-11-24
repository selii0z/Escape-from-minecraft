package fr.taczoperations.mixin.client;
 
import fr.taczoperations.client.BoardTextureFilter;

import fr.taczoperations.OperationsMod;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureManager.class)
public abstract class BoardFontMixin {
    @Inject(method = "register(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V",
        at = @At("HEAD"))
    private void taczoperations$filterBoardFonts(ResourceLocation key, AbstractTexture texture, CallbackInfo ignored) {
        if (texture instanceof FontTexture && key.getNamespace().equals(OperationsMod.ID)
            && (key.getPath().startsWith("heading/") || key.getPath().startsWith("interface/"))) {
            ((BoardTextureFilter) texture).taczoperations$enableLinearFilter();
        }
    }
}
