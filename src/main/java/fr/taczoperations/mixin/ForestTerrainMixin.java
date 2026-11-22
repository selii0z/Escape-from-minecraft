package fr.taczoperations.mixin;

import fr.taczoperations.operations.ForestExpedition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class ForestTerrainMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"), cancellable = true)
    private void taczoperations$protectForest(BlockPos position, BlockState replacement, int flags,
                                               int recursion, CallbackInfoReturnable<Boolean> result) {
        if ((Object) this instanceof ServerLevel level && level.dimension().equals(ForestExpedition.FOREST)) {
            var block = replacement.getBlock();
            if ((block instanceof DoorBlock || block instanceof TrapDoorBlock) && level.getBlockState(position).is(block)) {
                return;
            }
            result.setReturnValue(false);
        }
    }
}
