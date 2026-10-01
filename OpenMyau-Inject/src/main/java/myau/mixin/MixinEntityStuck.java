package myau.mixin;

import myau.event.EventManager;
import myau.events.StuckMoveEntityEvent;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 */
@Mixin(value = {Entity.class})
public abstract class MixinEntityStuck {
    @Inject(
            method = {"moveEntity"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void onStuckMoveEntity(double x, double y, double z, CallbackInfo callbackInfo) {
        if ((Entity) ((Object) this) instanceof EntityPlayerSP) {
            StuckMoveEntityEvent event = new StuckMoveEntityEvent();
            EventManager.call(event);
            if (event.isCancelled()) {
                callbackInfo.cancel();
            }
        }
    }
}
