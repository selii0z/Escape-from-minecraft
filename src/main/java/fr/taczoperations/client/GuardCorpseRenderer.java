package fr.taczoperations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.taczoperations.OperationsMod;
import fr.taczoperations.entity.GuardCorpse;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = OperationsMod.ID, value = Dist.CLIENT)
public final class GuardCorpseRenderer extends EntityRenderer<GuardCorpse> {
    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/illager/pillager.png");
    private final ModelPart body;

    public GuardCorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
        body = context.bakeLayer(ModelLayers.PILLAGER);
        body.getChild("arms").visible = false;
        body.getChild("left_arm").zRot = -.22F;
        body.getChild("right_arm").zRot = .28F;
        shadowRadius = .35F;
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(OperationsMod.GUARD_CORPSE.get(), GuardCorpseRenderer::new);
    }

    @Override
    public void render(GuardCorpse corpse, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, .16, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(-1, -1, 1);
        pose.translate(0, -.62, 0);
        body.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(corpse, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(GuardCorpse corpse) {
        return TEXTURE;
    }
}
