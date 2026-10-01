package myau.module.modules;

import java.util.ArrayList;
import java.util.List;

import myau.Myau;
import myau.access.AccessorEntityPlayerSP;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.BridgeInputEvent;
import myau.events.PacketEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.util.KeyBindUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 */
public class BridgeAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final EnumFacing[] SIDES = {
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
    };
    private static final float FAR_THRESHOLD = 180.0F;

    public final FloatProperty edgeOffset = new FloatProperty("edge-offset", 0.0F, 0.0F, 0.3F);
    public final FloatProperty unsneakDelay = new FloatProperty("unsneak-delay", 50.0F, 50.0F, 300.0F);
    public final FloatProperty sneakOnJump = new FloatProperty("sneak-on-jump", 0.0F, 0.0F, 500.0F);
    public final BooleanProperty sneakKeyPressed = new BooleanProperty("sneak-key-pressed", false);
    public final BooleanProperty holdingBlocks = new BooleanProperty("holding-blocks", false);
    public final BooleanProperty lookingDown = new BooleanProperty("looking-down", false);
    public final BooleanProperty notMovingForward = new BooleanProperty("not-moving-forward", false);
    public final BooleanProperty prePlace = new BooleanProperty("pre-place", false);
    public final BooleanProperty autoSneak = new BooleanProperty("auto-sneak", false);

    private boolean sneakingFromModule;
    private boolean placed;
    private boolean forceRelease;
    private int sneakJumpDelayTicks = -1;
    private int sneakJumpStartTick = -1;
    private int unsneakDelayTicks = -1;
    private int unsneakStartTick = -1;

    private static boolean updatedThisTick = false;
    private static float fakeYaw = Float.NaN;
    private static float realYaw = Float.NaN;
    private static float realPitch = Float.NaN;
    private static boolean movementFixActive = false;
    private static float fakePitch = Float.NaN;

    public BridgeAssist() {
        super("Bridge Assist", false);
    }

    @Override
    public String[] getSuffix() {
        double offset = this.edgeOffset.getValue();
        String text;
        if (offset == Math.rint(offset)) {
            text = Integer.toString((int) offset);
        } else {
            text = Double.toString(Math.round(offset * 100.0) / 100.0);
        }
        return new String[]{text};
    }

    @Override
    public void onDisabled() {
        this.sneakingFromModule = false;
        this.resetUnsneak();
        updatedThisTick = false;
        movementFixActive = false;
        fakeYaw = Float.NaN;
        fakePitch = Float.NaN;
    }

    public static float[] getFakeRotation() {
        BridgeAssist inst = (BridgeAssist) Myau.moduleManager.modules.get(BridgeAssist.class);
        if (inst != null) {
            inst.updateFakeRotation();
        }
        if (Float.isNaN(fakeYaw) || Float.isNaN(fakePitch)) {
            return null;
        }
        return new float[]{fakeYaw, fakePitch};
    }

    private void updateFakeRotation() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null
                || mc.thePlayer.capabilities.isFlying) {
            updatedThisTick = true;
            fakeYaw = Float.NaN;
            fakePitch = Float.NaN;
            return;
        }
        if (updatedThisTick) {
            return;
        }
        updatedThisTick = true;

        if (!this.isEnabled() || !this.prePlace.getValue()) {
            fakeYaw = Float.NaN;
            fakePitch = Float.NaN;
            return;
        }

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            fakeYaw = Float.NaN;
            fakePitch = Float.NaN;
            return;
        }
        if (this.lookingDown.getValue() && mc.thePlayer.rotationPitch < 70.0F) {
            fakeYaw = Float.NaN;
            fakePitch = Float.NaN;
            return;
        }
        if (this.notMovingForward.getValue()
                && mc.thePlayer.movementInput != null
                && mc.thePlayer.movementInput.moveForward > 0.0F) {
            fakeYaw = Float.NaN;
            fakePitch = Float.NaN;
            return;
        }

        float basePitch = AccessorEntityPlayerSP.getLastReportedPitch(mc.thePlayer);
        double reach = mc.playerController.getBlockReachDistance();

        TargetResult target = this.findTarget(basePitch, reach);
        if (target == null) {
            fakeYaw = Float.NaN;
            fakePitch = Float.NaN;
            return;
        }

        float baseYaw = AccessorEntityPlayerSP.getLastReportedYaw(mc.thePlayer);
        float[] sm = smoothRotation(baseYaw, basePitch, target.yaw, target.pitch, 15, 20.0F);
        float[] fixed = fixRotation(sm[0], sm[1], baseYaw, basePitch);
        fakeYaw = fixed[0];
        fakePitch = fixed[1];
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.thePlayer != null) {
            realYaw = mc.thePlayer.rotationYaw;
            realPitch = mc.thePlayer.rotationPitch;
        }
        if (event.getType() == EventType.POST) {
            updatedThisTick = false;
            movementFixActive = false;
            return;
        }
        if (event.getType() != EventType.PRE || !this.isEnabled()) {
            return;
        }
        if (!updatedThisTick) {
            this.updateFakeRotation();
        }
        if (!Float.isNaN(fakeYaw) && !Float.isNaN(fakePitch)) {
            event.setRotation(fakeYaw, fakePitch, 1);
            event.setPervRotation(fakeYaw, 1);
            movementFixActive = true;
        }
    }

    /**
     */
    public static boolean isHandlingMovementFix() {
        return movementFixActive;
    }

    /**
     */
    public static void fixMovementForServerRotation(BridgeInputEvent event) {
        if (!movementFixActive) {
            return;
        }
        float forward = event.getForward();
        float strafe = event.getStrafe();
        if (forward == 0.0F && strafe == 0.0F) {
            return;
        }
        float intended = realYaw;
        if (forward < 0.0F) {
            intended += 180.0F;
        }
        if (strafe != 0.0F) {
            float multiplier = forward == 0.0F ? 1.0F : 0.5F * Math.signum(forward);
            intended += -90.0F * multiplier * Math.signum(strafe);
        }
        float angle = MathHelper.wrapAngleTo180_float(intended - fakeYaw + 22.5F);
        int direction = (int) (angle + 180.0F) / 45 % 8;
        switch (direction) {
            case 0:
                event.setForward(-1.0F);
                event.setStrafe(0.0F);
                break;
            case 1:
                event.setForward(-1.0F);
                event.setStrafe(1.0F);
                break;
            case 2:
                event.setForward(0.0F);
                event.setStrafe(1.0F);
                break;
            case 3:
                event.setForward(1.0F);
                event.setStrafe(1.0F);
                break;
            case 4:
                event.setForward(1.0F);
                event.setStrafe(0.0F);
                break;
            case 5:
                event.setForward(1.0F);
                event.setStrafe(-1.0F);
                break;
            case 6:
                event.setForward(0.0F);
                event.setStrafe(-1.0F);
                break;
            default:
                event.setForward(-1.0F);
                event.setStrafe(-1.0F);
                break;
        }
    }

    @EventTarget
    public void onBridgeInput(BridgeInputEvent event) {
        if (!this.isEnabled()
                || mc.thePlayer == null || mc.theWorld == null
                || mc.currentScreen != null
                || mc.thePlayer.capabilities.isFlying) {
            return;
        }
        if (!this.autoSneak.getValue()) {
            this.sneakingFromModule = false;
            return;
        }

        boolean manualSneak = this.isManualSneak();
        boolean requireSneak = this.sneakKeyPressed.getValue();

        if (manualSneak && !requireSneak) {
            this.resetUnsneak();
            return;
        }

        if (requireSneak && (!manualSneak || (event.getForward() == 0.0F && event.getStrafe() == 0.0F))) {
            if (!manualSneak) {
                this.resetUnsneak();
            }
            this.repressSneak(event);
            return;
        }

        if (this.notMovingForward.getValue() && event.getForward() > 0.0F) {
            this.clearSneak(event);
            return;
        }
        if (this.lookingDown.getValue() && realPitch < 70.0F) {
            this.clearSneak(event);
            return;
        }
        if (this.holdingBlocks.getValue()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemBlock)) {
                this.clearSneak(event);
                return;
            }
        }

        if (event.isJump() && mc.thePlayer.onGround
                && (event.getForward() != 0.0F || event.getStrafe() != 0.0F)
                && this.sneakOnJump.getValue() > 0.0F) {
            if (!requireSneak || this.forceRelease) {
                this.sneakJumpStartTick = mc.thePlayer.ticksExisted;
                double raw = this.sneakOnJump.getValue() / 50.0;
                int base = (int) raw;
                this.sneakJumpDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
                this.pressSneak(event, true);
                return;
            }
        }

        SimulatedPlayer sim = SimulatedPlayer.fromKeyBinds();
        sim.rotationYaw = realYaw;
        sim.rotationPitch = realPitch;
        sim.movementInput.sneak = false;
        sim.tick();

        double offset = this.computeEdgeOffset(sim.getEntityBoundingBox());

        if (Double.isNaN(offset)) {
            if (event.isJump()
                    && (this.sneakOnJump.getValue() <= 0.0F
                    || (event.getForward() == 0.0F && event.getStrafe() == 0.0F))) {
                if (this.sneakingFromModule) {
                    this.tryReleaseSneak(event, true);
                }
            } else if (mc.thePlayer.onGround) {
                this.pressSneak(event, true);
            } else if (this.sneakingFromModule) {
                this.tryReleaseSneak(event, true);
            }
            return;
        }

        if (offset > this.edgeOffset.getValue()) {
            this.pressSneak(event, true);
        } else if (this.sneakingFromModule) {
            this.tryReleaseSneak(event, true);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != myau.event.types.EventType.SEND
                || !this.isEnabled()
                || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            return;
        }
        C08PacketPlayerBlockPlacement c08 = (C08PacketPlayerBlockPlacement) event.getPacket();
        if (c08.getPlacedBlockDirection() != 255
                && this.sneakingFromModule
                && this.sneakKeyPressed.getValue()) {
            this.placed = true;
        }
    }

    private void pressSneak(BridgeInputEvent event, boolean resetDelay) {
        event.setSneak(true);
        this.sneakingFromModule = true;
        if (resetDelay) {
            this.unsneakStartTick = -1;
        }
        this.repressSneak(event);
    }

    private void tryReleaseSneak(BridgeInputEvent event, boolean resetDelay) {
        int existed = mc.thePlayer.ticksExisted;
        if (this.unsneakStartTick == -1 && this.sneakJumpStartTick == -1) {
            this.unsneakStartTick = existed;
            double raw = (this.unsneakDelay.getValue() - 50.0F) / 50.0;
            int base = (int) raw;
            this.unsneakDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
        }

        if (this.sneakJumpStartTick != -1 && existed - this.sneakJumpStartTick < this.sneakJumpDelayTicks) {
            this.pressSneak(event, false);
            return;
        }
        if (this.unsneakStartTick != -1 && existed - this.unsneakStartTick < this.unsneakDelayTicks) {
            this.pressSneak(event, false);
            return;
        }

        this.releaseSneak(event, resetDelay);
    }

    private void releaseSneak(BridgeInputEvent event, boolean resetDelay) {
        if (!this.sneakKeyPressed.getValue()) {
            event.setSneak(false);
        } else if (this.sneakingFromModule && this.isManualSneak()
                && (this.placed || !mc.thePlayer.onGround)) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    mc.gameSettings.keyBindSneak.getKeyCode(), false);
            event.setSneak(false);
            this.forceRelease = true;
        } else if (this.forceRelease) {
            event.setSneak(false);
        }

        this.sneakingFromModule = false;
        this.placed = false;
        if (resetDelay) {
            this.resetUnsneak();
        }
    }

    private void repressSneak(BridgeInputEvent event) {
        if (this.forceRelease && this.isManualSneak()) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    mc.gameSettings.keyBindSneak.getKeyCode(), true);
            event.setSneak(true);
        }
        this.forceRelease = false;
    }

    private void clearSneak(BridgeInputEvent event) {
        this.sneakingFromModule = false;
        this.resetUnsneak();
        if (this.sneakKeyPressed.getValue()) {
            this.repressSneak(event);
        }
    }

    private void resetUnsneak() {
        this.unsneakStartTick = -1;
        this.sneakJumpStartTick = -1;
        this.sneakJumpDelayTicks = -1;
        this.unsneakDelayTicks = -1;
    }

    private boolean isManualSneak() {
        return KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());
    }

    private double computeEdgeOffset(AxisAlignedBB simBox) {
        AxisAlignedBB groundCheck = new AxisAlignedBB(
                simBox.minX, simBox.minY - 0.01, simBox.minZ,
                simBox.maxX, simBox.minY, simBox.maxZ
        );

        List<AxisAlignedBB> groundBoxes = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, groundCheck);
        if (groundBoxes.isEmpty()) {
            return Double.NaN;
        }

        double feetX = (simBox.minX + simBox.maxX) / 2.0;
        double feetZ = (simBox.minZ + simBox.maxZ) / 2.0;

        double minDist = Double.MAX_VALUE;
        for (AxisAlignedBB box : groundBoxes) {
            double closestX = Math.max(box.minX, Math.min(feetX, box.maxX));
            double closestZ = Math.max(box.minZ, Math.min(feetZ, box.maxZ));
            double dx = Math.abs(feetX - closestX);
            double dz = Math.abs(feetZ - closestZ);
            double dist = Math.max(dx, dz);
            minDist = Math.min(minDist, dist);
        }

        return minDist;
    }

    private TargetResult findTarget(float currentPitch, double reach) {
        float yaw = mc.thePlayer.rotationYaw;

        AxisAlignedBB bbox = mc.thePlayer.getEntityBoundingBox();
        int standY = MathHelper.floor_double(bbox.minY) - 1;
        int minX = MathHelper.floor_double(bbox.minX);
        int maxX = MathHelper.floor_double(bbox.maxX);
        int minZ = MathHelper.floor_double(bbox.minZ);
        int maxZ = MathHelper.floor_double(bbox.maxZ);

        ArrayList<FaceTarget> targets = new ArrayList<FaceTarget>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos standBlock = new BlockPos(x, standY, z);
                if (isReplaceable(standBlock)) {
                    continue;
                }
                for (EnumFacing face : SIDES) {
                    BlockPos placed = standBlock.offset(face);
                    if (!isReplaceable(placed)) {
                        continue;
                    }
                    targets.add(new FaceTarget(standBlock, face));
                }
            }
        }
        if (targets.isEmpty()) {
            return null;
        }

        float bestDelta = Float.MAX_VALUE;
        float bestPitch = Float.NaN;
        BlockPos bestSupport = null;
        EnumFacing bestFace = null;
        float randScale = 0.2F;

        for (float pitch = 60.0F; pitch <= 90.0F; ) {
            float step = 1.0F + (float) (Math.random() * 2.0D - 1.0D) * (0.3F + randScale * 0.4F);
            if (step < 0.4F) step = 0.4F;
            if (step > 1.8F) step = 1.8F;
            pitch += step;
            float samplePitch = Math.min(pitch, 90.0F);
            MovingObjectPosition mop = rayCastBlock(reach, yaw, samplePitch);
            if (mop == null) {
                continue;
            }
            EnumFacing hitFace = mop.sideHit;
            if (hitFace == EnumFacing.UP || hitFace == EnumFacing.DOWN) {
                continue;
            }

            BlockPos hitBlock = mop.getBlockPos();
            for (FaceTarget t : targets) {
                if (hitBlock.equals(t.block) && hitFace == t.face) {
                    float delta = Math.abs(samplePitch - currentPitch);
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        bestPitch = samplePitch;
                        bestSupport = t.block;
                        bestFace = t.face;
                    }
                    break;
                }
            }
            if (pitch >= 90.0F) {
                break;
            }
        }

        if (bestSupport == null || bestFace == null || Float.isNaN(bestPitch)) {
            return null;
        }
        return new TargetResult(yaw, bestPitch, bestSupport, bestFace);
    }


    private static boolean isReplaceable(BlockPos blockPos) {
        if (mc.theWorld == null) {
            return true;
        }
        Block block = mc.theWorld.getBlockState(blockPos).getBlock();
        return block.isReplaceable(mc.theWorld, blockPos);
    }

    private static MovingObjectPosition rayCastBlock(double distance, float yaw, float pitch) {
        Vec3 eyeVec = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 lookVec = getLookVec(yaw, pitch);
        Vec3 sumVec = eyeVec.addVector(
                lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyeVec, sumVec, false, false, false);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }
        return mop;
    }

    private static Vec3 getLookVec(float yaw, float pitch) {
        float f = MathHelper.cos(-yaw * ((float) Math.PI / 180.0F) - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * ((float) Math.PI / 180.0F) - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * ((float) Math.PI / 180.0F));
        float f3 = MathHelper.sin(-pitch * ((float) Math.PI / 180.0F));
        return new Vec3(f1 * f2, f3, f * f2);
    }

    private static float[] smoothRotation(float baseYaw, float basePitch,
                                          float targetYaw, float targetPitch,
                                          int speed, float randomizationPercent) {
        if (speed <= 0) {
            return new float[]{baseYaw, clampPitch(basePitch)};
        }
        if (speed >= 30) {
            return new float[]{targetYaw, clampPitch(targetPitch)};
        }
        float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - baseYaw);
        float deltaPitch = targetPitch - basePitch;
        float magnitude = (float) MathHelper.sqrt_double(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (magnitude < 0.001F) {
            return new float[]{targetYaw, clampPitch(targetPitch)};
        }
        float t = speed / 30.0F;
        float stepSize = t * t * 180.0F;
        float range = 0.6F * (float) (randomizationPercent / 100.0D);
        float multiplier = (range <= 0.001F) ? 1.0F : (1.0F - range / 2.0F + (float) (Math.random() * range));
        stepSize *= multiplier;
        float proximityFactor = Math.min(1.0F, magnitude / FAR_THRESHOLD);
        proximityFactor = (float) Math.pow(proximityFactor, 0.7D);
        float maxSlowdown = (float) (randomizationPercent / 100.0D);
        float proximityMult = Math.max(0.8F, 1.0F - maxSlowdown * (1.0F - proximityFactor));
        stepSize *= proximityMult;
        float stepLength = Math.min(stepSize, magnitude);
        float scale = stepLength / magnitude;
        float stepYaw = deltaYaw * scale;
        float stepPitch = deltaPitch * scale;
        float yaw = baseYaw + stepYaw;
        float pitch = basePitch + stepPitch;
        return new float[]{yaw, clampPitch(pitch)};
    }

    private static float clampPitch(float value) {
        return MathHelper.clamp_float(value, -90.0F, 90.0F);
    }

    private static float unwrapYaw(float yaw, float prevYaw) {
        return prevYaw + ((((yaw - prevYaw + 180.0F) % 360.0F) + 360.0F) % 360.0F - 180.0F);
    }

    private static float[] fixRotation(float targetYaw, float targetPitch, float baseYaw, float basePitch) {
        targetYaw = unwrapYaw(targetYaw, baseYaw);
        float deltaYaw = targetYaw - baseYaw;
        float deltaPitch = targetPitch - basePitch;
        float sens = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        double grid = (double) (sens * sens * sens) * 1.2D;
        float quantYaw = (float) ((double) Math.round((double) deltaYaw / grid) * grid);
        float quantPitch = (float) ((double) Math.round((double) deltaPitch / grid) * grid);
        return new float[]{baseYaw + quantYaw, clampPitch(basePitch + quantPitch)};
    }


    private static class SimulatedPlayer {
        double posX, posY, posZ;
        float rotationYaw, rotationPitch;
        boolean onGround;
        MovementInput movementInput;

        SimulatedPlayer(double posX, double posY, double posZ,
                        float rotationYaw, float rotationPitch, boolean onGround) {
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.rotationYaw = rotationYaw;
            this.rotationPitch = rotationPitch;
            this.onGround = onGround;
            this.movementInput = new MovementInput();
        }

        static SimulatedPlayer fromKeyBinds() {
            net.minecraft.entity.player.EntityPlayer player = mc.thePlayer;
            SimulatedPlayer sim = new SimulatedPlayer(
                    player.posX, player.posY, player.posZ,
                    player.rotationYaw, player.rotationPitch,
                    player.onGround
            );
            sim.movementInput.forward = mc.gameSettings.keyBindForward.isKeyDown() ? 1.0F
                    : (mc.gameSettings.keyBindBack.isKeyDown() ? -1.0F : 0.0F);
            sim.movementInput.strafe = mc.gameSettings.keyBindLeft.isKeyDown() ? 1.0F
                    : (mc.gameSettings.keyBindRight.isKeyDown() ? -1.0F : 0.0F);
            sim.movementInput.jump = mc.gameSettings.keyBindJump.isKeyDown();
            sim.movementInput.sneak = mc.gameSettings.keyBindSneak.isKeyDown();
            return sim;
        }

        void tick() {
            float forward = movementInput.forward;
            float strafe = movementInput.strafe;

            if (forward != 0.0F || strafe != 0.0F) {
                float moveSpeed = 0.1F;
                if (movementInput.sneak) {
                    moveSpeed *= 0.3F;
                }

                float yawRad = (float) Math.toRadians(rotationYaw);
                double dx = -Math.sin(yawRad) * strafe * moveSpeed
                        + -Math.sin(yawRad + Math.PI / 2.0D) * forward * moveSpeed;
                double dz = Math.cos(yawRad) * strafe * moveSpeed
                        + Math.cos(yawRad + Math.PI / 2.0D) * forward * moveSpeed;

                posX += dx;
                posZ += dz;

                if (onGround) {
                    posY -= 0.01D;
                    AxisAlignedBB bb = getEntityBoundingBox();
                    onGround = !mc.theWorld
                            .getCollidingBoundingBoxes(mc.thePlayer, bb.offset(0.0D, -0.01D, 0.0D))
                            .isEmpty();
                } else {
                    posY -= 0.08D;
                }
            }
        }

        AxisAlignedBB getEntityBoundingBox() {
            float width = 0.6F;
            float height = 1.8F;
            return new AxisAlignedBB(
                    posX - width / 2.0F, posY, posZ - width / 2.0F,
                    posX + width / 2.0F, posY + height, posZ + width / 2.0F
            );
        }

        static class MovementInput {
            float forward;
            float strafe;
            boolean jump;
            boolean sneak;
        }
    }

    private static class FaceTarget {
        final BlockPos block;
        final EnumFacing face;

        FaceTarget(BlockPos block, EnumFacing face) {
            this.block = block;
            this.face = face;
        }
    }

    private static class TargetResult {
        final float yaw;
        final float pitch;
        final BlockPos support;
        final EnumFacing face;

        TargetResult(float yaw, float pitch, BlockPos support, EnumFacing face) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.support = support;
            this.face = face;
        }
    }
}
