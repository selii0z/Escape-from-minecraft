package fr.taczoperations.mixin.client;

import fr.taczoperations.client.RaidHudSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional Cobblemon compatibility; never changes the party or its saved display preference. */
@Pseudo
@Mixin(targets="com.cobblemon.mod.common.client.gui.PartyOverlay",remap=false)
public abstract class CobblemonPartyHudMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void efm$hidePartyInRaid(CallbackInfo ci){
        if(RaidHudSettings.inRaid())ci.cancel();
    }
}
