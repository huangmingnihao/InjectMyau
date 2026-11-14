package myau.module.modules;

import myau.Myau;
import myau.access.AccessorRenderManager;
import myau.enums.DelayModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.AttackEvent;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.inject.Log;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.ItemUtil;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings.GameType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Backtrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final long POSITION_INTERP_MS = 80L;
    private static final double POS_EPS = 1.0E-6;
    private static final double MOVEMENT_DISTANCE_EPS = 0.001;
    private static final int WARMUP_TICKS = 20;


    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Default", "LeaderLite"});

    // ==================================================================

    // ==================================================================
    public final FloatProperty maxDelay =
            new FloatProperty("BT Delay", 200.0F, 50.0F, 500.0F, 10.0F, this::isDefaultMode);
    public final FloatProperty minDistance =
            new FloatProperty("distance-min", 0.0F, 0.0F, 3.0F, 0.1F, this::isDefaultMode);
    public final FloatProperty maxDistance =
            new FloatProperty("distance-max", 4.0F, 1.0F, 6.0F, 0.1F, this::isDefaultMode);
    public final FloatProperty cooldown =
            new FloatProperty("cooldown", 0.0F, 0.0F, 2000.0F, 50.0F, this::isDefaultMode);
    public final FloatProperty maxHurtTime =
            new FloatProperty("max-hurt-time", 500.0F, 0.0F, 500.0F, 10.0F, this::isDefaultMode);
    public final BooleanProperty disableAdventure =
            new BooleanProperty("disable-adventure", true, this::isDefaultMode);
    public final BooleanProperty flushOnDamage =
            new BooleanProperty("flush-on-damage", true, this::isDefaultMode);
    public final BooleanProperty ignoreTeammates =
            new BooleanProperty("ignore-teammates", true, this::isDefaultMode);
    public final BooleanProperty weaponOnly =
            new BooleanProperty("weapon-only", false, this::isDefaultMode);
    public final BooleanProperty showServerPosition =
            new BooleanProperty("show-server-position", true, this::isDefaultMode);
    public final ColorProperty positionColor =
            new ColorProperty("position-color", 0x0000FA,
                    () -> this.isDefaultMode() && this.showServerPosition.getValue());
    public final BooleanProperty debug =
            new BooleanProperty("debug", false, this::isDefaultMode);
    public final IntProperty positionAlpha =
            new IntProperty("position-alpha", 100, 0, 255, 5,
                    () -> this.isDefaultMode() && this.showServerPosition.getValue());

    // ==================================================================

    // ==================================================================
    public final FloatProperty llMinRange =
            new FloatProperty("Min Range", 3.0F, 1.0F, 6.0F, this::isLeaderLiteMode);
    public final FloatProperty llMaxRange =
            new FloatProperty("Max Range", 6.0F, 1.0F, 6.0F, this::isLeaderLiteMode);
    public final IntProperty llDelay =
            new IntProperty("Delay", 200, 0, 1000, this::isLeaderLiteMode);
    public final IntProperty llChance =
            new IntProperty("Chance", 100, 5, 100, this::isLeaderLiteMode);
    public final BooleanProperty llResetOnVelocity =
            new BooleanProperty("Reset On Velocity", true, this::isLeaderLiteMode);
    public final BooleanProperty llRender =
            new BooleanProperty("Render", true, this::isLeaderLiteMode);

    private final Queue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<TimedPacket>();
    private Vec3 targetPos = null;
    private Vec3 lastTargetSnapshot = null;
    private EntityPlayer target = null;
    private int currentDelay = 0;
    private long lastDeactivationTime = 0L;
    private long lastQueuedReleaseAt = 0L;
    private boolean delayingPackets = false;
    private boolean wasActive = false;
    private Vec3 positionInterpFrom = null;
    private Vec3 positionInterpTo = null;
    private long positionInterpStartMs = 0L;
    private String lastDebugLine = null;


    private static final int LL_MAX_BUFFERED_PACKETS = 512;
    private final Deque<LLDelayedMovement> llQueue = new ArrayDeque<LLDelayedMovement>();
    private EntityPlayer llTrackedPlayer;
    private EntityPlayerSP llTrackedLocalPlayer;
    private WorldClient llTrackedWorld;
    private NetHandlerPlayClient llTrackedHandler;
    private Vec3 llTruePos;
    private Vec3 llLastTruePos;
    private boolean llBacktracking;
    private Packet<?> llProcessingPacket;

    public Backtrack() {
        super("Backtrack", false);
    }

    public boolean isLeaderLiteMode() {
        return this.mode.getValue() == 1;
    }

    public boolean isDefaultMode() {
        return this.mode.getValue() == 0;
    }

    @Override
    public String[] getSuffix() {

        if (this.isLeaderLiteMode()) {
            return new String[]{this.llDelay.getValue() + "ms"};
        }
        return new String[]{
                this.currentDelay > 0 && !this.isPacketQueueEmpty()
                        ? this.currentDelay + "ms"
                        : (int) this.maxDelay.getValue().floatValue() + "ms"
        };
    }

    @Override
    public void onEnabled() {
        this.clearPendingPackets();
        this.targetPos = null;
        this.lastTargetSnapshot = null;
        this.target = null;
        this.currentDelay = 0;
        this.lastDeactivationTime = 0L;
        this.delayingPackets = false;
        this.wasActive = false;
        this.clearPositionInterp();
    }

    @Override
    public void onDisabled() {
        if (mc.isSingleplayer()) {
            this.resetWithoutProcessingPackets();
        } else {
            this.releaseAll();
        }
        this.target = null;
        this.targetPos = null;
        this.lastTargetSnapshot = null;
        this.currentDelay = 0;
        this.clearPositionInterp();
    }

    private double minDistanceValue() {
        return Math.min(this.minDistance.getValue(), this.maxDistance.getValue());
    }


    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isLeaderLiteMode()) {
            this.llOnTick(event);
            return;
        }
        if (event.getType() != EventType.PRE || !this.isEnabled()) {
            return;
        }
        if (mc.isSingleplayer()) {
            this.resetWithoutProcessingPackets();
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.releaseAll();
            return;
        }
        if (this.weaponOnly.getValue() && !ItemUtil.isHoldingSword()) {
            this.releaseAll();
            return;
        }
        if (this.disableAdventure.getValue() && isAdventure()) {
            this.releaseAll();
            return;
        }
        if (this.flushOnDamage.getValue() && mc.thePlayer.hurtTime > 0) {
            this.releaseAll();
            return;
        }
        if (this.targetPos != null && this.target != null) {
            double distance = RotationUtil.distanceToBoxAt(this.target, this.targetPos);
            if ((distance > this.maxDistance.getValue() || distance < this.minDistanceValue())
                    && this.wasActive) {
                this.releaseAll();
                this.lastDeactivationTime = System.currentTimeMillis();
                this.wasActive = false;
            }
        }
        this.processDelayedPackets();
        if (this.isPacketQueueEmpty() && this.target != null) {
            this.targetPos = this.target.getPositionVector();
        }
        this.wasActive = this.currentDelay > 0 && !this.isPacketQueueEmpty();
        if (this.debug.getValue()) {
            this.reportState();
        } else {
            this.lastDebugLine = null;
        }
    }

    private void reportState() {
        int queued = this.packetQueue.size();
        double offset = -1.0;
        if (this.target != null && this.targetPos != null) {
            offset = this.targetPos.distanceTo(this.target.getPositionVector());
        }
        String line = String.format(
                "[BT] target=%s delay=%d queue=%d delaying=%b active=%b offset=%.2f draw=%b",
                this.target == null ? "none" : this.target.getName(),
                this.currentDelay, queued, this.delayingPackets, this.wasActive, offset,
                this.target != null && this.targetPos != null && this.currentDelay > 0
                        && this.showServerPosition.getValue());
        if (!line.equals(this.lastDebugLine)) {
            this.lastDebugLine = line;
            Log.line(line);
        }
    }

    private static boolean isAdventure() {
        GameType gameType = mc.playerController.getCurrentGameType();
        return gameType != null && gameType.isAdventure();
    }


    @EventTarget
    public void onAttack(AttackEvent event) {
        if (this.isLeaderLiteMode()) {
            this.llOnAttack(event);
            return;
        }
        if (!this.isEnabled() || mc.isSingleplayer() || mc.thePlayer == null) {
            return;
        }
        if (this.weaponOnly.getValue() && !ItemUtil.isHoldingSword()) {
            return;
        }
        if (this.disableAdventure.getValue() && isAdventure()) {
            return;
        }
        if (this.cooldown.getValue() > 0.0F && this.lastDeactivationTime > 0L
                && System.currentTimeMillis() - this.lastDeactivationTime
                < this.cooldown.getValue()) {
            return;
        }
        if (!(event.getTarget() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer attacked = (EntityPlayer) event.getTarget();
        if (TeamUtil.isBot(attacked) || TeamUtil.isFriend(attacked)
                || this.ignoreTeammates.getValue() && TeamUtil.isSameTeam(attacked)) {
            return;
        }
        double distance = mc.thePlayer.getDistanceToEntity(attacked);
        if (distance > this.maxDistance.getValue() || distance < this.minDistanceValue()) {
            return;
        }
        if (this.maxHurtTime.getValue() < 500.0F
                && attacked.hurtTime * 50 > this.maxHurtTime.getValue()) {
            return;
        }


        if (!this.canHoldPackets()) {
            return;
        }
        if (this.target == null || attacked != this.target) {
            this.releaseAll();
            this.targetPos = attacked.getPositionVector();
        }
        this.target = attacked;
        this.currentDelay = (int) this.maxDelay.getValue().floatValue();
    }


    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (this.isLeaderLiteMode()) {
            if (this.isEnabled() && event.getType() == EventType.RECEIVE
                    && this.llReceiveOnMainThread(event.getPacket())) {
                event.setCancelled(true);
            }
            return;
        }
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || mc.isSingleplayer()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (mc.thePlayer.ticksExisted < WARMUP_TICKS) {
            this.clearPendingPackets();
            this.delayingPackets = false;
            return;
        }
        Packet<?> packet = event.getPacket();
        this.processDelayedPackets();

        if (this.weaponOnly.getValue() && !ItemUtil.isHoldingSword()
                || this.disableAdventure.getValue() && isAdventure()
                || this.flushOnDamage.getValue() && mc.thePlayer.hurtTime > 0
                || this.target == null) {
            this.releaseAll();
            return;
        }
        if (packet instanceof S40PacketDisconnect) {
            this.releaseAll();
            this.target = null;
            this.targetPos = null;
            return;
        }
        if (packet instanceof S13PacketDestroyEntities) {
            for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                if (this.target != null && id == this.target.getEntityId()) {
                    this.target = null;
                    this.targetPos = null;
                    this.releaseAll();
                    return;
                }
            }
        }
        if (this.currentDelay <= 0) {
            return;
        }
        if (!this.canContinueHolding()) {
            this.releaseAll();
            return;
        }

        Vec3 nextTargetSnapshot = this.readTargetPosition(packet);
        if (nextTargetSnapshot != null) {
            this.delayingPackets = this.isMovingAway(this.lastTargetSnapshot, nextTargetSnapshot);
            this.targetPos = nextTargetSnapshot;
            this.lastTargetSnapshot = nextTargetSnapshot;
        }
        if (this.shouldFlushForPacket(packet)) {
            this.flushPendingPackets();
            return;
        }
        if (this.delayingPackets) {
            this.queuePacket(packet);
            event.setCancelled(true);
            return;
        }
        if (!this.isPacketQueueEmpty()) {
            this.flushPendingPackets();
        }
    }

    private boolean canHoldPackets() {
        if (this.maxDelay.getValue().floatValue() <= 0.0F) {
            return false;
        }
        if (Myau.delayManager != null
                && (Myau.delayManager.getDelayModule() != DelayModules.NONE
                || !Myau.delayManager.delayedPacket.isEmpty())) {
            return false;
        }
        return this.canContinueHolding();
    }

    private boolean canContinueHolding() {
        if (this.maxDelay.getValue().floatValue() <= 0.0F) {
            return false;
        }
        if (Myau.moduleManager != null) {
            Stuck stuck = (Stuck) Myau.moduleManager.getModule(Stuck.class);
            if (stuck != null && stuck.isEnabled()) {
                return false;
            }
        }
        return true;
    }

    private Vec3 readTargetPosition(Packet<?> packet) {
        if (packet instanceof S14PacketEntity) {
            S14PacketEntity movePacket = (S14PacketEntity) packet;
            if (movePacket.getEntity(mc.theWorld) != this.target) {
                return null;
            }
            Vec3 position = this.targetPos != null ? this.targetPos : this.target.getPositionVector();
            return position.addVector(
                    movePacket.func_149062_c() / 32.0,
                    movePacket.func_149061_d() / 32.0,
                    movePacket.func_149064_e() / 32.0);
        }
        if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport teleportPacket = (S18PacketEntityTeleport) packet;
            if (teleportPacket.getEntityId() != this.target.getEntityId()) {
                return null;
            }
            return new Vec3(teleportPacket.getX() / 32.0, teleportPacket.getY() / 32.0,
                    teleportPacket.getZ() / 32.0);
        }
        return null;
    }

    private boolean shouldFlushForPacket(Packet<?> packet) {
        if (packet instanceof S08PacketPlayerPosLook) {
            return true;
        }
        return packet instanceof S12PacketEntityVelocity && mc.thePlayer != null
                && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId();
    }

    private boolean isMovingAway(Vec3 previousSnapshot, Vec3 newSnapshot) {
        if (previousSnapshot == null || newSnapshot == null || this.target == null) {
            return false;
        }
        double previousDistance = RotationUtil.distanceToBoxAt(this.target, previousSnapshot);
        double newDistance = RotationUtil.distanceToBoxAt(this.target, newSnapshot);
        return newDistance > previousDistance + MOVEMENT_DISTANCE_EPS;
    }


    private void processDelayedPackets() {
        synchronized (this.packetQueue) {
            long now = System.currentTimeMillis();
            while (!this.packetQueue.isEmpty()) {
                TimedPacket timed = this.packetQueue.peek();
                if (timed == null || now < timed.releaseAt) {
                    break;
                }
                this.packetQueue.poll();
                processPacket(timed.packet);
            }
            if (this.packetQueue.isEmpty()) {
                this.lastQueuedReleaseAt = 0L;
            }
        }
    }

    private void queuePacket(Packet<?> packet) {
        synchronized (this.packetQueue) {
            long releaseAt = System.currentTimeMillis() + this.currentDelay;
            if (this.lastQueuedReleaseAt > 0L && releaseAt <= this.lastQueuedReleaseAt) {
                releaseAt = this.lastQueuedReleaseAt + 1L;
            }
            this.packetQueue.add(new TimedPacket(packet, releaseAt));
            this.lastQueuedReleaseAt = releaseAt;
        }
    }

    private void releaseAll() {
        this.flushPendingPackets();
        this.currentDelay = 0;
        this.lastTargetSnapshot = null;
        this.delayingPackets = false;
        this.clearPositionInterp();
    }

    private void flushPendingPackets() {
        synchronized (this.packetQueue) {
            while (!this.packetQueue.isEmpty()) {
                TimedPacket timed = this.packetQueue.poll();
                if (timed != null) {
                    processPacket(timed.packet);
                }
            }
            this.lastQueuedReleaseAt = 0L;
        }
    }

    private void clearPendingPackets() {
        synchronized (this.packetQueue) {
            this.packetQueue.clear();
            this.lastQueuedReleaseAt = 0L;
        }
    }

    private boolean isPacketQueueEmpty() {
        synchronized (this.packetQueue) {
            return this.packetQueue.isEmpty();
        }
    }

    private void resetWithoutProcessingPackets() {
        this.clearPendingPackets();
        this.target = null;
        this.targetPos = null;
        this.lastTargetSnapshot = null;
        this.currentDelay = 0;
        this.lastDeactivationTime = 0L;
        this.delayingPackets = false;
        this.wasActive = false;
        this.clearPositionInterp();
    }

    @SuppressWarnings("unchecked")
    private static void processPacket(Packet<?> packet) {
        if (packet == null || mc.getNetHandler() == null) {
            return;
        }
        try {
            ((Packet<net.minecraft.network.play.INetHandlerPlayClient>) packet)
                    .processPacket(mc.getNetHandler());
        } catch (Throwable ignored) {
        }
    }


    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.isLeaderLiteMode()) {
            this.llRender3D(event);
            return;
        }
        if (!this.isEnabled() || mc.isSingleplayer() || !this.showServerPosition.getValue()) {
            return;
        }
        if (this.target == null || this.targetPos == null || this.target.isDead
                || this.currentDelay <= 0) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (this.positionInterpTo == null) {
            this.positionInterpFrom = this.targetPos;
            this.positionInterpTo = this.targetPos;
            this.positionInterpStartMs = nowMs;
        } else if (positionChanged(this.targetPos, this.positionInterpTo)) {
            double elapsedProgress =
                    Math.min(1.0, (double) (nowMs - this.positionInterpStartMs) / POSITION_INTERP_MS);
            this.positionInterpFrom =
                    lerpVec3(this.positionInterpFrom, this.positionInterpTo, elapsedProgress);
            this.positionInterpTo = this.targetPos;
            this.positionInterpStartMs = nowMs;
        }
        double progress =
                Math.min(1.0, (double) (nowMs - this.positionInterpStartMs) / POSITION_INTERP_MS);
        drawPlayerBoundingBox(lerpVec3(this.positionInterpFrom, this.positionInterpTo, progress),
                this.positionColor.getValue(), this.positionAlpha.getValue());
    }

    private static void drawPlayerBoundingBox(Vec3 position, int color, int alpha) {
        if (mc.thePlayer == null) {
            return;
        }
        double x = position.xCoord - AccessorRenderManager.getRenderPosX(mc.getRenderManager());
        double y = position.yCoord - AccessorRenderManager.getRenderPosY(mc.getRenderManager());
        double z = position.zCoord - AccessorRenderManager.getRenderPosZ(mc.getRenderManager());
        AxisAlignedBB playerBox = mc.thePlayer.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
        AxisAlignedBB box = new AxisAlignedBB(
                playerBox.minX - mc.thePlayer.posX + x,
                playerBox.minY - mc.thePlayer.posY + y,
                playerBox.minZ - mc.thePlayer.posZ + z,
                playerBox.maxX - mc.thePlayer.posX + x,
                playerBox.maxY - mc.thePlayer.posY + y,
                playerBox.maxZ - mc.thePlayer.posZ + z);
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;

        RenderUtil.enableRenderState();
        RenderUtil.drawBoundingBox(box, red, green, blue, alpha, 2.0F);
        GlStateManager.resetColor();
        RenderUtil.disableRenderState();
    }

    private void clearPositionInterp() {
        this.positionInterpFrom = null;
        this.positionInterpTo = null;
        this.positionInterpStartMs = 0L;
    }

    private static boolean positionChanged(Vec3 first, Vec3 second) {
        return Math.abs(first.xCoord - second.xCoord) > POS_EPS
                || Math.abs(first.yCoord - second.yCoord) > POS_EPS
                || Math.abs(first.zCoord - second.zCoord) > POS_EPS;
    }

    private static Vec3 lerpVec3(Vec3 from, Vec3 to, double progress) {
        if (progress <= 0.0) {
            return from;
        }
        if (progress >= 1.0) {
            return to;
        }
        return new Vec3(from.xCoord + (to.xCoord - from.xCoord) * progress,
                from.yCoord + (to.yCoord - from.yCoord) * progress,
                from.zCoord + (to.zCoord - from.zCoord) * progress);
    }


    public boolean isRenderingServerPositionFor(EntityLivingBase entity) {
        return !mc.isSingleplayer() && this.showServerPosition.getValue() && this.target == entity
                && this.targetPos != null && !this.target.isDead && this.currentDelay > 0;
    }

    public Vec3 getBacktrackPosition() {
        return this.target != null && this.targetPos != null && !this.target.isDead
                && this.currentDelay > 0 ? this.targetPos : null;
    }

    public EntityPlayer getBacktrackTarget() {
        return this.target != null && !this.target.isDead && this.currentDelay > 0
                ? this.target : null;
    }

    private static final class TimedPacket {
        private final Packet<?> packet;
        private final long releaseAt;

        private TimedPacket(Packet<?> packet, long releaseAt) {
            this.packet = packet;
            this.releaseAt = releaseAt;
        }
    }

    // ==================================================================

    //


    // ==================================================================

    private void llClearAll() {
        this.llQueue.clear();
        this.llTrackedPlayer = null;
        this.llTrackedLocalPlayer = null;
        this.llTrackedWorld = null;
        this.llTrackedHandler = null;
        this.llTruePos = null;
        this.llLastTruePos = null;
        this.llBacktracking = false;
    }

    private void llResetAndRelease() {
        this.llReleaseAll();
        this.llClearAll();
    }

    private boolean llIsSessionValid() {
        return this.llTrackedWorld != null
                && this.llTrackedHandler != null
                && mc.theWorld == this.llTrackedWorld
                && mc.thePlayer == this.llTrackedLocalPlayer
                && mc.getNetHandler() == this.llTrackedHandler
                && this.llTrackedHandler.getNetworkManager().isChannelOpen();
    }

    private boolean llCanReplay() {
        return this.llIsSessionValid()
                && this.llTrackedPlayer != null
                && this.llTrackedWorld.getEntityByID(this.llTrackedPlayer.getEntityId()) == this.llTrackedPlayer;
    }

    @SuppressWarnings("unchecked")
    private void llReplay(Packet<?> packet) {
        Packet<?> previous = this.llProcessingPacket;
        this.llProcessingPacket = packet;
        try {
            if (mc.getNetHandler() != null) {
                ((Packet<net.minecraft.network.play.INetHandlerPlayClient>) packet)
                        .processPacket(mc.getNetHandler());
            }
        } catch (Throwable error) {
            myau.inject.Log.swallowed(error);
        } finally {
            this.llProcessingPacket = previous;
        }
    }

    private void llReleaseAll() {
        this.llBacktracking = false;
        while (!this.llQueue.isEmpty()) {
            if (!this.llCanReplay()) {
                this.llQueue.clear();
                return;
            }
            this.llReplay(this.llQueue.removeFirst().packet);
        }
    }

    private boolean llIsTrackedValid() {
        if (!this.llCanReplay() || mc.thePlayer.isDead) return false;
        if (this.llTrackedPlayer.isDead || this.llTrackedPlayer.deathTime > 0) return false;
        return mc.thePlayer.getDistanceToEntity(this.llTrackedPlayer)
                <= this.llMaxRange.getValue() + 3.0F;
    }

    private AxisAlignedBB llBuildBox(Vec3 pos) {
        double border = this.llTrackedPlayer.getCollisionBorderSize();
        return this.llTrackedPlayer
                .getEntityBoundingBox()
                .offset(
                        pos.xCoord - this.llTrackedPlayer.posX,
                        pos.yCoord - this.llTrackedPlayer.posY,
                        pos.zCoord - this.llTrackedPlayer.posZ)
                .expand(border, border, border);
    }

    private void llCheckBacktrackRange() {
        if (!this.llBacktracking || this.llTrackedPlayer == null || this.llTruePos == null) return;
        double visibleDist = RotationUtil.distanceToBox(
                this.llBuildBox(this.llTrackedPlayer.getPositionVector()));
        double trueDist = RotationUtil.distanceToBox(this.llBuildBox(this.llTruePos));
        if (trueDist < this.llMinRange.getValue()
                || trueDist > this.llMaxRange.getValue()
                || trueDist <= visibleDist + 1.0E-6) {
            this.llReleaseAll();
        }
    }

    private boolean llCanHoldPackets() {
        if (this.llDelay.getValue() <= 0) return false;
        if (Myau.delayManager != null
                && (Myau.delayManager.getDelayModule() != DelayModules.NONE
                || !Myau.delayManager.delayedPacket.isEmpty())) {
            return false;
        }
        Stuck stuck = Myau.moduleManager == null
                ? null
                : (Stuck) Myau.moduleManager.getModule(Stuck.class);
        return stuck == null || !stuck.isEnabled();
    }


    private void llProcessQueue() {
        long now = System.nanoTime();
        long delayNanos = (long) this.llDelay.getValue() * 1_000_000L;
        while (!this.llQueue.isEmpty()) {
            if (!this.llCanReplay()) {
                this.llClearAll();
                return;
            }
            LLDelayedMovement movement = this.llQueue.peekFirst();
            if (now - movement.receivedAt < delayNanos) break;
            this.llReplay(this.llQueue.removeFirst().packet);
        }
    }

    private void llOnAttack(AttackEvent event) {
        if (!this.isEnabled()) return;
        Entity entity = event.getTarget();
        if (!(entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) entity;
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null
                || mc.thePlayer.isDead || player == mc.thePlayer || player.isDead
                || player.deathTime > 0
                || mc.theWorld.getEntityByID(player.getEntityId()) != player) return;
        if (this.llTrackedPlayer == player && this.llIsTrackedValid()) return;
        this.llResetAndRelease();
        if (Math.random() > this.llChance.getValue() / 100.0) {
            return;
        }
        this.llTrackedPlayer = player;
        this.llTrackedLocalPlayer = mc.thePlayer;
        this.llTrackedWorld = mc.theWorld;
        this.llTrackedHandler = mc.getNetHandler();
        this.llTruePos = new Vec3(
                player.serverPosX / 32.0D,
                player.serverPosY / 32.0D,
                player.serverPosZ / 32.0D);
        this.llLastTruePos = this.llTruePos;
    }

    private void llOnTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() == EventType.POST) return;
        if (!this.llIsSessionValid()) {
            this.llClearAll();
            return;
        }
        if (!this.llIsTrackedValid() || !this.llCanHoldPackets()) {
            this.llResetAndRelease();
            return;
        }
        this.llLastTruePos = this.llTruePos;
        this.llCheckBacktrackRange();
        this.llProcessQueue();
    }

    private boolean llReceiveOnMainThread(Packet<?> packet) {
        if (packet == this.llProcessingPacket) return false;
        if (packet instanceof S01PacketJoinGame
                || packet instanceof S07PacketRespawn
                || packet instanceof S40PacketDisconnect) {
            this.llClearAll();
            return false;
        }
        if (!this.isEnabled()) {
            this.llResetAndRelease();
            return false;
        }
        if (!this.llIsSessionValid()) {
            this.llClearAll();
            return false;
        }
        if (packet instanceof S08PacketPlayerPosLook) {
            this.llResetAndRelease();
            return false;
        }
        boolean localVelocity =
                packet instanceof S12PacketEntityVelocity
                        && ((S12PacketEntityVelocity) packet).getEntityID()
                        == mc.thePlayer.getEntityId();
        if (packet instanceof S27PacketExplosion) {
            S27PacketExplosion explosion = (S27PacketExplosion) packet;
            localVelocity = explosion.func_149149_c() != 0
                    || explosion.func_149144_d() != 0
                    || explosion.func_149147_e() != 0;
        }
        if (localVelocity && this.llResetOnVelocity.getValue()) {
            this.llResetAndRelease();
            return false;
        }
        if (!this.llIsTrackedValid() || !this.llCanHoldPackets()) {
            this.llResetAndRelease();
            return false;
        }
        if (packet instanceof S13PacketDestroyEntities) {
            for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                if (id == this.llTrackedPlayer.getEntityId()) {
                    this.llResetAndRelease();
                    break;
                }
            }
            return false;
        }
        Vec3 previous = this.llTruePos;
        Vec3 next;
        boolean teleport = packet instanceof S18PacketEntityTeleport;
        if (packet instanceof S14PacketEntity.S15PacketEntityRelMove
                || packet instanceof S14PacketEntity.S17PacketEntityLookMove) {
            S14PacketEntity move = (S14PacketEntity) packet;
            if (move.getEntity(this.llTrackedWorld) != this.llTrackedPlayer) return false;
            next = previous.addVector(
                    move.func_149062_c() / 32.0D,
                    move.func_149061_d() / 32.0D,
                    move.func_149064_e() / 32.0D);
        } else if (teleport) {
            S18PacketEntityTeleport move = (S18PacketEntityTeleport) packet;
            if (move.getEntityId() != this.llTrackedPlayer.getEntityId()) return false;
            next = new Vec3(move.getX() / 32.0D, move.getY() / 32.0D, move.getZ() / 32.0D);
        } else {
            return false;
        }
        this.llProcessQueue();
        if (!this.llIsTrackedValid()) {
            this.llClearAll();
            return false;
        }
        this.llTruePos = next;
        double oldDistance = RotationUtil.distanceToBox(this.llBuildBox(previous));
        double newDistance = RotationUtil.distanceToBox(this.llBuildBox(next));
        boolean movingAway = newDistance > oldDistance + 1.0E-6;
        boolean inRange = newDistance >= this.llMinRange.getValue()
                && newDistance <= this.llMaxRange.getValue();
        if (movingAway
                && inRange
                && (!teleport || previous.squareDistanceTo(next) < 64.0)
                && this.llQueue.size() < LL_MAX_BUFFERED_PACKETS) {
            this.llBacktracking = true;
            this.llQueue.addLast(new LLDelayedMovement(packet, System.nanoTime()));
            return true;
        }
        this.llReleaseAll();
        return false;
    }

    private void llRender3D(Render3DEvent event) {
        if (!this.isEnabled() || !this.llRender.getValue()) return;
        if (!this.llCanReplay() || !this.llBacktracking || this.llTruePos == null) return;

        float partialTicks = event.getPartialTicks();
        Vec3 from = this.llLastTruePos != null ? this.llLastTruePos : this.llTruePos;
        Vec3 smoothed = new Vec3(
                from.xCoord + (this.llTruePos.xCoord - from.xCoord) * partialTicks,
                from.yCoord + (this.llTruePos.yCoord - from.yCoord) * partialTicks,
                from.zCoord + (this.llTruePos.zCoord - from.zCoord) * partialTicks);

        AxisAlignedBB box = this.llBuildBox(smoothed)
                .offset(
                        -mc.getRenderManager().viewerPosX,
                        -mc.getRenderManager().viewerPosY,
                        -mc.getRenderManager().viewerPosZ);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.tryBlendFuncSeparate(
                org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        RenderUtil.drawFilledBox(box, 255, 255, 255);
        RenderUtil.drawBoundingBox(box, 255, 255, 255, 204, 1.5F);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static final class LLDelayedMovement {
        private final Packet<?> packet;
        private final long receivedAt;

        private LLDelayedMovement(Packet<?> packet, long receivedAt) {
            this.packet = packet;
            this.receivedAt = receivedAt;
        }
    }
}
