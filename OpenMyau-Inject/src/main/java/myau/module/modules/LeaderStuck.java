package myau.module.modules;

import myau.Myau;
import myau.access.AccessorMinecraft;
import myau.enums.BlinkModules;
import myau.enums.DelayModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LivingUpdateEvent;
import myau.events.LoadWorldEvent;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.StrafeEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.ISkipTickWorld;
import myau.util.KeyBindUtil;
import myau.util.SkipTickPacketPolicy;
import myau.util.TimerSpeedLease;
import myau.util.WorldTickUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;









public class LeaderStuck extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode =
            new ModeProperty("Mode", 0, new String[]{"Normal", "SkipTick"});
    public final IntProperty stuckTicks =
            new IntProperty("Stuck Ticks", 10, 10, 20);

    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;
    private int tick;
    private boolean using = false;
    private boolean knockbackRelease = false;
    private boolean internalToggle = false;
    private boolean releasing = false;
    private EntityPlayerSP motionOwner;
    private boolean internalReleasePending;
    private volatile boolean externalSkipFreeze;
    private volatile boolean externalAutoRelease;
    private boolean externalDelayOwned;
    private boolean externalDelayKnockback;
    private volatile boolean externalReleasingDelay;
    private WorldClient externalWorld;
    private volatile int requestedSkipTicks;
    private boolean requestedDelayKnockback;
    private EntityPlayerSP requestedPlayer;
    private WorldClient requestedWorld;
    private long freezeRequestSequence;
    private long startedFreezeRequest;

    public LeaderStuck() {
        super("LeaderStuck", false, false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (this.internalToggle) {
            super.setEnabled(enabled);
            return;
        }
        if (!enabled && this.requestedSkipTicks > 0 && !this.internalToggle) {
            this.abortExternalSkipFreeze();
        }
        if (enabled && this.requestedSkipTicks > 0 && !this.internalToggle) {
            return;
        }
        if (!enabled && this.externalSkipFreeze && !this.internalToggle) {
            this.endExternalSkipFreeze();
            return;
        }
        if (enabled && this.externalSkipFreeze && !this.internalToggle) {
            return;
        }
        if (enabled && this.knockbackRelease && !this.internalToggle) {
            return;
        }
        if (!enabled && !this.internalToggle) {
            boolean cleanup = this.using || this.knockbackRelease || this.motionOwner != null;
            this.knockbackRelease = false;
            if (!this.isEnabled()) {
                if (cleanup) {
                    this.onDisabled();
                }
                return;
            }
        }
        super.setEnabled(enabled);
    }


    public static boolean isFrozen() {
        if (Myau.moduleManager == null) {
            return false;
        }
        LeaderStuck stuck = (LeaderStuck) Myau.moduleManager.getModule(LeaderStuck.class);
        return stuck != null && stuck.isStuckActive();
    }

    public boolean isStuckActive() {
        return this.isEnabled() || this.using || this.knockbackRelease || this.releasing
                || this.externalSkipFreeze || this.externalReleasingDelay || this.requestedSkipTicks > 0;
    }

    public boolean beginExternalSkipFreeze(int ticks) {
        return this.beginExternalSkipFreeze(ticks, false, false);
    }

    public boolean freezeForTicks(int ticks) {
        return this.freezeForTicks(ticks, true);
    }


    public static boolean freezePlayerForTicks(int ticks) {
        if (Myau.moduleManager == null) {
            return false;
        }
        LeaderStuck stuck = (LeaderStuck) Myau.moduleManager.getModule(LeaderStuck.class);
        return stuck != null && stuck.freezeForTicks(ticks);
    }

    public synchronized boolean freezeForTicks(int ticks, boolean delayKnockback) {
        if (!mc.isCallingFromMinecraftThread() || ticks <= 0 || mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer.isDead || mc.currentScreen != null || !mc.inGameHasFocus
                || this.isStuckActive() || WorldTickUtil.getSkipTicks() > 0) {
            return false;
        }
        if (Myau.blinkManager == null || Myau.blinkManager.getBlinkingModule() != BlinkModules.NONE) {
            return false;
        }
        if (Myau.delayManager == null
                || Myau.delayManager.getDelayModule() != DelayModules.NONE
                || !Myau.delayManager.delayedPacket.isEmpty()) {
            return false;
        }
        this.requestedDelayKnockback = delayKnockback;
        this.requestedPlayer = mc.thePlayer;
        this.requestedWorld = mc.theWorld;
        this.freezeRequestSequence++;
        this.requestedSkipTicks = ticks;
        return true;
    }

    private synchronized boolean beginExternalSkipFreeze(
            int ticks, boolean autoRelease, boolean delayKnockback) {
        if (!mc.isCallingFromMinecraftThread() || mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer.isDead || mc.currentScreen != null || !mc.inGameHasFocus
                || ticks <= 0) {
            return false;
        }
        if (this.isStuckActive() || this.externalReleasingDelay) {
            return false;
        }
        if (WorldTickUtil.getSkipTicks() > 0) {
            return false;
        }
        if (Myau.blinkManager == null || Myau.blinkManager.getBlinkingModule() != BlinkModules.NONE) {
            return false;
        }
        if (delayKnockback && (Myau.delayManager == null
                || Myau.delayManager.getDelayModule() != DelayModules.NONE
                || !Myau.delayManager.delayedPacket.isEmpty())) {
            return false;
        }
        this.externalAutoRelease = autoRelease;
        this.externalDelayOwned = false;
        this.externalDelayKnockback = delayKnockback;
        this.externalWorld = mc.theWorld;
        TimerSpeedLease.cancelActive();
        this.motionOwner = mc.thePlayer;
        this.savedMotionX = mc.thePlayer.motionX;
        this.savedMotionY = mc.thePlayer.motionY;
        this.savedMotionZ = mc.thePlayer.motionZ;
        KeyBinding.unPressAllKeys();
        mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0.0;
        mc.thePlayer.movementInput.moveForward = mc.thePlayer.movementInput.moveStrafe = 0.0F;
        mc.thePlayer.movementInput.jump = mc.thePlayer.movementInput.sneak = false;
        WorldTickUtil.setSkipTicks(ticks);
        this.externalSkipFreeze = true;
        return true;
    }

    public boolean isExternalSkipFreezeActive() {
        return this.externalSkipFreeze;
    }

    public boolean isReleasingExternalDelay() {
        return this.externalReleasingDelay;
    }

    public boolean isTimedSkipFreezeActive() {
        return this.externalSkipFreeze && this.externalAutoRelease;
    }

    public boolean hasPendingSkipFreeze() {
        return this.requestedSkipTicks > 0;
    }

    public long getFreezeRequestSequence() {
        return this.freezeRequestSequence;
    }

    public boolean hasStartedFreezeRequest(long sequence) {
        return sequence > 0 && this.startedFreezeRequest == sequence;
    }

    public int getFreezeTicksRemaining() {
        if (this.requestedSkipTicks > 0) {
            return this.requestedSkipTicks;
        }
        return this.externalSkipFreeze && this.externalWorld == mc.theWorld
                ? WorldTickUtil.getSkipTicks() : 0;
    }

    public void abortExternalSkipFreeze() {
        this.requestedSkipTicks = 0;
        this.requestedPlayer = null;
        this.requestedWorld = null;
        this.endExternalSkipFreeze(false);
    }

    public void endExternalSkipFreeze() {
        this.endExternalSkipFreeze(true);
    }

    private synchronized void endExternalSkipFreeze(boolean releaseKnockback) {
        if (!this.externalSkipFreeze) {
            return;
        }
        this.externalReleasingDelay = true;
        this.externalSkipFreeze = false;
        boolean releaseDelayed = this.externalDelayOwned;
        this.externalAutoRelease = false;
        this.externalDelayOwned = false;
        this.externalDelayKnockback = false;
        if (this.externalWorld == mc.theWorld) {
            WorldTickUtil.clearSkipTicks();
        } else if (this.externalWorld instanceof ISkipTickWorld) {
            ((ISkipTickWorld) this.externalWorld).leader$setSkipTicks(0);
        }
        boolean samePlayer = mc.thePlayer != null && mc.thePlayer == this.motionOwner
                && mc.theWorld == this.externalWorld;
        if (samePlayer) {
            mc.thePlayer.motionX = this.savedMotionX;
            mc.thePlayer.motionY = this.savedMotionY;
            mc.thePlayer.motionZ = this.savedMotionZ;
        }
        if (samePlayer && mc.currentScreen == null && mc.inGameHasFocus) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindForward.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindBack.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindLeft.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindRight.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindJump.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
        this.motionOwner = null;
        try {
            if (releaseDelayed && Myau.delayManager != null) {
                if (releaseKnockback && samePlayer && !mc.thePlayer.isDead) {
                    Myau.delayManager.releaseDelay(DelayModules.STUCK);
                } else {
                    Myau.delayManager.discardDelay(DelayModules.STUCK);
                }
            }
        } finally {
            this.externalWorld = null;
            this.externalReleasingDelay = false;
        }
    }

    public static boolean shouldSuppressExternalSkipPacket(Packet<?> packet) {
        return isExternalPacketFreezeActive() && SkipTickPacketPolicy.isGameplayPacket(packet);
    }

    public static boolean isExternalPacketFreezeActive() {
        if (Myau.moduleManager == null) {
            return false;
        }
        LeaderStuck stuck = (LeaderStuck) Myau.moduleManager.getModule(LeaderStuck.class);
        return stuck != null && (stuck.externalSkipFreeze || stuck.externalReleasingDelay);
    }

    public static boolean hasRequestedSkipFreeze() {
        if (Myau.moduleManager == null) {
            return false;
        }
        LeaderStuck stuck = (LeaderStuck) Myau.moduleManager.getModule(LeaderStuck.class);
        return stuck != null && stuck.hasPendingSkipFreeze();
    }

    private void setEnabledInternal(boolean enabled) {
        this.internalToggle = true;
        try {
            super.setEnabled(enabled);
        } finally {
            this.internalToggle = false;
        }
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            this.tick = 0;
            this.using = true;
            this.internalReleasePending = false;
            this.savedMotionX = mc.thePlayer.motionX;
            this.savedMotionY = mc.thePlayer.motionY;
            this.savedMotionZ = mc.thePlayer.motionZ;
            this.motionOwner = mc.thePlayer;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        EntityPlayerSP owner = this.motionOwner;
        if (this.externalSkipFreeze && this.externalAutoRelease
                && event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            boolean knockback = this.externalDelayKnockback && owner != null
                    && event.getPacket() instanceof S12PacketEntityVelocity
                    && ((S12PacketEntityVelocity) event.getPacket()).getEntityID() == owner.getEntityId();
            if (event.getPacket() instanceof S27PacketExplosion) {
                S27PacketExplosion explosion = (S27PacketExplosion) event.getPacket();
                knockback = this.externalDelayKnockback && (explosion.func_149149_c() != 0
                        || explosion.func_149144_d() != 0 || explosion.func_149147_e() != 0);
            }
            if (knockback) {
                synchronized (this) {
                    if (this.externalSkipFreeze && this.externalAutoRelease
                            && Myau.delayManager != null
                            && Myau.delayManager.beginDelay(
                                    DelayModules.STUCK,
                                    (Packet<INetHandlerPlayClient>) event.getPacket())) {
                        this.externalDelayOwned = true;
                        event.setCancelled(true);
                    }
                }
            }
            return;
        }
        if (this.isEnabled() && event.getType() == EventType.RECEIVE && mc.thePlayer != null) {
            boolean knockback = false;
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity s12 = (S12PacketEntityVelocity) event.getPacket();
                knockback = s12.getEntityID() == mc.thePlayer.getEntityId();
            } else if (event.getPacket() instanceof S27PacketExplosion) {
                S27PacketExplosion s27 = (S27PacketExplosion) event.getPacket();
                knockback = s27.func_149149_c() != 0.0F || s27.func_149144_d() != 0.0F
                        || s27.func_149147_e() != 0.0F;
            }
            if (knockback) {
                Myau.delayManager.setDelayState(true, DelayModules.VELOCITY);
                Myau.delayManager.delayedPacket.offer(
                        (Packet<INetHandlerPlayClient>) event.getPacket());
                event.setCancelled(true);
                this.knockbackRelease = true;
                this.tick = this.stuckTicks.getValue();
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE && this.requestedSkipTicks > 0) {
            int ticks = this.requestedSkipTicks;
            boolean valid = mc.thePlayer == this.requestedPlayer && mc.theWorld == this.requestedWorld;
            this.requestedSkipTicks = 0;
            this.requestedPlayer = null;
            this.requestedWorld = null;
            if (valid && this.beginExternalSkipFreeze(ticks, true, this.requestedDelayKnockback)) {
                this.startedFreezeRequest = this.freezeRequestSequence;
            }
        }
        if (this.externalSkipFreeze) {
            if (mc.thePlayer == null || mc.theWorld != this.externalWorld
                    || mc.thePlayer != this.motionOwner || mc.thePlayer.isDead
                    || mc.currentScreen != null || !mc.inGameHasFocus) {
                this.endExternalSkipFreeze();
                return;
            }
            if (this.externalAutoRelease && event.getType() == EventType.POST) {
                if (this.getFreezeTicksRemaining() == 0) {
                    this.endExternalSkipFreeze();
                }
            }
            return;
        }
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer != this.motionOwner || mc.thePlayer.isDead) {
            this.setEnabled(false);
            return;
        }

        if (this.isEnabled() && this.using && this.mode.getValue() == 1) {
            if (WorldTickUtil.getSkipTicks() == 0) {
                if (Myau.blinkManager.getBlinkingModule() == BlinkModules.NONE) {
                    Myau.blinkManager.setBlinkState(true, BlinkModules.BLINK);
                }
                WorldTickUtil.setSkipTicks(this.stuckTicks.getValue());
            }
        }
        if (this.using) {
            int releaseTick = this.stuckTicks.getValue();
            if (this.internalReleasePending) {
                this.internalReleasePending = false;
                this.knockbackRelease = false;
                this.setEnabledInternal(true);
            } else if (this.tick >= releaseTick) {
                this.setEnabledInternal(false);
                this.using = true;
                this.internalReleasePending = true;
            }
            this.tick++;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if ((this.isEnabled() || this.externalSkipFreeze) && mc.thePlayer != null) {
            if (this.isEnabled() && !this.externalSkipFreeze && this.mode.getValue() == 0) {
                Myau.blinkManager.setBlinkState(true, BlinkModules.BLINK);
            }
            KeyBinding.unPressAllKeys();
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionZ = 0.0;
            mc.thePlayer.motionY = 0.0;
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if ((this.isEnabled() || this.externalSkipFreeze) && mc.thePlayer != null) {
            mc.thePlayer.movementInput.moveForward = 0.0f;
            mc.thePlayer.movementInput.moveStrafe = 0.0f;
            mc.thePlayer.movementInput.jump = false;
            mc.thePlayer.movementInput.sneak = false;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if ((this.isEnabled() || this.externalSkipFreeze) && mc.thePlayer != null) {
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionY = 0.0;
            mc.thePlayer.motionZ = 0.0;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled() || this.externalSkipFreeze) {
            event.setForward(0.0f);
            event.setStrafe(0.0f);
        }
    }

    @Override
    public void onDisabled() {
        this.endExternalSkipFreeze();
        WorldTickUtil.clearSkipTicks();
        this.using = false;
        if (!this.internalToggle) {
            this.tick = 0;
            this.internalReleasePending = false;
        }
        if (!this.internalToggle) {
            this.knockbackRelease = false;
        }
        if (mc.thePlayer != null && mc.thePlayer == this.motionOwner) {
            mc.thePlayer.motionX = this.savedMotionX;
            mc.thePlayer.motionZ = this.savedMotionZ;
            mc.thePlayer.motionY = this.savedMotionY;
        }
        if (!this.internalToggle) {
            this.motionOwner = null;
        }
        this.releasing = true;
        try {
            if (Myau.delayManager != null && Myau.delayManager.getDelayModule() == DelayModules.VELOCITY) {
                if (mc.getNetHandler() == null) {
                    Myau.delayManager.delayedPacket.clear();
                    Myau.delayManager.delayModule = DelayModules.NONE;
                } else {
                    Myau.delayManager.setDelayState(false, DelayModules.VELOCITY);
                }
            }
            if (Myau.blinkManager != null && Myau.blinkManager.getBlinkingModule() == BlinkModules.BLINK) {
                if (mc.getNetHandler() == null) {
                    Myau.blinkManager.blinkedPackets.clear();
                    Myau.blinkManager.blinking = false;
                    Myau.blinkManager.blinkModule = BlinkModules.NONE;
                } else {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                }
            }
        } finally {
            this.releasing = false;
        }
        if (AccessorMinecraft.getTimer(mc) != null) {
            AccessorMinecraft.getTimer(mc).timerSpeed = 1.0F;
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (this.requestedSkipTicks > 0) {
            this.abortExternalSkipFreeze();
        }
        if (this.externalSkipFreeze) {
            this.abortExternalSkipFreeze();
            return;
        }
        this.setEnabled(false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
