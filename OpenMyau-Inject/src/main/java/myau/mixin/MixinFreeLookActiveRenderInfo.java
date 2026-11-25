package myau.mixin;

import myau.inject.FreeLookCallbacks;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.player.EntityPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {ActiveRenderInfo.class}, priority = 9999)
public abstract class MixinFreeLookActiveRenderInfo {
    @Redirect(
            method = "updateRenderInfo",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;rotationYaw:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private static float myauRenderInfoYaw(EntityPlayer player) {
        return FreeLookCallbacks.renderInfoYaw(player);
    }

    @Redirect(
            method = "updateRenderInfo",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;rotationPitch:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private static float myauRenderInfoPitch(EntityPlayer player) {
        return FreeLookCallbacks.renderInfoPitch(player);
    }
}
