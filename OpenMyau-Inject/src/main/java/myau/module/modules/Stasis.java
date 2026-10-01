package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;

public class Stasis extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public Stasis() {
        super("Stasis", false);
    }
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionY = 0.0;
        mc.thePlayer.motionZ = 0.0;
    }
    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.thePlayer.movementInput == null) {
            return;
        }
        mc.thePlayer.movementInput.moveForward = 0.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
    }
    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) {
            return;
        }
        if (!(event.getPacket() instanceof C03PacketPlayer)) {
            return;
        }
        if (!(event.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook)) {
            event.setCancelled(true);
        }
    }
}
