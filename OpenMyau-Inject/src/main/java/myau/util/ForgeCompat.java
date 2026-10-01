package myau.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ForgeCompat {
    private static Boolean present;
    private static Method onPlayerAttackTarget;
    private static Field forgeLightPipelineEnabled;
    private static boolean resolved;
    private ForgeCompat() {
    }
    public static synchronized boolean isPresent() {
        if (present == null) {
            present = Boolean.valueOf(find("net.minecraftforge.common.MinecraftForge") != null);
        }
        return present.booleanValue();
    }
    public static boolean onPlayerAttackTarget(EntityPlayer player, Entity target) {
        resolve();
        if (onPlayerAttackTarget == null) {
            return true;
        }
        try {
            return ((Boolean) onPlayerAttackTarget.invoke(null, player, target)).booleanValue();
        } catch (Throwable t) {
            return true;
        }
    }

    public static void setLightPipelineEnabled(boolean enabled) {
        resolve();
        if (forgeLightPipelineEnabled == null) {
            return;
        }
        try {
            forgeLightPipelineEnabled.setBoolean(null, enabled);
        } catch (Throwable ignored) {
        }
    }
    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        Class<?> hooks = find("net.minecraftforge.common.ForgeHooks");
        if (hooks != null) {
            try {
                onPlayerAttackTarget = hooks.getMethod("onPlayerAttackTarget",
                        EntityPlayer.class, Entity.class);
            } catch (Throwable ignored) {
            }
        }
        Class<?> container = find("net.minecraftforge.common.ForgeModContainer");
        if (container != null) {
            try {
                forgeLightPipelineEnabled = container.getField("forgeLightPipelineEnabled");
            } catch (Throwable ignored) {
            }
        }
    }
    private static Class<?> find(String name) {
        try {
            return Class.forName(name, false, ForgeCompat.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }
}
