package fr.taczoperations.mixin.client;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.taczoperations.client.RaidEnemyModels;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Pillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class RaidEnemyModelMixin {
    @Shadow protected EntityModel<?> model;
    @Unique private EntityModel<?> efm$original;
    @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("HEAD"),require=0)
    private void efm$before(LivingEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci){
        if(!(entity instanceof Pillager guard)||!(model instanceof net.minecraft.client.model.IllagerModel<?>))return;
        var preview=RaidEnemyModels.select(guard);
        if(preview!=null){efm$original=model;model=preview.model();}
    }
    @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("RETURN"),require=0)
    private void efm$after(LivingEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci){
        if(efm$original!=null){model=efm$original;efm$original=null;}
    }
}
