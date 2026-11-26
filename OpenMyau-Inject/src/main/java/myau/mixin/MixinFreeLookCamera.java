package myau.mixin;

import myau.inject.FreeLookCallbacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin implementation of the freelook camera hooks, used when the client runs as a
 * regular loader mod instead of the JVMTI injection path.
 */
@Mixin(value = {EntityRenderer.class}, priority = 9999)
public abstract class MixinFreeLookCamera {
    private static boolean myauFrameLogged;

    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void myauFrameTick(float partialTicks, long nanoTime, CallbackInfo callbackInfo) {
        if (!myauFrameLogged) {
            myauFrameLogged = true;
            myau.inject.Diag.log("freelook frameTick hook reached");
        }
        FreeLookCallbacks.frameTick();
    }

    @Redirect(
            method = "updateCameraAndRender",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Minecraft;inGameHasFocus:Z",
                    opcode = Opcodes.GETFIELD
            )
    )
    private boolean myauOverrideMouse(Minecraft minecraft) {
        return FreeLookCallbacks.overrideMouse(minecraft);
    }

    @Redirect(
            method = "orientCamera",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;rotationYaw:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float myauOrientYaw(Entity entity) {
        return FreeLookCallbacks.orientYaw(entity);
    }

    @Redirect(
            method = "orientCamera",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float myauOrientPrevYaw(Entity entity) {
        return FreeLookCallbacks.orientPrevYaw(entity);
    }

    @Redirect(
            method = "orientCamera",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;rotationPitch:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float myauOrientPitch(Entity entity) {
        return FreeLookCallbacks.orientPitch(entity);
    }

    @Redirect(
            method = "orientCamera",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float myauOrientPrevPitch(Entity entity) {
        return FreeLookCallbacks.orientPrevPitch(entity);
    }
}
