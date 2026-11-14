package myau.mixin;

import myau.util.TimerSpeedLease;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;












@Mixin(Timer.class)
public abstract class MixinTimer {

    @Inject(method = "updateTimer", at = @At("RETURN"))
    private void myau$limitTickBurst(CallbackInfo ci) {
        try {
            TimerSpeedLease.limitFrame((Timer) (Object) this);
        } catch (Throwable swallowed) {
            myau.inject.Log.swallowed(swallowed);
        }
    }
}
