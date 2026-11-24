package fr.taczoperations.mixin;

import fr.taczoperations.operations.ForestExpedition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class ForestTimeMixin {
    @Inject(method = "synchronizeTime", at = @At("HEAD"), cancellable = true)
    private void taczoperations$syncExpeditionTime(ServerLevel level, CallbackInfo callback) {
        if (level.dimension().equals(ForestExpedition.FOREST)) {
            ForestExpedition.synchronizeAtmosphere(level);
            callback.cancel();
        }
    }
}
