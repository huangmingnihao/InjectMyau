package myau.inject;

import org.lwjgl.util.vector.Vector3f;

import myau.Myau;
import myau.access.AccessorMinecraft;
import myau.access.AccessorRenderManager;
import myau.module.modules.FreeLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MouseHelper;

/**
 * Runtime hooks backing the {@link FreeLook} module.  Each method replaces one of
 * the vanilla field reads / calls that the raven-bS Mixin freelook redirects, so
 * the same behaviour is reproduced without Mixin and survives obfuscation.
 */
public final class FreeLookCallbacks {
    public static final String OWNER = "myau/inject/FreeLookCallbacks";

    private FreeLookCallbacks() {
    }

    private static FreeLook module() {
        if (Myau.moduleManager == null) {
            return null;
        }
        return (FreeLook) Myau.moduleManager.modules.get(FreeLook.class);
    }

    private static boolean active() {
        FreeLook module = module();
        return module != null && module.isEnabled() && FreeLook.perspectiveToggled;
    }

    /** Runs every rendered frame from EntityRenderer.updateCameraAndRender. */
    public static void frameTick() {
        try {
            FreeLook module = module();
            if (module != null) {
                module.onFrame();
            }
        } catch (Throwable t) {
            Log.swallowed(t);
        }
    }

    // ---- EntityRenderer.orientCamera: camera placement -----------------------

    public static float orientYaw(Entity entity) {
        return active() ? FreeLook.cameraYaw : entity.rotationYaw;
    }

    public static float orientPrevYaw(Entity entity) {
        return active() ? FreeLook.cameraYaw : entity.prevRotationYaw;
    }

    public static float orientPitch(Entity entity) {
        return active() ? FreeLook.cameraPitch : entity.rotationPitch;
    }

    public static float orientPrevPitch(Entity entity) {
        return active() ? FreeLook.cameraPitch : entity.prevRotationPitch;
    }

    // ---- ActiveRenderInfo.updateRenderInfo: view vector ---------------------

    public static float renderInfoYaw(Entity entity) {
        return active() && entity instanceof EntityPlayer
                ? FreeLook.cameraYaw : entity.rotationYaw;
    }

    public static float renderInfoPitch(Entity entity) {
        return active() && entity instanceof EntityPlayer
                ? FreeLook.cameraPitch : entity.rotationPitch;
    }

    // ---- RenderGlobal.setupTerrain: frustum orientation ---------------------

    public static float terrainYaw(Entity entity) {
        return active() && entity == Minecraft.getMinecraft().getRenderViewEntity()
                ? FreeLook.cameraYaw : entity.rotationYaw;
    }

    public static float terrainPitch(Entity entity) {
        return active() && entity == Minecraft.getMinecraft().getRenderViewEntity()
                ? FreeLook.cameraPitch : entity.rotationPitch;
    }

    public static Vector3f terrainViewVector(RenderGlobal renderGlobal,
                                             Entity entityIn, double partialTicks) {
        float pitch;
        float yaw;
        if (entityIn == Minecraft.getMinecraft().getRenderViewEntity() && active()) {
            pitch = FreeLook.cameraPitch;
            yaw = FreeLook.cameraYaw;
        } else {
            pitch = (float) ((double) entityIn.prevRotationPitch
                    + (double) (entityIn.rotationPitch - entityIn.prevRotationPitch) * partialTicks);
            yaw = (float) ((double) entityIn.prevRotationYaw
                    + (double) (entityIn.rotationYaw - entityIn.prevRotationYaw) * partialTicks);
        }
        if (Minecraft.getMinecraft().gameSettings.thirdPersonView == 2) {
            pitch += 180.0F;
        }
        float cosYaw = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sinYaw = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cosPitch = -MathHelper.cos(-pitch * 0.017453292F);
        float sinPitch = MathHelper.sin(-pitch * 0.017453292F);
        return new Vector3f(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    // ---- RenderManager.cacheActiveRenderInfo: entity render angles ---------

    public static void setPlayerViewX(RenderManager renderManager, float value) {
        AccessorRenderManager.setPlayerViewX(renderManager, active() ? FreeLook.cameraPitch : value);
    }

    public static void setPlayerViewY(RenderManager renderManager, float value) {
        AccessorRenderManager.setPlayerViewY(renderManager, active() ? FreeLook.cameraYaw : value);
    }

    // ---- EntityRenderer.updateCameraAndRender: mouse handling --------------

    /** Replaces the {@code Minecraft.inGameHasFocus} read guarding mouse input. */
    public static boolean overrideMouse(Minecraft mc) {
        try {
            if (!AccessorMinecraft.isInGameHasFocus(mc)) {
                return false;
            }
            FreeLook module = module();
            if (module == null || !module.isEnabled() || !FreeLook.perspectiveToggled) {
                return true;
            }
            MouseHelper helper = AccessorMinecraft.getMouseHelper(mc);
            if (helper == null) {
                return false;
            }
            helper.mouseXYChange();
            float sens = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
            float mult = sens * sens * sens * 8.0F;
            float fdx = helper.deltaX * mult;
            float fdy = helper.deltaY * mult;
            FreeLook.cameraYaw += fdx * 0.15F;
            if (module.invertPitch.getValue()) {
                fdy = -fdy;
            }
            FreeLook.cameraPitch += fdy * 0.15F;
            if (module.lockPitch.getValue()) {
                FreeLook.cameraPitch = Math.max(-90.0F, Math.min(90.0F, FreeLook.cameraPitch));
            }
            if (module.customFov.getValue()) {
                mc.gameSettings.fovSetting = module.fov.getValue();
            }
        } catch (Throwable t) {
            Log.swallowed(t);
        }
        return false;
    }

    // ---- Minecraft.runTick: F5 perspective toggle --------------------------

    /** Replaces the PUTFIELD of GameSettings.thirdPersonView inside runTick. */
    public static void setThirdPersonView(GameSettings gameSettings, int value) {
        try {
            if (active()) {
                FreeLook module = module();
                if (module != null) {
                    module.resetPerspective();
                    return;
                }
            }
        } catch (Throwable t) {
            Log.swallowed(t);
        }
        gameSettings.thirdPersonView = value;
    }
}
