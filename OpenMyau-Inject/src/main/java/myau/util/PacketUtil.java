package myau.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

public class PacketUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final ThreadLocal<SendAttempt> sendAttempt = new ThreadLocal<>();

    public static NetworkManager getConnection() {
        return mc.getNetHandler() == null ? null : mc.getNetHandler().getNetworkManager();
    }








    public static boolean trySendPacket(Packet<?> packet) {
        if (packet == null || mc.getNetHandler() == null) {
            return false;
        }
        SendAttempt previous = sendAttempt.get();
        SendAttempt attempt = new SendAttempt(packet, previous);
        sendAttempt.set(attempt);
        try {
            sendPacket(packet);
            return !attempt.cancelled;
        } finally {
            if (previous == null) {
                sendAttempt.remove();
            } else {
                sendAttempt.set(previous);
            }
        }
    }


    public static void markSendCancelled(Packet<?> packet) {
        for (SendAttempt attempt = sendAttempt.get(); attempt != null; attempt = attempt.previous) {
            if (attempt.packet == packet) {
                attempt.cancelled = true;
            }
        }
    }

    public static void sendPacket(Packet<?> packet) {
        mc.getNetHandler().getNetworkManager().sendPacket(packet);
    }

    public static void sendPacketNoEvent(Packet<?> packet) {
        mc.getNetHandler().getNetworkManager().sendPacket(packet, null);
    }

    private static final class SendAttempt {
        private final Packet<?> packet;
        private final SendAttempt previous;
        private boolean cancelled;

        private SendAttempt(Packet<?> packet, SendAttempt previous) {
            this.packet = packet;
            this.previous = previous;
        }
    }
}
