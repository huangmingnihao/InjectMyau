package myau.module.modules;

import net.minecraft.client.Minecraft;








final class ScaffoldSupport {
    private ScaffoldSupport() {
    }


    static int randomSwapSlot() {
        int current = Minecraft.getMinecraft().thePlayer.inventory.currentItem;
        int slot = java.util.concurrent.ThreadLocalRandom.current().nextInt(9);
        while (slot == current) {
            slot = java.util.concurrent.ThreadLocalRandom.current().nextInt(9);
        }
        return slot;
    }


    static int altSlot(int handle) {
        return handle % 8 + 1;
    }
}
