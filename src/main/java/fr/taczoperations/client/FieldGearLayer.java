package fr.taczoperations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.taczoperations.OperationsMod;
import fr.taczoperations.compat.FieldCurios;
import fr.taczoperations.operations.FieldInventoryMenu;
import fr.taczoperations.operations.FieldPack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only gear models following the animated player torso and head. */
@EventBusSubscriber(modid=OperationsMod.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class FieldGearLayer extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>>{
    private FieldGearLayer(RenderLayerParent<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> parent){super(parent);}
    @SubscribeEvent public static void addLayers(EntityRenderersEvent.AddLayers event){
        if(!ModList.get().isLoaded("curios"))return;
        for(var skin:event.getSkins()){
            if(event.getSkin(skin) instanceof PlayerRenderer renderer)renderer.addLayer(new FieldGearLayer(renderer));
        }
    }
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,
            float limbSwing,float limbAmount,float partial,float age,float headYaw,float headPitch){
        if(player.isInvisible()||player.isSpectator()||!ModList.get().isLoaded("curios"))return;
        var bag=FieldCurios.equipped(player,FieldInventoryMenu.BAG);
        var rig=FieldCurios.equipped(player,FieldInventoryMenu.RIG);
        if(bag.getItem() instanceof FieldPack pack&&!pack.rig())draw(pose,buffers,light,player,bag,true);
        if(rig.getItem() instanceof FieldPack pack&&pack.rig())draw(pose,buffers,light,player,rig,false);
    }
    private void draw(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer p,ItemStack item,boolean bag){
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        pose.translate(0,.36,bag?.30:-.27);
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        // Match the item's back/front surfaces to the character's torso.
        if(!bag)pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(bag?.82F:.82F,bag?.78F:.73F,bag?.75F:.63F);
        Minecraft.getInstance().getItemRenderer().renderStatic(item,ItemDisplayContext.FIXED,light,
            OverlayTexture.NO_OVERLAY,pose,buffers,p.level(),p.getId());
        pose.popPose();
    }
}
