package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.util.PacketUtil;
import myau.util.StuckFreezeState;
import myau.util.StuckPacketPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class Stuck extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"PULSE", "NORMAL"});
    public final FloatProperty pulseDelay = new FloatProperty("PulseDelay", 20.0F, 0.0F, 200.0F);
    private final StuckFreezeState state = new StuckFreezeState();
    private volatile EntityPlayerSP owner;
    private double savedX, savedY, savedZ;
    private boolean motionSaved;
    private volatile long generation;

    public Stuck() { super("Stuck", false, false); }

    public static boolean isManualActive() {
        if (Myau.moduleManager == null) return false;
        Stuck stuck = (Stuck) Myau.moduleManager.getModule(Stuck.class);
        return stuck != null && stuck.isEnabled();
    }

    private boolean validOwner() {
        EntityPlayerSP player = owner;
        return isEnabled() && player != null && player == mc.thePlayer && mc.theWorld == player.worldObj;
    }
    private boolean frozen() { return validOwner() && state.isFrozen(); }

    @Override
    public void onEnabled() {
        generation++;
        // End an automatic freeze before taking ownership of player motion.
        if (Myau.moduleManager != null) {
            LeaderStuck smart = (LeaderStuck) Myau.moduleManager.getModule(LeaderStuck.class);
            if (smart != null && smart.isStuckActive()) {
                smart.abortExternalSkipFreeze();
                smart.setEnabled(false);
            }
        }
        owner = mc.thePlayer;
        motionSaved = false;
        state.enable();
    }

    private void restoreMotion() {
        if (motionSaved && owner == mc.thePlayer && owner != null && owner.worldObj == mc.theWorld) {
            owner.motionX = savedX; owner.motionY = savedY; owner.motionZ = savedZ;
        }
        motionSaved = false;
    }
    private void zeroMotion() { owner.motionX = owner.motionY = owner.motionZ = 0.0; }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (!validOwner()) {
            motionSaved = false;
            owner = mc.thePlayer;
            state.enable();
            generation++;
            if (!validOwner()) return;
        }
        boolean wasFrozen = state.isFrozen();
        state.tick(mode.getValue() == 0, Math.round(pulseDelay.getValue()));
        if (wasFrozen && !state.isFrozen()) restoreMotion();
        if (!wasFrozen && state.isFrozen()) {
            savedX = owner.motionX; savedY = owner.motionY; savedZ = owner.motionZ;
            motionSaved = true;
        }
        if (state.isFrozen()) zeroMotion();
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!validOwner() || !state.blocksInput()) return;
        // All Scaffold modes and MovementFix have finished writing input.
        owner.movementInput.moveForward = owner.movementInput.moveStrafe = 0.0F;
        owner.movementInput.jump = false;
    }
    @EventTarget(Priority.LOWEST)
    public void onStuckPreLivingUpdate(StuckPreLivingUpdateEvent event) {
        if (frozen()) zeroMotion();
    }
    @EventTarget(Priority.LOWEST)
    public void onStuckMoveEntity(StuckMoveEntityEvent event) {
        if (frozen()) { zeroMotion(); event.setCancelled(true); }
    }
    @EventTarget(Priority.LOWEST)
    public void onStuckMoveEntityWithHeading(StuckMoveEntityWithHeadingEvent event) {
        if (frozen()) { zeroMotion(); event.setCancelled(true); }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!validOwner() || event.isCancelled()) return;
        EntityPlayerSP player = owner;
        if (player == null) return;
        if (event.getType() == EventType.SEND && frozen() && event.getPacket() instanceof C03PacketPlayer) {
            C03PacketPlayer original = (C03PacketPlayer) event.getPacket();
            C03PacketPlayer replacement = StuckPacketPolicy.lookOnly(original, player.rotationYaw, player.rotationPitch);
            if (replacement != original) {
                event.setCancelled(true);
                PacketUtil.sendPacket(replacement);
            }
        } else if (event.getType() == EventType.RECEIVE) {
            int delay = 0;
            if (event.getPacket() instanceof S08PacketPlayerPosLook) delay = 3;
            if (event.getPacket() instanceof S12PacketEntityVelocity &&
                    ((S12PacketEntityVelocity) event.getPacket()).getEntityID() == player.getEntityId()) delay = 4;
            if (event.getPacket() instanceof S27PacketExplosion) {
                S27PacketExplosion p = (S27PacketExplosion) event.getPacket();
                if (p.func_149149_c() != 0 || p.func_149144_d() != 0 || p.func_149147_e() != 0) delay = 4;
            }
            if (delay > 0) {
                final int resumeDelay = delay;
                final long expectedGeneration = generation;
                // Runs before vanilla's queued packet task, so restoration cannot overwrite
                // the velocity/teleport that the server is about to apply.
                Runnable release = () -> {
                    if (validOwner() && generation == expectedGeneration) {
                        restoreMotion();
                        state.resumeAfter(resumeDelay);
                    }
                };
                if (mc.isCallingFromMinecraftThread()) release.run();
                else mc.addScheduledTask(release);
            }
        }
    }

    @Override
    public void onDisabled() {
        generation++;
        restoreMotion();
        state.reset();
        owner = null;
    }
    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        generation++;
        motionSaved = false;
        owner = null;
        state.reset();
    }
    @Override
    public String[] getSuffix() { return new String[]{mode.getModeString()}; }
}
