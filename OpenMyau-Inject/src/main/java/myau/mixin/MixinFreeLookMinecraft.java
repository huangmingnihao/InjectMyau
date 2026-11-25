package myau.mixin;

import myau.inject.FreeLookCallbacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {Minecraft.class}, priority = 9999)
public abstract class MixinFreeLookMinecraft {
    @Redirect(
            method = "runTick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/settings/GameSettings;thirdPersonView:I",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void myauSetThirdPersonView(GameSettings gameSettings, int value) {
        FreeLookCallbacks.setThirdPersonView(gameSettings, value);
    }
}
