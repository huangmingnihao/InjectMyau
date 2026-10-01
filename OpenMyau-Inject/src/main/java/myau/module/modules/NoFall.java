package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.PlayerUpdateEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.access.AccessorC03PacketPlayer;
import myau.access.AccessorKeyBinding;
import myau.access.AccessorMinecraft;
import myau.access.AccessorPlayerControllerMP;
import myau.module.Module;
import myau.util.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;

import java.util.ArrayList;
import java.util.List;

public class NoFall extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil packetDelayTimer = new TimerUtil();
    private final TimerUtil scoreboardResetTimer = new TimerUtil();
    private boolean slowFalling = false;
    private boolean lastOnGround = false;
    public final ModeProperty mode = new ModeProperty(
            "mode", 0, new String[]{"PACKET", "BLINK", "NO_GROUND", "SPOOF", "GRIM_SERVER_1_9", "WATER"}
    );
    public final FloatProperty distance = new FloatProperty("distance", 3.0F, 0.0F, 20.0F);
    public final IntProperty delay = new IntProperty("delay", 0, 0, 10000);
    public final BooleanProperty newestGrim = new BooleanProperty("newest-grim", false,
            () -> this.mode.getValue() == MODE_GRIM_SERVER_19);
    private static final int MODE_GRIM_SERVER_19 = 4;

    private static final int MODE_WATER = 5;

    private static final float WATER_MIN_FALL = 3.3F;

    private static final long WATER_PLACE_DELAY = 500L;
    private static final long WATER_PICKUP_WAIT = 150L;
    private static final float WATER_PITCH = 90.0F;
    private static final float WATER_MANUAL_PITCH = 80.0F;
    private static final int WATER_ROTATION_PRIORITY = 8;
    public final BooleanProperty waterPickup = new BooleanProperty("water-pickup", true,
            () -> this.mode.getValue() == MODE_WATER);
    public final BooleanProperty waterSilentAim = new BooleanProperty("water-silent-aim", true,
            () -> this.mode.getValue() == MODE_WATER);
    public final BooleanProperty waterSwitch = new BooleanProperty("water-switch", true,
            () -> this.mode.getValue() == MODE_WATER);
    private long waterLastPlace = 0L;
    private boolean waterShouldPickup = false;
    private int waterLastSlot = -1;
    private boolean shouldNoFall = false;
    private boolean suppressMotion = false;
    private boolean shouldJump = false;
    private boolean holdingPackets = false;
    private boolean releasing = false;
    private final List<S32PacketConfirmTransaction> heldPackets = new ArrayList<>();
    private boolean canTrigger() {
        return this.scoreboardResetTimer.hasTimeElapsed(3000) && this.packetDelayTimer.hasTimeElapsed(this.delay.getValue().longValue());
    }
    public NoFall() {
        super("No Fall", false);
    }
    @EventTarget(Priority.HIGH)
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S08PacketPlayerPosLook) {
            this.onDisabled();
        } else if (this.isEnabled() && event.getType() == EventType.SEND && !event.isCancelled()) {
            if (event.getPacket() instanceof C03PacketPlayer) {
                C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
                switch (this.mode.getValue()) {
                    case 0:
                        if (this.slowFalling) {
                            this.slowFalling = false;
                            AccessorMinecraft.getTimer(mc).timerSpeed = 1.0F;
                        } else if (!packet.isOnGround()) {
                            AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
                            if (PlayerUtil.canFly(this.distance.getValue())
                                    && !PlayerUtil.checkInWater(aabb)
                                    && this.canTrigger()) {
                                this.packetDelayTimer.reset();
                                this.slowFalling = true;
                                AccessorMinecraft.getTimer(mc).timerSpeed = 0.5F;
                            }
                        }
                        break;
                    case 1:
                        boolean allowed = !mc.thePlayer.isOnLadder() && !mc.thePlayer.capabilities.allowFlying && mc.thePlayer.hurtTime == 0;
                        if (Myau.blinkManager.getBlinkingModule() != BlinkModules.NO_FALL) {
                            if (this.lastOnGround
                                    && !packet.isOnGround()
                                    && allowed
                                    && PlayerUtil.canFly(this.distance.getValue().intValue())
                                    && mc.thePlayer.motionY < 0.0) {
                                Myau.blinkManager.setBlinkState(false, Myau.blinkManager.getBlinkingModule());
                                Myau.blinkManager.setBlinkState(true, BlinkModules.NO_FALL);
                            }
                        } else if (!allowed) {
                            Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            ChatUtil.sendFormatted(String.format("%s%s: &cFailed player check!&r", Myau.clientName, this.getName()));
                        } else if (PlayerUtil.checkInWater(mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0))) {
                            Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            ChatUtil.sendFormatted(String.format("%s%s: &cFailed void check!&r", Myau.clientName, this.getName()));
                        } else if (packet.isOnGround()) {
                            for (Packet<?> blinkedPacket : Myau.blinkManager.blinkedPackets) {
                                if (blinkedPacket instanceof C03PacketPlayer) {
                                    AccessorC03PacketPlayer.setOnGround((C03PacketPlayer) blinkedPacket, true);
                                }
                            }
                            Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            this.packetDelayTimer.reset();
                        }
                        this.lastOnGround = packet.isOnGround() && allowed && this.canTrigger();
                        break;
                    case 2:
                        AccessorC03PacketPlayer.setOnGround(packet, false);
                        break;
                    case MODE_GRIM_SERVER_19:
                        if (this.suppressMotion) {
                            this.suppressMotion = false;
                            event.setCancelled(true);
                            if (this.newestGrim.getValue()) {
                                PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                                        mc.thePlayer.posX, mc.thePlayer.posY + 0.01, mc.thePlayer.posZ, true));
                            }
                            PacketUtil.sendPacketNoEvent(new C03PacketPlayer(true));
                        }
                        break;
                    case 3:
                        if (!packet.isOnGround()) {
                            AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
                            if (PlayerUtil.canFly(this.distance.getValue())
                                    && !PlayerUtil.checkInWater(aabb)
                                    && this.canTrigger()) {
                                this.packetDelayTimer.reset();
                                AccessorC03PacketPlayer.setOnGround(packet, true);
                                mc.thePlayer.fallDistance = 0.0F;
                            }
                        }
                }
            }
        }
    }
    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != MODE_GRIM_SERVER_19) {
            return;
        }
        if (mc.thePlayer == null || mc.thePlayer.isCollidedHorizontally) {
            return;
        }
        if (mc.thePlayer.motionY > 0.1) {
            this.shouldNoFall = false;
        }
        if (mc.thePlayer.fallDistance > this.distance.getValue()) {
            this.shouldNoFall = true;
        }
        if (this.shouldNoFall && mc.thePlayer.onGround) {
            this.suppressMotion = true;
            AccessorKeyBinding.setPressed(mc.gameSettings.keyBindJump, false);
            mc.thePlayer.fallDistance = 0.0F;
            this.shouldNoFall = false;
        }
        if (this.shouldNoFall) {
            AccessorKeyBinding.setPressed(mc.gameSettings.keyBindJump, false);
        }
    }
    @EventTarget(Priority.HIGH)
    public void onGrimPacket(PacketEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != MODE_GRIM_SERVER_19) {
            return;
        }
        if (event.getType() != EventType.RECEIVE || mc.thePlayer == null || this.releasing) {
            return;
        }
        if (mc.thePlayer.ticksExisted < 10 || mc.thePlayer.isCollidedHorizontally) {
            return;
        }

        if (event.getPacket() instanceof S12PacketEntityVelocity && !event.isCancelled()) {
            S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) event.getPacket();
            if (velocity.getEntityID() != mc.thePlayer.getEntityId()) {
                return;
            }

            if (velocity.getMotionY() > 0
                    || MovementTicks.sinceVelocity() <= 14
                    || MovementTicks.ground() <= 1) {
                this.shouldJump = true;
            }
            if (!mc.thePlayer.onGround && this.shouldNoFall) {
                this.holdingPackets = true;
            }
            return;
        }

        if (this.holdingPackets && event.getPacket() instanceof S32PacketConfirmTransaction) {
            event.setCancelled(true);
            this.heldPackets.add((S32PacketConfirmTransaction) event.getPacket());
        }
    }
    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != MODE_GRIM_SERVER_19) {
            return;
        }
        if (mc.thePlayer == null || mc.thePlayer.isCollidedHorizontally) {
            return;
        }
        if (this.shouldJump && this.shouldNoFall) {
            mc.thePlayer.movementInput.jump = true;
            this.shouldJump = false;
        }
    }
    private void releaseHeldPackets() {
        if (this.heldPackets.isEmpty()) {
            this.heldPackets.clear();
            return;
        }
        if (mc.getNetHandler() == null) {
            this.heldPackets.clear();
            return;
        }
        this.releasing = true;
        try {
            for (S32PacketConfirmTransaction packet : this.heldPackets) {
                try {
                    packet.processPacket(mc.getNetHandler());
                } catch (Throwable ignored) {
                }
            }
        } finally {
            this.heldPackets.clear();
            this.releasing = false;
        }
    }
    @EventTarget
    public void onWaterRender(Render3DEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != MODE_WATER || mc.thePlayer == null) {
            return;
        }
        if (mc.isGamePaused() || mc.thePlayer.capabilities.isFlying
                || mc.thePlayer.capabilities.isCreativeMode) {
            return;
        }
        if (!this.isFallingFarEnough()) {
            return;
        }
        float pitch = this.waterSilentAim.getValue() ? WATER_PITCH : mc.thePlayer.rotationPitch;
        MovingObjectPosition hit = RotationUtil.rayTrace(mc.thePlayer.rotationYaw, pitch,
                mc.playerController.getBlockReachDistance(), 1.0F);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || hit.sideHit != EnumFacing.UP) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.waterLastPlace < WATER_PLACE_DELAY) {
            return;
        }
        if (this.waterSwitch.getValue() && !this.isHolding(Items.water_bucket)) {
            this.switchToWater();
        }
        if (!this.waterSilentAim.getValue() && mc.thePlayer.rotationPitch < WATER_MANUAL_PITCH) {
            return;
        }
        if (!this.isHolding(Items.water_bucket)) {
            return;
        }
        this.waterLastPlace = now;
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        this.waterShouldPickup = this.waterPickup.getValue();
        if (!this.waterShouldPickup) {
            this.waterLastSlot = -1;
        }
    }

    @EventTarget
    public void onWaterUpdate(UpdateEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != MODE_WATER
                || event.getType() != EventType.PRE || mc.thePlayer == null || mc.isGamePaused()) {
            return;
        }
        if (this.waterSilentAim.getValue() && this.isWaterAimWanted()) {
            event.setRotation(event.getNewYaw(), WATER_PITCH, WATER_ROTATION_PRIORITY);
        }
        if (!this.waterShouldPickup
                || System.currentTimeMillis() - this.waterLastPlace <= WATER_PICKUP_WAIT
                || !this.isHolding(Items.bucket)) {
            return;
        }
        this.waterShouldPickup = false;
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        if (this.waterLastSlot != -1) {
            this.switchSlot(this.waterLastSlot);
            this.waterLastSlot = -1;
        }
    }
    private boolean isFallingFarEnough() {
        return !mc.thePlayer.onGround && mc.thePlayer.fallDistance >= WATER_MIN_FALL;
    }
    private boolean isWaterAimWanted() {
        return (this.isFallingFarEnough()
                || System.currentTimeMillis() - this.waterLastPlace < WATER_PLACE_DELAY)
                && this.findHotbarSlot(Items.water_bucket) != -1;
    }
    private boolean isHolding(Item item) {
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() == item;
    }
    private int findHotbarSlot(Item item) {
        for (int slot = 0; slot < InventoryPlayer.getHotbarSize(); slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() == item) {
                return slot;
            }
        }
        return -1;
    }
    private void switchToWater() {
        int slot = this.findHotbarSlot(Items.water_bucket);
        if (slot != -1) {
            this.waterLastSlot = mc.thePlayer.inventory.currentItem;
            this.switchSlot(slot);
        }
    }
    private void switchSlot(int slot) {
        mc.thePlayer.inventory.currentItem = slot;
        AccessorPlayerControllerMP.callSyncCurrentPlayItem(mc.playerController);
    }
    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (ServerUtil.hasPlayerCountInfo()) {
                this.scoreboardResetTimer.reset();
            }
            if (this.mode.getValue() == MODE_GRIM_SERVER_19 && this.holdingPackets && mc.thePlayer.onGround) {
                this.holdingPackets = false;
                this.releaseHeldPackets();
            }
            if (this.mode.getValue() == 0 && this.slowFalling) {
                PacketUtil.sendPacketNoEvent(new C03PacketPlayer(true));
                mc.thePlayer.fallDistance = 0.0F;
            }
        }
    }
    @Override
    public void onDisabled() {
        this.waterLastPlace = 0L;
        this.waterShouldPickup = false;
        this.waterLastSlot = -1;
        this.lastOnGround = false;
        this.shouldNoFall = false;
        this.suppressMotion = false;
        this.shouldJump = false;
        this.holdingPackets = false;
        this.releaseHeldPackets();
        Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
        if (this.slowFalling) {
            this.slowFalling = false;
            AccessorMinecraft.getTimer(mc).timerSpeed = 1.0F;
        }
    }
    @Override
    public void verifyValue(String mode) {
        if (this.isEnabled()) {
            this.onDisabled();
        }
    }
    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
