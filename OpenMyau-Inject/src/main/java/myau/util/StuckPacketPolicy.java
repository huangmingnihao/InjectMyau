package myau.util;

import net.minecraft.network.play.client.C03PacketPlayer;

public final class StuckPacketPolicy {
    private StuckPacketPolicy() {}
    public static C03PacketPlayer lookOnly(C03PacketPlayer packet, float yaw, float pitch) {
        if (packet instanceof C03PacketPlayer.C05PacketPlayerLook) return packet;
        // Keep the outgoing silent angles, rather than substituting the camera angles.
        return new C03PacketPlayer.C05PacketPlayerLook(
                packet.getRotating() ? packet.getYaw() : yaw,
                packet.getRotating() ? packet.getPitch() : pitch, packet.isOnGround());
    }
}
