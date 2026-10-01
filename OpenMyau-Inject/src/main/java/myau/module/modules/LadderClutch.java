package myau.module.modules;

import myau.access.AccessorKeyBinding;
import myau.access.AccessorPlayerControllerMP;
import myau.access.AccessorRenderManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.PlayerUpdateEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.KeyBindUtil;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public class LadderClutch extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int STAGE_IDLE = 0;
    private static final int STAGE_BLOCK = 1;
    private static final int STAGE_SWAP = 2;
    private static final int STAGE_LADDER = 3;
    private static final double PLACE_REACH = 4.5;
    private static final double HIGHLIGHT_REACH = 32.0;
    private static final long PLACE_PACKET_COOLDOWN = 90L;
    private static final long ROTATION_RETURN_TIME = 260L;
    private static final long BLOCK_ROTATION_HOLD = 240L;
    private static final long LADDER_ROTATION_HOLD = 300L;
    private static final long LADDER_STAGE_TIMEOUT = 500L;
    private static final long DUPLICATE_WINDOW = 250L;
    private static final double FALLING_MOTION = -0.0784;
    private static final int ROTATION_PRIORITY = 6;

    public final FloatProperty minFallDistance = new FloatProperty("min-fall-distance", 3.0F, 0.0F, 10.0F);
    public final IntProperty activationDelay = new IntProperty("activation-delay", 0, 0, 400);
    public final IntProperty minPitch = new IntProperty("min-pitch", 45, 0, 90);
    public final BooleanProperty rotate = new BooleanProperty("rotate", true);
    public final BooleanProperty highlight = new BooleanProperty("highlight-blocks", true);
    public final BooleanProperty positionIndicator = new BooleanProperty("position-indicator", false);

    private int stage = STAGE_IDLE;
    private int ladderSlot = -1;
    private int blockSlot = -1;
    private long stageStartedAt = 0L;
    private long activationArmedAt = 0L;
    private boolean blockSwapPending = false;
    private boolean ladderSwapPending = false;
    private boolean finishAfterLadderSwap = false;
    private boolean placedThisCycle = false;
    private boolean scriptPlacing = false;
    private BlockPos placedBlock = null;
    private EnumFacing ladderSide = null;
    private boolean fallTracking = false;
    private boolean fallEligible = false;
    private boolean fallConsumed = false;
    private float currentFallDistance = 0.0F;
    private boolean placeQueued = false;
    private BlockPos queuedBlock = null;
    private EnumFacing queuedSide = null;
    private Vec3 queuedHit = null;
    private int queuedStage = STAGE_IDLE;
    private BlockPos lastPlacementBlock = null;
    private EnumFacing lastPlacementSide = null;
    private long lastPlacementAt = 0L;
    private long lastPlacePacketAt = 0L;

    private float rotateYaw = 0.0F;
    private float rotatePitch = 0.0F;
    private float rotationBaseYaw = 0.0F;
    private boolean hasRotationBase = false;
    private boolean rotationActive = false;
    private boolean rotationReturning = false;
    private long rotationUntil = 0L;
    private long rotationReturnStartedAt = 0L;
    private long rotationReturnUntil = 0L;
    private float returnStartYaw = 0.0F;
    private float returnStartPitch = 0.0F;
    public LadderClutch() {
        super("Ladder Clutch", false);
    }
    @Override
    public void onDisabled() {
        this.resetState(false);
        this.clearFallTracking();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }
        this.updateFallTracking();
        this.runStages();
        float[] rotations = this.computeRotations(event.getNewYaw(), event.getNewPitch());
        if (rotations != null) {
            event.setRotation(rotations[0], rotations[1], ROTATION_PRIORITY);
        }
    }
    private void runStages() {

        if (this.shouldSuppressUse()) {
            this.suppressUse();
        }
        if (this.blockSwapPending) {
            if (!this.canCommitBlockSwap()) {
                this.cancelPendingBlockSwap();
            }
            return;
        }
        if (this.placeQueued) {
            if (this.rotate.getValue() && !this.rotationStillHeld()) {
                this.clearQueuedPlace();
            } else if (this.processQueuedPlace() || this.placeQueued) {
                return;
            }
        }
        switch (this.stage) {
            case STAGE_BLOCK:
                this.maintainBlockStage();
                return;
            case STAGE_SWAP:
                if (!this.ladderSwapPending) {
                    this.resetState(true);
                }
                return;
            case STAGE_LADDER:
                this.maintainLadderStage();
                return;
            default:
                break;
        }
        this.tryArm();
    }

    private void tryArm() {
        if (!this.shouldStart()) {
            this.activationArmedAt = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (this.activationArmedAt == 0L) {
            this.activationArmedAt = now;
        }
        if (this.activationDelay.getValue() > 0
                && now - this.activationArmedAt < this.activationDelay.getValue()) {
            return;
        }
        int currentSlot = mc.thePlayer.inventory.currentItem;
        int swapSlot = this.findBlockSlot(currentSlot);
        if (swapSlot == -1) {
            return;
        }
        this.ladderSlot = currentSlot;
        this.blockSlot = swapSlot;
        this.activationArmedAt = 0L;
        this.placedThisCycle = false;
        this.fallConsumed = true;
        this.blockSwapPending = true;
    }
    private void maintainBlockStage() {
        if (mc.thePlayer == null || !this.isValidLadderSlot(this.ladderSlot)) {
            this.resetState(true);
            return;
        }
        if (mc.thePlayer.onGround || !this.holdingUse()) {
            this.queueLadderSwap(true);
            return;
        }
        if (!this.holdSlot(this.blockSlot)) {
            return;
        }
        if (!this.rotate.getValue()) {
            return;
        }
        if (this.rotationStillHeld() && this.tryQueueBlockAt(this.rotateYaw, this.rotatePitch)) {
            this.processQueuedPlace();
            return;
        }
        this.updateBlockRotationQueue();
    }
    private void maintainLadderStage() {
        if (mc.thePlayer == null || !this.holdingUse() || !this.isValidLadderSlot(this.ladderSlot)) {
            this.resetState(true);
            return;
        }
        if (System.currentTimeMillis() - this.stageStartedAt > LADDER_STAGE_TIMEOUT) {
            this.resetState(true);
            return;
        }
        if (!this.holdSlot(this.ladderSlot)) {
            return;
        }
        if (this.rotationStillHeld() && this.tryQueueLadderAt(this.rotateYaw, this.rotatePitch)) {
            this.processQueuedPlace();
            return;
        }
        this.updateLadderRotationQueue();
    }
    @EventTarget
    public void onPostMotion(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST || mc.thePlayer == null) {
            return;
        }
        if (this.blockSwapPending) {
            this.commitBlockSwap();
            return;
        }
        if (this.ladderSwapPending) {
            this.commitLadderSwap();
        }
        if (this.stage == STAGE_LADDER && this.placeQueued && this.queuedStage == STAGE_LADDER) {
            this.processQueuedPlace();
        }
    }
    @EventTarget
    public void onPreMotion(PlayerUpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (this.placeQueued && this.rotate.getValue() && this.rotationStillHeld()) {
            this.processQueuedPlace();
        }
    }
    private boolean canCommitBlockSwap() {
        if (mc.thePlayer == null || mc.thePlayer.onGround) {
            return false;
        }
        if (mc.currentScreen != null || !this.holdingUse()) {
            return false;
        }
        if (mc.thePlayer.inventory.currentItem != this.ladderSlot
                || !this.isValidLadderSlot(this.ladderSlot)) {
            return false;
        }
        if (!this.isHotbarSlot(this.blockSlot)
                || !isPlaceableBlock(mc.thePlayer.inventory.getStackInSlot(this.blockSlot))) {
            return false;
        }
        return this.isCrosshairOnTopFace() && mc.thePlayer.motionY < -0.01;
    }
    private void commitBlockSwap() {
        if (!this.canCommitBlockSwap()) {
            this.cancelPendingBlockSwap();
            return;
        }
        this.blockSwapPending = false;
        this.stage = STAGE_BLOCK;
        this.stageStartedAt = System.currentTimeMillis();
        this.switchSlot(this.blockSlot);
        if (mc.thePlayer.inventory.currentItem != this.blockSlot) {
            this.cancelPendingBlockSwap();
        }
    }
    private void cancelPendingBlockSwap() {
        this.blockSwapPending = false;
        this.fallConsumed = false;
        this.resetState(false);
    }
    private void queueLadderSwap(boolean finish) {
        this.blockSwapPending = false;
        this.ladderSwapPending = true;
        this.finishAfterLadderSwap = finish;
        this.stage = STAGE_SWAP;
        this.stageStartedAt = System.currentTimeMillis();
    }
    private void commitLadderSwap() {
        if (!this.isValidLadderSlot(this.ladderSlot)) {
            this.resetState(true);
            return;
        }
        if (!this.holdSlot(this.ladderSlot)) {
            return;
        }
        this.ladderSwapPending = false;
        boolean finish = this.finishAfterLadderSwap;
        this.finishAfterLadderSwap = false;
        if (finish || this.placedBlock == null || !this.holdingUse()) {
            this.resetState(true);
            return;
        }
        this.stage = STAGE_LADDER;
        this.stageStartedAt = System.currentTimeMillis();
    }
    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) {
            return;
        }
        if (!(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            return;
        }
        C08PacketPlayerBlockPlacement placement = (C08PacketPlayerBlockPlacement) event.getPacket();
        long now = System.currentTimeMillis();
        if (placement.getPlacedBlockDirection() == 255) {
            if (this.blockSwapPending || this.ladderSwapPending || this.stage != STAGE_IDLE) {
                event.setCancelled(true);
            }
            return;
        }
        if (this.stage == STAGE_SWAP || this.ladderSwapPending) {
            event.setCancelled(true);
            return;
        }
        if (this.isDuplicatePlacement(placement, now)) {
            event.setCancelled(true);
            return;
        }
        EnumFacing side = facingFrom(placement.getPlacedBlockDirection());
        BlockPos position = placement.getPosition();
        if (this.stage == STAGE_IDLE) {
            if (now - this.lastPlacePacketAt < PLACE_PACKET_COOLDOWN) {
                event.setCancelled(true);
            }
            return;
        }
        if (this.stage == STAGE_BLOCK) {
            if (side != EnumFacing.UP) {
                event.setCancelled(true);
                return;
            }
            if (this.shouldSuppressUse() && !this.scriptPlacing) {
                event.setCancelled(true);
                return;
            }
            if (this.placedThisCycle) {
                event.setCancelled(true);
                return;
            }
            if (position == null) {
                return;
            }
            this.placedThisCycle = true;
            this.placedBlock = position.offset(side);
            this.lastPlacePacketAt = now;
            this.rememberPlacement(position, side, now);
            this.queueLadderSwap(false);
            return;
        }
        if (this.stage == STAGE_LADDER) {
            if (this.shouldSuppressUse() && !this.scriptPlacing) {
                event.setCancelled(true);
                return;
            }
            if (this.shouldSuppressUse() && !this.isValidLadderPlacement(position, side)) {
                event.setCancelled(true);
                return;
            }
            this.lastPlacePacketAt = now;
            this.rememberPlacement(position, side, now);
            this.resetState(true);
        }
    }
    private boolean isDuplicatePlacement(C08PacketPlayerBlockPlacement placement, long now) {
        if (this.lastPlacementBlock == null || placement.getPosition() == null) {
            return false;
        }
        if (now - this.lastPlacementAt > DUPLICATE_WINDOW) {
            return false;
        }
        return facingFrom(placement.getPlacedBlockDirection()) == this.lastPlacementSide
                && this.lastPlacementBlock.equals(placement.getPosition());
    }
    private void rememberPlacement(BlockPos position, EnumFacing side, long now) {
        if (position == null) {
            return;
        }
        this.lastPlacementBlock = position;
        this.lastPlacementSide = side;
        this.lastPlacementAt = now;
    }

    private boolean isValidLadderPlacement(BlockPos position, EnumFacing side) {
        if (!this.rotate.getValue()) {
            return true;
        }
        if (mc.thePlayer == null || !isLadder(mc.thePlayer.getHeldItem())) {
            return false;
        }
        if (side == null || side.getAxis() == EnumFacing.Axis.Y) {
            return false;
        }
        if (this.placedBlock != null && !this.placedBlock.equals(position)) {
            return false;
        }
        return this.ladderSide == null || this.ladderSide == side;
    }
    private void updateBlockRotationQueue() {
        if (this.stage != STAGE_BLOCK || this.placedThisCycle || this.placeQueued) {
            return;
        }
        if (mc.thePlayer.inventory.currentItem != this.blockSlot
                || !isPlaceableBlock(mc.thePlayer.getHeldItem()) || !this.holdingUse()) {
            return;
        }
        MovingObjectPosition crosshair = this.crosshairTopFace(HIGHLIGHT_REACH);
        if (crosshair == null) {
            return;
        }
        float[] rotations = this.rotationsTo(crosshair.hitVec);
        if (rotations == null) {
            return;
        }
        float yaw = this.nearestContinuousYaw(rotations[0]);
        this.applyRotation(yaw, rotations[1], BLOCK_ROTATION_HOLD);
        this.tryQueueBlockAt(yaw, rotations[1]);
    }

    private boolean tryQueueBlockAt(float yaw, float pitch) {
        if (this.stage != STAGE_BLOCK || this.placedThisCycle || this.placeQueued) {
            return false;
        }
        if (mc.thePlayer.inventory.currentItem != this.blockSlot
                || !isPlaceableBlock(mc.thePlayer.getHeldItem()) || !this.holdingUse()) {
            return false;
        }
        MovingObjectPosition crosshair = this.crosshairTopFace(HIGHLIGHT_REACH);
        if (crosshair == null) {
            return false;
        }
        MovingObjectPosition ray = this.rayTrace(yaw, pitch, PLACE_REACH);
        if (ray == null || ray.sideHit != EnumFacing.UP
                || !ray.getBlockPos().equals(crosshair.getBlockPos())) {
            return false;
        }
        return this.queuePlacement(ray.getBlockPos(), ray.sideHit, ray.hitVec);
    }
    private void updateLadderRotationQueue() {
        if (this.placeQueued || this.placedBlock == null || !this.rotate.getValue()) {
            return;
        }
        if (mc.thePlayer.inventory.currentItem != this.ladderSlot
                || !isLadder(mc.thePlayer.getHeldItem())) {
            return;
        }
        EnumFacing preferred = this.closestSide(this.placedBlock);
        if (this.tryLadderSide(preferred)) {
            return;
        }
        for (EnumFacing side : HORIZONTAL) {
            if (side != preferred && this.tryLadderSide(side)) {
                return;
            }
        }
    }
    private boolean tryLadderSide(EnumFacing side) {
        return this.tryLadderFloorEdge(side) || this.tryLadderSideFace(side);
    }
    private boolean tryLadderFloorEdge(EnumFacing side) {
        Vec3 target = this.floorEdgeTarget(this.placedBlock, side);
        if (target == null) {
            return false;
        }
        float[] rotations = this.rotationsTo(target);
        if (rotations == null) {
            return false;
        }
        float yaw = this.currentRotationYaw();
        return this.commitLadderAim(side, yaw, rotations[1]);
    }

    private boolean tryLadderSideFace(EnumFacing side) {
        Vec3 target = faceCentre(this.placedBlock, side);
        float[] rotations = this.rotationsTo(target);
        if (rotations == null) {
            return false;
        }
        float yaw = this.nearestContinuousYaw(rotations[0]);
        if (Math.abs(yaw - this.currentRotationYaw()) > 90.0F) {
            return false;
        }
        return this.commitLadderAim(side, yaw, rotations[1]);
    }
    private boolean commitLadderAim(EnumFacing side, float yaw, float pitch) {
        MovingObjectPosition ray = this.rayTrace(yaw, pitch, PLACE_REACH);
        if (ray == null || ray.getBlockPos() == null || ray.sideHit != side) {
            return false;
        }
        if (side.getAxis() == EnumFacing.Axis.Y || !ray.getBlockPos().equals(this.placedBlock)) {
            return false;
        }
        this.ladderSide = side;
        if (!this.queuePlacement(ray.getBlockPos(), side, ray.hitVec)) {
            return false;
        }
        this.applyRotation(yaw, pitch, LADDER_ROTATION_HOLD);
        return true;
    }
    private boolean tryQueueLadderAt(float yaw, float pitch) {
        if (this.stage != STAGE_LADDER || this.placedBlock == null || this.placeQueued) {
            return false;
        }
        if (mc.thePlayer.inventory.currentItem != this.ladderSlot
                || !isLadder(mc.thePlayer.getHeldItem()) || !this.holdingUse()) {
            return false;
        }
        MovingObjectPosition ray = this.rayTrace(yaw, pitch, PLACE_REACH);
        if (ray == null || ray.getBlockPos() == null || !ray.getBlockPos().equals(this.placedBlock)) {
            return false;
        }
        if (ray.sideHit == null || ray.sideHit.getAxis() == EnumFacing.Axis.Y) {
            return false;
        }
        if (this.ladderSide != null && this.ladderSide != ray.sideHit) {
            return false;
        }
        this.ladderSide = ray.sideHit;
        return this.queuePlacement(ray.getBlockPos(), ray.sideHit, ray.hitVec);
    }
    private boolean queuePlacement(BlockPos block, EnumFacing side, Vec3 hit) {
        if (this.placeQueued) {
            return true;
        }
        if (block == null || side == null || hit == null || mc.thePlayer.getHeldItem() == null) {
            return false;
        }
        this.queuedBlock = block;
        this.queuedSide = side;
        this.queuedHit = hit;
        this.queuedStage = this.stage;
        this.placeQueued = true;
        return true;
    }
    private boolean processQueuedPlace() {
        if (!this.placeQueued) {
            return false;
        }
        if (this.queuedBlock == null || this.queuedSide == null || this.queuedHit == null) {
            this.clearQueuedPlace();
            return false;
        }
        if (this.queuedStage != this.stage) {
            this.clearQueuedPlace();
            return false;
        }
        if (!this.canPlaceQueued()) {
            return false;
        }
        BlockPos block = this.queuedBlock;
        EnumFacing side = this.queuedSide;
        Vec3 hit = this.queuedHit;
        this.clearQueuedPlace();
        boolean placed;
        this.scriptPlacing = true;
        try {
            placed = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                    mc.thePlayer.getHeldItem(), block, side, hit);
        } finally {
            this.scriptPlacing = false;
        }
        if (placed) {
            mc.thePlayer.swingItem();
        }
        return placed;
    }
    private boolean canPlaceQueued() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) {
            return false;
        }
        if (this.queuedStage == STAGE_BLOCK) {
            return mc.thePlayer.inventory.currentItem == this.blockSlot && isPlaceableBlock(held);
        }
        if (this.queuedStage == STAGE_LADDER) {
            return mc.thePlayer.inventory.currentItem == this.ladderSlot && isLadder(held);
        }
        return false;
    }
    private void clearQueuedPlace() {
        this.placeQueued = false;
        this.queuedBlock = null;
        this.queuedSide = null;
        this.queuedHit = null;
        this.queuedStage = STAGE_IDLE;
    }
    private float[] computeRotations(float lastYaw, float lastPitch) {
        this.rotationBaseYaw = lastYaw;
        this.hasRotationBase = true;
        long now = System.currentTimeMillis();
        if (this.rotationActive && now <= this.rotationUntil) {
            return new float[]{this.rotateYaw, this.rotatePitch};
        }
        if (this.rotationActive && !this.rotationReturning) {
            this.beginRotationReturn(now);
        }
        if (!this.rotationReturning) {
            return null;
        }
        if (now >= this.rotationReturnUntil) {
            this.clearRotationState();
            return null;
        }

        float targetYaw = nearestYawTo(this.returnStartYaw, lastYaw);
        double progress = (double) (now - this.rotationReturnStartedAt)
                / (double) (this.rotationReturnUntil - this.rotationReturnStartedAt);
        progress = smoothStep(progress);

        this.rotateYaw = (float) (this.returnStartYaw + (targetYaw - this.returnStartYaw) * progress);
        this.rotatePitch = (float) (this.returnStartPitch + (lastPitch - this.returnStartPitch) * progress);
        return new float[]{this.rotateYaw, this.rotatePitch};
    }
    private void applyRotation(float yaw, float pitch, long holdMs) {
        this.rotateYaw = this.nearestContinuousYaw(yaw);
        this.rotatePitch = pitch;
        this.rotationActive = true;
        this.rotationReturning = false;
        this.rotationUntil = System.currentTimeMillis() + holdMs;
        if (this.holdingUse()) {
            this.suppressUse();
        }
    }

    private void beginRotationReturn(long now) {
        this.returnStartYaw = this.rotateYaw;
        this.returnStartPitch = this.rotatePitch;
        this.rotationReturnStartedAt = now;
        this.rotationReturnUntil = now + ROTATION_RETURN_TIME;
        this.rotationActive = false;
        this.rotationReturning = true;
    }
    private void clearRotationState() {
        this.rotationActive = false;
        this.rotationReturning = false;
        this.hasRotationBase = false;
        this.rotationUntil = 0L;
        this.rotationReturnStartedAt = 0L;
        this.rotationReturnUntil = 0L;
    }
    private boolean rotationStillHeld() {
        return this.rotationActive && System.currentTimeMillis() <= this.rotationUntil;
    }

    private boolean shouldSuppressUse() {
        return this.rotate.getValue() && this.rotationStillHeld();
    }
    private void suppressUse() {
        AccessorKeyBinding.setPressed(mc.gameSettings.keyBindUseItem, false);
    }
    private float currentRotationYaw() {
        return this.hasRotationBase ? this.rotationBaseYaw : this.rotateYaw;
    }

    private float nearestContinuousYaw(float yaw) {
        return nearestYawTo(this.currentRotationYaw(), yaw);
    }
    private static float nearestYawTo(float base, float yaw) {
        while (yaw - base > 180.0F) {
            yaw -= 360.0F;
        }
        while (yaw - base < -180.0F) {
            yaw += 360.0F;
        }
        return yaw;
    }

    private static double smoothStep(double value) {
        if (value <= 0.0) {
            return 0.0;
        }
        if (value >= 1.0) {
            return 1.0;
        }
        return value * value * (3.0 - 2.0 * value);
    }

    private float[] rotationsTo(Vec3 target) {
        if (target == null || mc.thePlayer == null) {
            return null;
        }
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double dx = target.xCoord - eyes.xCoord;
        double dy = target.yCoord - eyes.yCoord;
        double dz = target.zCoord - eyes.zCoord;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        return new float[]{yaw, pitch};
    }
    private static Vec3 faceCentre(BlockPos block, EnumFacing side) {
        return new Vec3(block.getX() + 0.5 + side.getFrontOffsetX() * 0.5,
                block.getY() + 0.5 + side.getFrontOffsetY() * 0.5,
                block.getZ() + 0.5 + side.getFrontOffsetZ() * 0.5);
    }
    private static final EnumFacing[] HORIZONTAL =
            {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};
    private boolean shouldStart() {
        if (mc.currentScreen != null || !this.holdingUse() || mc.thePlayer == null) {
            return false;
        }
        if (!isLadder(mc.thePlayer.getHeldItem())) {
            return false;
        }
        if (!this.fallTracking || !this.fallEligible || this.fallConsumed) {
            return false;
        }
        if (this.minPitch.getValue() > 0 && mc.thePlayer.rotationPitch < this.minPitch.getValue()) {
            return false;
        }
        return this.isCrosshairOnTopFace();
    }
    private boolean isCrosshairOnTopFace() {
        return this.crosshairTopFace(HIGHLIGHT_REACH) != null;
    }

    private MovingObjectPosition crosshairTopFace(double reach) {
        MovingObjectPosition hit =
                this.rayTrace(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, reach);
        return hit != null && hit.sideHit == EnumFacing.UP ? hit : null;
    }
    private MovingObjectPosition rayTrace(float yaw, float pitch, double reach) {
        MovingObjectPosition hit = RotationUtil.rayTrace(yaw, pitch, reach, 1.0F);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || hit.getBlockPos() == null || hit.sideHit == null) {
            return null;
        }
        return hit;
    }
    private boolean holdingUse() {
        return KeyBindUtil.isKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode());
    }
    private boolean holdSlot(int slot) {
        if (mc.thePlayer.inventory.currentItem == slot) {
            return true;
        }
        this.switchSlot(slot);
        return mc.thePlayer.inventory.currentItem == slot;
    }

    private void switchSlot(int slot) {
        if (!this.isHotbarSlot(slot)) {
            return;
        }
        mc.thePlayer.inventory.currentItem = slot;
        AccessorPlayerControllerMP.callSyncCurrentPlayItem(mc.playerController);
    }
    private boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot <= 8;
    }
    private int findBlockSlot(int exceptSlot) {
        for (int slot = 0; slot <= 8; slot++) {
            if (slot != exceptSlot && isPlaceableBlock(mc.thePlayer.inventory.getStackInSlot(slot))) {
                return slot;
            }
        }
        return -1;
    }
    private boolean isValidLadderSlot(int slot) {
        return this.isHotbarSlot(slot) && isLadder(mc.thePlayer.inventory.getStackInSlot(slot));
    }
    private static boolean isLadder(ItemStack stack) {
        return stack != null && stack.stackSize > 0
                && stack.getItem() == Item.getItemFromBlock(Blocks.ladder);
    }
    private static boolean isPlaceableBlock(ItemStack stack) {
        return stack != null && stack.stackSize > 0
                && stack.getItem() instanceof ItemBlock && !isLadder(stack);
    }
    private EnumFacing closestSide(BlockPos block) {
        double dx = mc.thePlayer.posX - (block.getX() + 0.5);
        double dz = mc.thePlayer.posZ - (block.getZ() + 0.5);
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0.0 ? EnumFacing.EAST : EnumFacing.WEST;
        }
        return dz >= 0.0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }
    private Vec3 floorEdgeTarget(BlockPos block, EnumFacing side) {
        if (block == null || side == null) {
            return null;
        }
        switch (side) {
            case NORTH:
                return new Vec3(block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.08);
            case SOUTH:
                return new Vec3(block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.92);
            case WEST:
                return new Vec3(block.getX() + 0.08, block.getY() + 1.0, block.getZ() + 0.5);
            case EAST:
                return new Vec3(block.getX() + 0.92, block.getY() + 1.0, block.getZ() + 0.5);
            default:
                return null;
        }
    }
    private static EnumFacing facingFrom(int direction) {
        return direction >= 0 && direction < EnumFacing.values().length
                ? EnumFacing.getFront(direction) : null;
    }

    private void updateFallTracking() {
        if (mc.thePlayer.onGround || mc.thePlayer.motionY > FALLING_MOTION
                || mc.thePlayer.capabilities.isCreativeMode || mc.thePlayer.capabilities.isFlying) {
            this.clearFallTracking();
            return;
        }
        if (!this.fallTracking) {
            this.fallTracking = true;
            this.fallConsumed = false;
        }
        if (!this.fallConsumed) {
            this.currentFallDistance = mc.thePlayer.fallDistance;
            this.fallEligible = this.minFallDistance.getValue() <= 0.0F
                    || this.currentFallDistance >= this.minFallDistance.getValue();
        }
    }
    private void clearFallTracking() {
        this.fallTracking = false;
        this.fallEligible = false;
        this.fallConsumed = false;
        this.currentFallDistance = 0.0F;
        this.activationArmedAt = 0L;
    }
    private void resetState(boolean releaseRotation) {
        boolean walkBack = releaseRotation && this.rotate.getValue()
                && (this.rotationActive || this.rotationReturning);
        this.ladderSlot = -1;
        this.blockSlot = -1;
        this.stage = STAGE_IDLE;
        this.stageStartedAt = 0L;
        this.activationArmedAt = 0L;
        this.placedThisCycle = false;
        this.scriptPlacing = false;
        this.blockSwapPending = false;
        this.ladderSwapPending = false;
        this.finishAfterLadderSwap = false;
        this.clearQueuedPlace();
        this.placedBlock = null;
        this.ladderSide = null;
        if (walkBack) {
            this.beginRotationReturn(System.currentTimeMillis());
        } else {
            this.clearRotationState();
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (!this.highlight.getValue() && !this.positionIndicator.getValue()) {
            return;
        }
        if (this.stage != STAGE_BLOCK || this.placedThisCycle) {
            return;
        }
        if (mc.thePlayer.inventory.currentItem != this.blockSlot
                || !isPlaceableBlock(mc.thePlayer.getHeldItem())) {
            return;
        }
        MovingObjectPosition crosshair = this.crosshairTopFace(HIGHLIGHT_REACH);
        if (crosshair == null) {
            return;
        }
        BlockPos wool = crosshair.getBlockPos().up();
        if (this.highlight.getValue()) {
            drawBox(wool, this.previewColor(), 58);
            EnumFacing side = this.closestSide(wool);
            BlockPos floor = wool.offset(side).down();
            drawBox(floor, 0xFF9900, 42);
        }
        if (this.positionIndicator.getValue()) {
            this.drawPositionIndicator(wool, event.getPartialTicks());
        }
    }
    private void drawPositionIndicator(BlockPos wool, float partialTicks) {
        double predict = Math.max(0.0, Math.min(1.35, partialTicks + 0.72F));
        double x = mc.thePlayer.posX + mc.thePlayer.motionX * predict;
        double z = mc.thePlayer.posZ + mc.thePlayer.motionZ * predict;
        EnumFacing side = this.closestSideAt(wool, x, z);
        BlockPos floor = wool.offset(side).down();
        double radius = Math.max(0.26, Math.min(0.42, mc.thePlayer.width / 2.0));
        boolean ready = this.inCatchZone(x, z, floor, side, radius);
        drawGroundCircle(x, floor.getY() + 1.028, z, radius * 0.56,
                ready ? 0x39FF14 : 0xFF0038, ready ? 7 : 5);
    }

    private EnumFacing closestSideAt(BlockPos block, double x, double z) {
        double dx = x - (block.getX() + 0.5);
        double dz = z - (block.getZ() + 0.5);
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0.0 ? EnumFacing.EAST : EnumFacing.WEST;
        }
        return dz >= 0.0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }
    private boolean inCatchZone(double x, double z, BlockPos floor, EnumFacing side, double radius) {
        double minX = x - radius;
        double maxX = x + radius;
        double minZ = z - radius;
        double maxZ = z + radius;
        double blockMinX = floor.getX();
        double blockMaxX = floor.getX() + 1.0;
        double blockMinZ = floor.getZ();
        double blockMaxZ = floor.getZ() + 1.0;
        double overlapX = Math.min(maxX, blockMaxX) - Math.max(minX, blockMinX);
        double overlapZ = Math.min(maxZ, blockMaxZ) - Math.max(minZ, blockMinZ);
        if (overlapX <= 0.055 || overlapZ <= 0.055) {
            return false;
        }
        double faceReach = radius + 0.18;
        switch (side) {
            case NORTH:
                return maxZ >= blockMaxZ - faceReach && minZ <= blockMaxZ + 0.10 && overlapX >= 0.16;
            case SOUTH:
                return minZ <= blockMinZ + faceReach && maxZ >= blockMinZ - 0.10 && overlapX >= 0.16;
            case WEST:
                return maxX >= blockMaxX - faceReach && minX <= blockMaxX + 0.10 && overlapZ >= 0.16;
            case EAST:
                return minX <= blockMinX + faceReach && maxX >= blockMinX - 0.10 && overlapZ >= 0.16;
            default:
                return false;
        }
    }
    private int previewColor() {
        ItemStack stack = this.isHotbarSlot(this.blockSlot)
                ? mc.thePlayer.inventory.getStackInSlot(this.blockSlot) : null;
        if (!isPlaceableBlock(stack)) {
            return 0xFFFFFF;
        }
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        if (block != Blocks.wool) {
            return 0xFFFFFF;
        }
        return WOOL_RGB[stack.getMetadata() & 15];
    }
    private static final int[] WOOL_RGB = {
            0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21
    };
    private static void drawBox(BlockPos pos, int rgb, int alpha) {
        double x1 = pos.getX() - AccessorRenderManager.getRenderPosX(mc.getRenderManager());
        double y1 = pos.getY() - AccessorRenderManager.getRenderPosY(mc.getRenderManager());
        double z1 = pos.getZ() - AccessorRenderManager.getRenderPosZ(mc.getRenderManager());
        double x2 = x1 + 1.0;
        double y2 = y1 + 1.0;
        double z2 = z1 + 1.0;
        RenderUtil.enableRenderState();
        GlStateManager.depthMask(false);
        GL11.glColor4f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F, alpha / 255.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x1, y2, z2); GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x2, y2, z1); GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x1, y2, z2); GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y2, z1); GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x2, y1, z2);
        GL11.glEnd();
        GlStateManager.depthMask(true);
        RenderUtil.disableRenderState();
        GlStateManager.resetColor();
    }

    private static void drawGroundCircle(double x, double y, double z, double radius,
                                         int rgb, int fillAlpha) {
        double ox = x - AccessorRenderManager.getRenderPosX(mc.getRenderManager());
        double oy = y - AccessorRenderManager.getRenderPosY(mc.getRenderManager());
        double oz = z - AccessorRenderManager.getRenderPosZ(mc.getRenderManager());
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        int points = 72;
        RenderUtil.enableRenderState();
        GlStateManager.depthMask(false);
        GL11.glColor4f(red, green, blue, fillAlpha / 255.0F);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(ox, oy, oz);
        for (int i = 0; i <= points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            GL11.glVertex3d(ox + Math.cos(angle) * radius, oy, oz + Math.sin(angle) * radius);
        }
        GL11.glEnd();

        GL11.glLineWidth(2.35F);
        GL11.glColor4f(red, green, blue, 1.0F);
        drawRing(ox, oy + 0.002, oz, radius * 0.34, points);
        drawRing(ox, oy + 0.004, oz, radius * 0.68, points);
        drawRing(ox, oy + 0.006, oz, radius, points);
        GL11.glLineWidth(1.0F);

        GlStateManager.depthMask(true);
        RenderUtil.disableRenderState();
        GlStateManager.resetColor();
    }
    private static void drawRing(double x, double y, double z, double radius, int points) {
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            GL11.glVertex3d(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }
    @Override
    public String[] getSuffix() {
        return new String[]{stageName(this.stage)};
    }
    private static String stageName(int stage) {
        switch (stage) {
            case STAGE_BLOCK:
                return "Block";
            case STAGE_SWAP:
                return "Swap";
            case STAGE_LADDER:
                return "Ladder";
            default:
                return "Idle";
        }
    }
}
