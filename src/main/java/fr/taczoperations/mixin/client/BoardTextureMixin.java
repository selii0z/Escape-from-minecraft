package fr.taczoperations.mixin.client;
 
import fr.taczoperations.client.BoardTextureFilter;

import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AbstractTexture.class)
public abstract class BoardTextureMixin implements BoardTextureFilter {
    @Unique private boolean taczoperations$linear;

    @Override
    public void taczoperations$enableLinearFilter() {
        taczoperations$linear = true;
    }

    @ModifyVariable(method = "setFilter", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean taczoperations$filter(boolean blur) {
        return blur || taczoperations$linear;
    }
}
