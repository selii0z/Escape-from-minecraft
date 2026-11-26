package fr.taczoperations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.taczoperations.OperationsMod;
import fr.taczoperations.operations.RaidFurnitureEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid=OperationsMod.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ImportedFurnitureRenderer implements BlockEntityRenderer<RaidFurnitureEntity>{
    public ImportedFurnitureRenderer(BlockEntityRendererProvider.Context context){}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){
        event.registerBlockEntityRenderer(OperationsMod.FURNITURE_ENTITY.get(),ImportedFurnitureRenderer::new);
    }
    @Override public void render(RaidFurnitureEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        String name=BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).getPath();
        String id="furniture_"+name;
        if(!ImportedGearModels.exists(id))return;
        pose.pushPose();
        pose.translate(.5,0,.5);
        ImportedGearModels.draw(id,"body",pose,buffers,light);
        pose.popPose();
    }
}
