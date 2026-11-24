package fr.taczoperations.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.taczoperations.client.ThermalVision;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public abstract class ThermalEntityTintMixin {
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
        require = 0)
    private void thermalTint(EntityModel<?> model, PoseStack pose, VertexConsumer vertices, int light, int overlay, int color,
                             LivingEntity entity, float yaw, float partialTick, PoseStack renderPose, MultiBufferSource buffers, int renderLight) {
        model.renderToBuffer(pose, vertices, light, overlay, ThermalVision.detects(entity) ? ThermalVision.tint(color) : color);
    }
}
