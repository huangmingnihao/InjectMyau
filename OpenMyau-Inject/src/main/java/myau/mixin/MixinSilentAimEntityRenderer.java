package myau.mixin;

import myau.inject.SilentAimCallbacks;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinSilentAimEntityRenderer {
    @Inject(method = "getMouseOver", at = @At("HEAD"))
    private void beforeRay(float partialTicks, CallbackInfo ci) { SilentAimCallbacks.beforeRay(); }

    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void prepareMouse(float partialTicks, long deadline, CallbackInfo ci) { SilentAimCallbacks.prepareMouse(); }

    @Redirect(method = "setupCameraTransform", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;orientCamera(F)V"))
    private void cameraScope(EntityRenderer renderer, float partialTicks) {
        SilentAimCallbacks.beginCamera();
        try { ((IAccessorEntityRenderer)renderer).callOrientCamera(partialTicks); }
        finally { SilentAimCallbacks.endCamera(); }
    }

    @Redirect(method = "renderWorldPass", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ActiveRenderInfo;updateRenderInfo(Lnet/minecraft/entity/player/EntityPlayer;Z)V"))
    private void renderInfoScope(EntityPlayer player, boolean thirdPerson) {
        SilentAimCallbacks.beginRenderInfo();
        try { ActiveRenderInfo.updateRenderInfo(player, thirdPerson); }
        finally { SilentAimCallbacks.endRenderInfo(); }
    }
}
