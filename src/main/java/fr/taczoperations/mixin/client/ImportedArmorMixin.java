package fr.taczoperations.mixin.client;
import fr.taczoperations.client.ImportedGearModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HumanoidArmorLayer.class)
public abstract class ImportedArmorMixin {
    @Inject(method="renderArmorPiece",at=@At("HEAD"),cancellable=true,require=0)
    private void efm$replace(PoseStack pose,MultiBufferSource buffers,LivingEntity entity,EquipmentSlot slot,int light,HumanoidModel<?> model,CallbackInfo ci){
        if(entity instanceof AbstractClientPlayer && ImportedGearModels.exists(ImportedGearModels.armorId(entity.getItemBySlot(slot),slot)))ci.cancel();
    }
}
