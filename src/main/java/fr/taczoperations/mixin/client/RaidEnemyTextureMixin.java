package fr.taczoperations.mixin.client;
import fr.taczoperations.client.RaidEnemyModels;
import net.minecraft.client.renderer.entity.PillagerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Pillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PillagerRenderer.class)
public abstract class RaidEnemyTextureMixin {
    @Inject(method="getTextureLocation(Lnet/minecraft/world/entity/monster/Pillager;)Lnet/minecraft/resources/ResourceLocation;",at=@At("HEAD"),cancellable=true,require=0)
    private void efm$texture(Pillager guard,CallbackInfoReturnable<ResourceLocation> cir){
        var preview=RaidEnemyModels.select(guard);
        if(preview!=null)cir.setReturnValue(preview.texture());
    }
}
