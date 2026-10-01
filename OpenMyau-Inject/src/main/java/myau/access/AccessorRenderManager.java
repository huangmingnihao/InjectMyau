package myau.access;

import java.lang.reflect.Field;

import myau.inject.MappingBridge;
import net.minecraft.client.renderer.entity.RenderManager;

public final class AccessorRenderManager {
    private static final String OWNER = "net.minecraft.client.renderer.entity.RenderManager";
    private static final Field F_RENDERPOSX =
            MappingBridge.field(OWNER, "renderPosX", double.class);
    private static final Field F_RENDERPOSY =
            MappingBridge.field(OWNER, "renderPosY", double.class);
    private static final Field F_RENDERPOSZ =
            MappingBridge.field(OWNER, "renderPosZ", double.class);
    private static final Field F_PLAYERVIEWX =
            MappingBridge.field(OWNER, "playerViewX", float.class);
    private static final Field F_PLAYERVIEWY =
            MappingBridge.field(OWNER, "playerViewY", float.class);
    private AccessorRenderManager() {
    }
    public static double getRenderPosX(RenderManager owner) {
        try {
            return F_RENDERPOSX.getDouble(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "renderPosX", t);
            return 0.0D;
        }
    }
    public static double getRenderPosY(RenderManager owner) {
        try {
            return F_RENDERPOSY.getDouble(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "renderPosY", t);
            return 0.0D;
        }
    }
    public static double getRenderPosZ(RenderManager owner) {
        try {
            return F_RENDERPOSZ.getDouble(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "renderPosZ", t);
            return 0.0D;
        }
    }
    public static void setPlayerViewX(RenderManager owner, float value) {
        try {
            F_PLAYERVIEWX.setFloat(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "playerViewX", t);
        }
    }
    public static void setPlayerViewY(RenderManager owner, float value) {
        try {
            F_PLAYERVIEWY.setFloat(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "playerViewY", t);
        }
    }
}
