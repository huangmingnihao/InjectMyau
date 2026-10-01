package myau.util;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;

public final class BadPacketsUtil {
    private static boolean slot;
    private static boolean attack;
    private static boolean swing;
    private static boolean block;
    private static boolean inventory;
    public BadPacketsUtil() {
    }
    public static boolean bad() {
        return bad(true, true, true, true, true);
    }
    public static boolean bad(boolean slot, boolean attack, boolean swing, boolean block, boolean inventory) {
        return BadPacketsUtil.slot && slot
                || BadPacketsUtil.attack && attack
                || BadPacketsUtil.swing && swing
                || BadPacketsUtil.block && block
                || BadPacketsUtil.inventory && inventory;
    }
    public static void reset() {
        slot = false;
        swing = false;
        attack = false;
        block = false;
        inventory = false;
    }
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof C09PacketHeldItemChange) {
            slot = true;
        } else if (packet instanceof C0APacketAnimation) {
            swing = true;
        } else if (packet instanceof C02PacketUseEntity) {
            attack = true;
        } else if (packet instanceof C08PacketPlayerBlockPlacement || packet instanceof C07PacketPlayerDigging) {
            block = true;
        } else if (packet instanceof C0EPacketClickWindow || packet instanceof C0DPacketCloseWindow) {
            inventory = true;
        } else if (packet instanceof C03PacketPlayer) {
            reset();
        }
    }
}
