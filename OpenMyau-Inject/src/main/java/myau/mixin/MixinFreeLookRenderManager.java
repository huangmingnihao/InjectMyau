package myau.mixin;

import myau.inject.FreeLookCallbacks;
import net.minecraft.client.renderer.entity.RenderManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {RenderManager.class}, priority = 9999)
public abstract class MixinFreeLookRenderManager {
    @Redirect(
            method = "cacheActiveRenderInfo",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/entity/RenderManager;playerViewX:F",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void myauSetPlayerViewX(RenderManager renderManager, float value) {
        FreeLookCallbacks.setPlayerViewX(renderManager, value);
    }

    @Redirect(
            method = "cacheActiveRenderInfo",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/entity/RenderManager;playerViewY:F",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void myauSetPlayerViewY(RenderManager renderManager, float value) {
        FreeLookCallbacks.setPlayerViewY(renderManager, value);
    }
}
