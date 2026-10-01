package myau.util;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public final class MovementTicks {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int CAP = 1000;
    private static int airTicks = 0;
    private static int groundTicks = 0;
    private static int ticksSinceVelocity = CAP;

    public static int air() {
        return airTicks;
    }

    public static int ground() {
        return groundTicks;
    }

    public static int sinceVelocity() {
        return ticksSinceVelocity;
    }

    public static void reset() {
        airTicks = 0;
        groundTicks = 0;
        ticksSinceVelocity = CAP;
    }
    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return;
        }
        if (mc.thePlayer.onGround) {
            airTicks = 0;
            if (groundTicks < CAP) {
                groundTicks++;
            }
        } else {
            groundTicks = 0;
            if (airTicks < CAP) {
                airTicks++;
            }
        }
        if (ticksSinceVelocity < CAP) {
            ticksSinceVelocity++;
        }
    }
    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE || mc.thePlayer == null) {
            return;
        }
        if (event.getPacket() instanceof S12PacketEntityVelocity
                && ((S12PacketEntityVelocity) event.getPacket()).getEntityID() == mc.thePlayer.getEntityId()) {
            ticksSinceVelocity = 0;
        }
    }
}
