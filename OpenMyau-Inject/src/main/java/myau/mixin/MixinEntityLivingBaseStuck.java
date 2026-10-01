package myau.mixin;

import myau.event.EventManager;
import myau.events.StuckMoveEntityWithHeadingEvent;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 */
@Mixin(value = {EntityLivingBase.class})
public abstract class MixinEntityLivingBaseStuck {
    @Inject(
            method = {"moveEntityWithHeading"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void onStuckMoveEntityWithHeading(float strafe, float forward, CallbackInfo callbackInfo) {
        if ((Entity) ((Object) this) instanceof EntityPlayerSP) {
            StuckMoveEntityWithHeadingEvent event = new StuckMoveEntityWithHeadingEvent();
            EventManager.call(event);
            if (event.isCancelled()) {
                callbackInfo.cancel();
            }
        }
    }
}
