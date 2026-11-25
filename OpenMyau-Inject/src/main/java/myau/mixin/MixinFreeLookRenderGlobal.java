package myau.mixin;

import myau.inject.FreeLookCallbacks;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import org.lwjgl.util.vector.Vector3f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {RenderGlobal.class}, priority = 9999)
public abstract class MixinFreeLookRenderGlobal {
    @Redirect(
            method = "setupTerrain",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;rotationYaw:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float myauTerrainYaw(Entity entity) {
        return FreeLookCallbacks.terrainYaw(entity);
    }

    @Redirect(
            method = "setupTerrain",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;rotationPitch:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float myauTerrainPitch(Entity entity) {
        return FreeLookCallbacks.terrainPitch(entity);
    }

    @Redirect(
            method = "setupTerrain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;getViewVector(Lnet/minecraft/entity/Entity;D)Lorg/lwjgl/util/vector/Vector3f;"
            )
    )
    private Vector3f myauTerrainViewVector(RenderGlobal renderGlobal, Entity entity, double partialTicks) {
        return FreeLookCallbacks.terrainViewVector(renderGlobal, entity, partialTicks);
    }
}
