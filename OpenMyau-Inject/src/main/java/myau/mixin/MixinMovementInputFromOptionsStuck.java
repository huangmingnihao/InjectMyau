package myau.mixin;

import myau.event.EventManager;
import myau.events.StuckMoveInputEvent;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 */
@Mixin(value = {MovementInputFromOptions.class})
public abstract class MixinMovementInputFromOptionsStuck extends MovementInput {
    @Shadow
    private GameSettings gameSettings;

    @Inject(
            method = {"updatePlayerMoveState"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void onStuckMoveInput(CallbackInfo callbackInfo) {
        this.moveStrafe = 0.0F;
        this.moveForward = 0.0F;
        if (this.gameSettings.keyBindForward.isKeyDown()) {
            ++this.moveForward;
        }
        if (this.gameSettings.keyBindBack.isKeyDown()) {
            --this.moveForward;
        }
        if (this.gameSettings.keyBindLeft.isKeyDown()) {
            ++this.moveStrafe;
        }
        if (this.gameSettings.keyBindRight.isKeyDown()) {
            --this.moveStrafe;
        }
        this.jump = this.gameSettings.keyBindJump.isKeyDown();
        this.sneak = this.gameSettings.keyBindSneak.isKeyDown();

        StuckMoveInputEvent event = new StuckMoveInputEvent(this.moveForward, this.moveStrafe, this.jump, this.sneak);
        EventManager.call(event);
        this.moveForward = event.getForward();
        this.moveStrafe = event.getStrafe();
        this.jump = event.isJump();
        this.sneak = event.isSneak();

        if (this.sneak) {
            this.moveStrafe = (float) ((double) this.moveStrafe * event.getSneakSlowdown());
            this.moveForward = (float) ((double) this.moveForward * event.getSneakSlowdown());
        }
        callbackInfo.cancel();
    }
}
