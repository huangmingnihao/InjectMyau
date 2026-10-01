package myau.module.modules;

import myau.event.EventTarget;
import myau.events.StuckMoveEntityEvent;
import myau.events.StuckMoveEntityWithHeadingEvent;
import myau.events.StuckMoveInputEvent;
import myau.events.StuckPreLivingUpdateEvent;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 *
 * - StuckMoveEntityEvent / StuckMoveEntityWithHeadingEvent：
 *
 */
public class Stuck extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int MODE_PULSE = 0;
    private static final int MODE_NORMAL = 1;

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"PULSE", "NORMAL"});
    public final FloatProperty pulseDelay = new FloatProperty("PulseDelay", 20.0F, 0.0F, 200.0F);

    private int pulseTimer = 0;
    private boolean passMovement = false;

    public Stuck() {
        super("Stuck", false, false);
    }

    @EventTarget
    public void onStuckMoveInput(StuckMoveInputEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        event.setForward(0.0F);
        event.setStrafe(0.0F);
    }

    @EventTarget
    public void onStuckPreLivingUpdate(StuckPreLivingUpdateEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            return;
        }
        this.passMovement = false;
        player.setSprinting(false);
        if (this.pulseTimer > 0) {
            this.pulseTimer -= 50;
        }
        if (player.hurtTime != 0) {
            this.passMovement = true;
        } else if (this.mode.getValue() == MODE_PULSE && this.pulseTimer <= 0) {
            this.pulseTimer = this.pulseTimer + (int) (this.pulseDelay.getValue() * 50.0F);
            this.passMovement = true;
        }
    }

    @EventTarget
    public void onStuckMoveEntity(StuckMoveEntityEvent event) {
        if (this.isEnabled() && !this.passMovement) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onStuckMoveEntityWithHeading(StuckMoveEntityWithHeadingEvent event) {
        if (this.isEnabled() && !this.passMovement) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDisabled() {
        this.passMovement = false;
        this.pulseTimer = 0;
        if (mc.thePlayer != null) {
            mc.thePlayer.setSprinting(false);
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
