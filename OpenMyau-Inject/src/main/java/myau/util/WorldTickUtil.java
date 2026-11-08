package myau.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;


public final class WorldTickUtil {
    private static World fallbackWorld;
    private static int fallbackSkipTicks;

    private WorldTickUtil() {
    }

    public static int getSkipTicks() {
        World world = Minecraft.getMinecraft().theWorld;
        if (world instanceof ISkipTickWorld) {
            return ((ISkipTickWorld) world).leader$getSkipTicks();
        }
        syncFallbackWorld(world);
        return fallbackSkipTicks;
    }

    public static void setSkipTicks(int ticks) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world instanceof ISkipTickWorld) {
            ((ISkipTickWorld) world).leader$setSkipTicks(ticks);
        } else {
            syncFallbackWorld(world);
            fallbackSkipTicks = Math.max(0, ticks);
        }
    }

    public static boolean consumeSkipTick(World world) {
        if (world == null || world != Minecraft.getMinecraft().theWorld) {
            return false;
        }
        if (world instanceof ISkipTickWorld) {
            ISkipTickWorld skipWorld = (ISkipTickWorld) world;
            int ticks = skipWorld.leader$getSkipTicks();
            if (ticks <= 0) return false;
            skipWorld.leader$setSkipTicks(ticks - 1);
            return true;
        }
        syncFallbackWorld(world);
        if (fallbackSkipTicks <= 0) return false;
        fallbackSkipTicks--;
        return true;
    }

    private static void syncFallbackWorld(World world) {
        if (fallbackWorld != world) {
            fallbackWorld = world;
            fallbackSkipTicks = 0;
        }
    }

    public static void clearSkipTicks() {
        setSkipTicks(0);
    }
}
