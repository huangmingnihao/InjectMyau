package myau.mixin;

import myau.event.EventManager;
import myau.events.StuckPreLivingUpdateEvent;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 */
@Mixin(value = {EntityPlayerSP.class})
public abstract class MixinEntityPlayerSPStuck {
    @Inject(
            method = {"onLivingUpdate"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void onStuckPreLivingUpdate(CallbackInfo callbackInfo) {
        StuckPreLivingUpdateEvent event = new StuckPreLivingUpdateEvent();
        EventManager.call(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }
}
