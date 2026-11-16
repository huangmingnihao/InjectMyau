package myau.module.modules;

import myau.access.AccessorMinecraft;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.KeyBindUtil;
import myau.util.SprintScaffoldMath;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.*;
import java.util.ArrayList;
import java.util.List;

/** Native port of E:/CScaffold.java using OpenB4 u20+ script semantics. */
public final class SprintScaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Scaffold parent;
    private EntityPlayerSP activePlayer;
    private float outputYaw, outputPitch;
    private net.minecraft.util.Timer ownedTimer;
    private float savedTimerSpeed, writtenTimerSpeed;
    public final ModeProperty rotations = new ModeProperty("Sprint Rotations", 0, new String[]{"HitVec", "Backward", "Offset", "Raycast", "Raycast2"}, this::visible);
    public final FloatProperty rotationSpeed = new FloatProperty("Sprint Rotation Speed", 2F, .5F, 10F, .5F, this::visible);
    public final ModeProperty fast = new ModeProperty("Sprint Fast Mode", 0, new String[]{"Disabled", "Telly", "Watchdog", "Watchdog2", "Watchdog3"}, this::visible);
    public final BooleanProperty tellyRmb = new BooleanProperty("Sprint Telly RMB", false, this::visible);
    public final BooleanProperty keepY = new BooleanProperty("Sprint Keep Y", false, this::visible);
    public final ModeProperty tower = new ModeProperty("Sprint Tower", 0, new String[]{"Disabled", "Watchdog", "Watchdog Low"}, this::visible);
    public final BooleanProperty blockCount = new BooleanProperty("Sprint Block Count", true, this::visible);
    public final BooleanProperty silentSwing = new BooleanProperty("Sprint Silent Swing", false, this::visible);
    public final BooleanProperty sneak = new BooleanProperty("Sprint Sneak", false, this::visible);
    public final IntProperty sneakDelay = new IntProperty("Sprint Sneak Delay", 0, 0, 25, this::visible);
    public final IntProperty timerTicks = new IntProperty("Sprint Timer Ticks", 10, 2, 15, this::visible);

    public SprintScaffold(Scaffold parent) {
        super("SprintScaffold", false);
        this.parent = parent;
        YAW_OFFSETS = buildOffsets(180);
        PITCH_OFFSETS = buildOffsets(90);
    }
    private boolean visible() { return parent.isSprintMode(); }
    private boolean active() {
        return parent.isEnabled() && visible() && mc.thePlayer != null && mc.theWorld != null;
    }
    private boolean ready() { return active() && activePlayer == mc.thePlayer; }
    public void release() {
        if (activePlayer != null) stop();
        releaseTimer();
        activePlayer = null;
        originalSlot = blockSlot = -1;
    }
    @EventTarget(Priority.HIGH)
    public void update(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (!active()) { release(); return; }
        if (activePlayer != mc.thePlayer) {
            release();
            activePlayer = mc.thePlayer;
            placeCooldown = 0;
            rotationReady = rotatedThisTick = snapNextRotation = false;
            start();
        }
        rightHeld = KeyBindUtil.isMouseButtonDown(1);
        onPreUpdate();
        float[] angles = getRotations(event.getYaw(), event.getPitch());
        outputYaw = angles[0]; outputPitch = angles[1];
        event.setRotation(outputYaw, outputPitch, 3);
        event.setPervRotation(outputYaw, 3);
    }
    @EventTarget(Priority.HIGH)
    public void input(MoveInputEvent event) {
        if (!ready()) return;
        onPostPlayerInput();
        // OpenB4 corrects movement after script input using the current tick's rotation.
        if (mc.thePlayer.movementInput.moveForward != 0 || mc.thePlayer.movementInput.moveStrafe != 0) {
            float[] input = SprintScaffoldMath.fixInput(mc.thePlayer.rotationYaw, outputYaw,
                    mc.thePlayer.movementInput.moveForward, mc.thePlayer.movementInput.moveStrafe);
            mc.thePlayer.movementInput.moveForward = input[0];
            mc.thePlayer.movementInput.moveStrafe = input[1];
        }
    }
    @EventTarget
    public void motion(PlayerUpdateEvent event) { if (ready()) onPreMotion(); }
    @EventTarget
    public void tick(TickEvent event) { if (!active()) release(); }
    @EventTarget
    public void world(LoadWorldEvent event) { release(); }
    @EventTarget
    public void left(LeftClickMouseEvent event) { if (ready() && mc.currentScreen == null) event.setCancelled(true); }
    @EventTarget
    public void right(RightClickMouseEvent event) { if (ready() && mc.currentScreen == null) event.setCancelled(true); }
    @EventTarget
    public void hit(HitBlockEvent event) { if (ready() && mc.currentScreen == null) event.setCancelled(true); }
    @EventTarget
    public void render(Render2DEvent event) {
        if (!ready() || !blockCount.getValue() || mc.currentScreen != null) return;
        ScaledResolution display = new ScaledResolution(mc);
        String text = "\u00a77Blocks: \u00a7f" + hotbarBlockCount();
        mc.fontRendererObj.drawString(text, (display.getScaledWidth() - mc.fontRendererObj.getStringWidth(text)) / 2F,
                display.getScaledHeight() / 2F + 12F, -1, true);
    }
    private double slider(String name) {
        switch (name) {
            case "Rotations": return rotations.getValue();
            case "Fast Mode": return fast.getValue();
            case "Tower": return tower.getValue();
            case "Rotation Speed": return rotationSpeed.getValue();
            case "Sneak Delay": return sneakDelay.getValue();
            case "Timer Ticks": return timerTicks.getValue();
            default: throw new IllegalArgumentException(name);
        }
    }
    private int sliderIndex(String name) { return (int) Math.round(slider(name)); }
    private boolean setting(String name) {
        switch (name) {
            case "Telly RMB": return tellyRmb.getValue();
            case "Keep Y": return keepY.getValue();
            case "Block Count": return blockCount.getValue();
            case "Silent Swing": return silentSwing.getValue();
            case "Sneak": return sneak.getValue();
            default: throw new IllegalArgumentException(name);
        }
    }
    private void setTimer(double speed) {
        net.minecraft.util.Timer timer = AccessorMinecraft.getTimer(mc);
        if (timer == null) return;
        if (ownedTimer != timer || timer.timerSpeed != writtenTimerSpeed) {
            releaseTimer(); ownedTimer = timer; savedTimerSpeed = timer.timerSpeed;
        }
        timer.timerSpeed = writtenTimerSpeed = (float) speed;
    }
    private void releaseTimer() {
        if (ownedTimer != null && ownedTimer.timerSpeed == writtenTimerSpeed) ownedTimer.timerSpeed = savedTimerSpeed;
        ownedTimer = null;
    }
    private static Vec3 position(EntityPlayerSP p) { return new Vec3(p.posX, p.posY, p.posZ); }
    private static Vec3 blockPosition(EntityPlayerSP p) {
        BlockPos pos = new BlockPos(p.posX, SprintScaffoldMath.blockY(p.posY), p.posZ);
        return new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }
    private Object[] raycastBlock(double reach) { return raycastBlock(reach, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch); }
    private Object[] raycastBlock(double reach, float yaw, float pitch) {
        net.minecraft.util.Vec3 eyes = mc.thePlayer.getPositionEyes(1F);
        float cy = MathHelper.cos(-yaw * .017453292F - (float) Math.PI);
        float sy = MathHelper.sin(-yaw * .017453292F - (float) Math.PI);
        float cp = -MathHelper.cos(-pitch * .017453292F);
        float sp = MathHelper.sin(-pitch * .017453292F);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyes, eyes.addVector(sy * cp * reach, sp * reach, cy * cp * reach), false, false, false);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        return new Object[]{new Vec3(pos.getX(), pos.getY(), pos.getZ()),
                new Vec3(hit.hitVec.xCoord - pos.getX(), hit.hitVec.yCoord - pos.getY(), hit.hitVec.zCoord - pos.getZ()), hit.sideHit.name()};
    }
    private static final class Vec3 {
        final double x, y, z;
        Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        Vec3 offset(double x, double y, double z) { return new Vec3(this.x + x, this.y + y, this.z + z); }
        BlockPos blockPos() { return new BlockPos(x, y, z); }
        net.minecraft.util.Vec3 nativeVec() { return new net.minecraft.util.Vec3(x, y, z); }
        @Override public boolean equals(Object o) { if (!(o instanceof Vec3)) return false; Vec3 v = (Vec3) o; return x == v.x && y == v.y && z == v.z; }
        @Override public int hashCode() { return blockPos().hashCode(); }
    }
    String[] ROTATION_MODES = new String[] {"HitVec", "Backward", "Offset", "Raycast", "Raycast2"};
    String[] FAST_MODES = new String[] {"Disabled", "Telly", "Watchdog", "Watchdog2", "Watchdog3"};
    String[] TOWER_MODES = new String[] {"Disabled", "Watchdog", "Watchdog Low"};

    int ROTATION_HITVEC = 0;
    int ROTATION_BACKWARD = 1;
    int ROTATION_OFFSET = 2;
    int ROTATION_RAYCAST = 3;
    int ROTATION_RAYCAST2 = 4;

    int FAST_DISABLED = 0;
    int FAST_TELLY = 1;
    int FAST_WATCHDOG = 2;
    int FAST_WATCHDOG2 = 3;
    int FAST_WATCHDOG3 = 4;

    int TOWER_DISABLED = 0;
    int TOWER_WATCHDOG_LOW = 2;

    float[] YAW_OFFSETS;
    float[] PITCH_OFFSETS;

    int keepYLevel;
    int originalSlot = -1;
    int blockSlot = -1;
    int placeCooldown;
    int towerTimerTicks;

    float serverYaw;
    float serverPitch;
    float lastTargetYaw;
    float lastTargetPitch;
    float[] targetRotation = new float[2];

    boolean rotationReady;
    boolean rotatedThisTick;
    boolean wasOnGround;
    boolean rightHeld;
    boolean timerModified;
    boolean snapNextRotation;
    boolean lowerCaseSides;

    float[] buildOffsets(int max) {
        List<Float> values = new ArrayList<>();
        values.add(0.0f);
        for (int step = 1; step <= max; step += step < 20 ? 1 : 2) {
            values.add((float) -step);
            values.add((float) step);
        }
        float[] result = new float[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    int rotationMode() {
        return sliderIndex("Rotations");
    }

    int fastMode() {
        return sliderIndex("Fast Mode");
    }

    int towerMode() {
        return sliderIndex("Tower");
    }

    boolean isTellyStyle(int mode) {
        return mode == FAST_TELLY || mode == FAST_WATCHDOG || mode == FAST_WATCHDOG2;
    }

    boolean jumpKeyDown() { return KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()); }

    float cameraYaw() {
        return mc.thePlayer.rotationYaw;
    }

    float wrapDegrees(float angle) {
        float wrapped = angle % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    String blockName(Vec3 position) { return Block.blockRegistry.getNameForObject(mc.theWorld.getBlockState(position.blockPos()).getBlock()).getResourcePath(); }

    boolean isAir(Vec3 position) {
        return blockName(position).equals("air");
    }

    void learnSideFormat(Object[] hit) {
        if (hit == null || hit[2] == null) return;
        String side = (String) hit[2];
        lowerCaseSides = side.equals(side.toLowerCase());
    }

    String sideName(String face) {
        return lowerCaseSides ? face.toLowerCase() : face.toUpperCase();
    }

    int findBlockSlot() {
        int bestSlot = -1;
        int bestCount = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemBlock && stack.stackSize > bestCount) {
                bestCount = stack.stackSize;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    int hotbarBlockCount() {
        int total = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemBlock) total += stack.stackSize;
        }
        return total;
    }

    Object[] getPlaceData(Vec3 target) {
        Vec3 playerPos = position(mc.thePlayer);
        double bestDistance = Double.MAX_VALUE;
        Vec3 bestPos = null;
        String bestFace = null;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -2; dy <= 0; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    Vec3 candidate = target.offset(dx, dy, dz);
                    if (isAir(candidate)) continue;
                    int ax = Math.abs(dx);
                    int ay = Math.abs(dy);
                    int az = Math.abs(dz);
                    String face;
                    if (ax >= ay && ax >= az) {
                        face = dx < 0 ? "EAST" : "WEST";
                    } else if (ay >= ax && ay >= az) {
                        face = dy < 0 ? "UP" : "DOWN";
                    } else {
                        face = dz < 0 ? "SOUTH" : "NORTH";
                    }
                    if (face.equals("DOWN")) continue;
                    double ox = playerPos.x - (candidate.x + 0.5);
                    double oy = playerPos.y - (candidate.y + 0.5);
                    double oz = playerPos.z - (candidate.z + 0.5);
                    double distance = ox * ox + oy * oy + oz * oz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = candidate;
                        bestFace = face;
                    }
                }
            }
        }
        return bestPos == null ? null : new Object[] {bestPos, bestFace};
    }

    float offsetYaw() {
        float yaw = cameraYaw();
        yaw += (yaw % 90.0f) > 45.0f ? -45.0f : 45.0f;
        return yaw - 180.0f;
    }

    void computeRotations(Vec3 placePos, String face, Vec3 hitPoint) {
        EntityPlayerSP player = mc.thePlayer;
        Vec3 eye = position(player).offset(0, player.getEyeHeight(), 0);
        double dx = hitPoint.x - eye.x;
        double dy = hitPoint.y - eye.y;
        double dz = hitPoint.z - eye.z;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        int mode = rotationMode();
        if (mode == ROTATION_BACKWARD) {
            targetRotation[0] = cameraYaw() - 180.0f;
            targetRotation[1] = pitch;
        } else if (mode == ROTATION_OFFSET) {
            targetRotation[0] = offsetYaw();
            targetRotation[1] = 83.0f;
        } else if (mode == ROTATION_RAYCAST) {
            raycastRotations(placePos, face, cameraYaw() - 180.0f);
        } else if (mode == ROTATION_RAYCAST2) {
            raycastRotations(placePos, face, lastTargetYaw);
        } else {
            targetRotation[0] = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            targetRotation[1] = pitch;
        }
    }

    void raycastRotations(Vec3 placePos, String face, float baseYaw) {
        boolean watchdog3 = fastMode() == FAST_WATCHDOG3;
        float camera = cameraYaw();
        if (watchdog3) baseYaw = camera;
        float bestDiff = 0.0f;
        float bestYaw = 0.0f;
        float bestPitch = 0.0f;
        for (float yawOffset : YAW_OFFSETS) {
            for (float pitchOffset : PITCH_OFFSETS) {
                float yaw = baseYaw + yawOffset;
                float pitch = clamp(pitchOffset, -90.0f, 90.0f);
                Object[] hit = raycastBlock(4.5, yaw, pitch);
                if (hit == null) continue;
                learnSideFormat(hit);
                if (!((Vec3) hit[0]).equals(placePos)) continue;
                if (!((String) hit[2]).equalsIgnoreCase(face)) continue;
                if (watchdog3) {
                    float diff = Math.abs(wrapDegrees(yaw - camera + 180.0f));
                    if (diff > bestDiff) {
                        bestDiff = diff;
                        bestYaw = yaw;
                        bestPitch = pitch;
                    }
                    if (diff > 90.0f) continue;
                }
                targetRotation[0] = yaw;
                targetRotation[1] = pitch;
                return;
            }
        }
        if (watchdog3 && bestDiff != 0.0f) {
            targetRotation[0] = bestYaw;
            targetRotation[1] = bestPitch;
            return;
        }
        rotationReady = false;
        rotatedThisTick = true;
        targetRotation[0] = lastTargetYaw;
        targetRotation[1] = lastTargetPitch;
    }

    void stepRotations(float targetYaw, float targetPitch) {
        if (rotatedThisTick) return;
        rotatedThisTick = true;
        int mode = fastMode();
        float speed = (float) (slider("Rotation Speed") * 20.0 - Math.random());
        if (wasOnGround && mode == FAST_WATCHDOG) {
            speed = (float) (89.0 - Math.random());
        }
        if (snapNextRotation) {
            speed = (float) (129.0 - Math.random());
            snapNextRotation = false;
        } else if (mode == FAST_WATCHDOG2) {
            speed = 34.0f;
        }
        if (wasOnGround && mode == FAST_WATCHDOG2) {
            snapNextRotation = true;
        }
        float yawStep = clamp(wrapDegrees(targetYaw - serverYaw), -speed, speed);
        float pitchStep = clamp(wrapDegrees(targetPitch - serverPitch), -speed, speed);
        serverYaw += yawStep;
        float camera = cameraYaw();
        serverYaw = camera + wrapDegrees(serverYaw - camera);
        serverPitch += pitchStep;
        rotationReady = Math.abs(wrapDegrees(serverYaw - targetYaw)) <= 0.1f
            && Math.abs(wrapDegrees(serverPitch - targetPitch)) <= 0.1f;
        if (mode == FAST_WATCHDOG3) {
            serverYaw = targetYaw;
            rotationReady = true;
        }
    }

    void swingArm() {
        if (setting("Silent Swing")) {
            mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
        } else {
            mc.thePlayer.swingItem();
        }
    }

    void placeTick(EntityPlayerSP player, boolean jumpDown) {
        Vec3 target = blockPosition(player).offset(0, -1, 0);
        if (setting("Keep Y")) {
            target = target.offset(0, keepYLevel - target.y, 0);
        }
        if (!isAir(target)) return;
        blockSlot = findBlockSlot();
        if (blockSlot == -1) return;
        Object[] data = getPlaceData(target);
        if (data == null) return;
        Vec3 placePos = (Vec3) data[0];
        String face = (String) data[1];
        Vec3 hitPoint = placePos.offset(0.5, 0.5, 0.5);
        computeRotations(placePos, face, hitPoint);
        stepRotations(targetRotation[0], targetRotation[1]);
        lastTargetYaw = targetRotation[0];
        lastTargetPitch = targetRotation[1];
        if (towerMode() != TOWER_DISABLED && jumpDown && player.onGround) {
            rotationReady = false;
        }
        if (rotationReady) {
            if (mc.thePlayer.inventory.currentItem != blockSlot) mc.thePlayer.inventory.currentItem = blockSlot;
            mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), placePos.blockPos(), EnumFacing.valueOf(face), hitPoint.nativeVec());
            swingArm();
            placeCooldown = 10;
        }
    }

    void resetTimer() {
        if (timerModified) {
            releaseTimer();
            timerModified = false;
        }
    }

    void towerTick(EntityPlayerSP player, boolean jumpDown) {
        int tower = towerMode();
        if (tower == TOWER_DISABLED) return;
        if (player.onGround && jumpDown) {
            serverYaw = cameraYaw();
        }
        if (tower != TOWER_WATCHDOG_LOW) return;
        boolean falling = !player.onGround && mc.thePlayer.motionY < -0.1;
        if (falling && jumpDown && towerTimerTicks < slider("Timer Ticks")) {
            setTimer(2.0);
            towerTimerTicks++;
            timerModified = true;
        } else {
            resetTimer();
        }
    }

    void watchdog3Tick(EntityPlayerSP player, boolean jumpDown) {
        float camera = cameraYaw();
        float diff = Math.abs(wrapDegrees(camera + 45.0f - serverYaw));
        int snapTick = diff > 60.0f ? 8 : 9;
        if (placeCooldown == snapTick && !jumpDown && wasOnGround && player.onGround) {
            serverYaw = camera + 45.0f;
        }
        if (player.onGround) {
            keepYLevel = (int) (position(player).y - 1.0);
        }
    }

    float[] getRotations(float lastYaw, float lastPitch) {
        EntityPlayerSP player = mc.thePlayer;
        boolean jumpDown = jumpKeyDown();
        int mode = fastMode();
        if (setting("Keep Y") && jumpDown) {
            keepYLevel = (int) position(player).y - 1;
        }
        if (placeCooldown > 0) placeCooldown--;

        if (isTellyStyle(mode) && player.onGround && (!setting("Telly RMB") || rightHeld)) {
            float camera = cameraYaw();
            serverYaw = camera;
            wasOnGround = player.onGround;
            return new float[] {camera, player.rotationPitch};
        }

        rotatedThisTick = false;
        placeTick(player, jumpDown);
        if (mode == FAST_WATCHDOG3) {
            watchdog3Tick(player, jumpDown);
        } else {
            stepRotations(lastTargetYaw, lastTargetPitch);
        }
        towerTick(player, jumpDown);
        if (mode != FAST_WATCHDOG3) {
            wasOnGround = player.onGround;
        }
        return new float[] {serverYaw, serverPitch};
    }

    void onPreUpdate() {
        learnSideFormat(raycastBlock(5.0));
        if (blockSlot != -1 && mc.thePlayer.inventory.currentItem != blockSlot) {
            mc.thePlayer.inventory.currentItem = blockSlot;
        }
    }

    void onPostPlayerInput() {
        int mode = fastMode();
        if (mode == FAST_WATCHDOG3 && !wasOnGround && placeCooldown != 10) {
            mc.thePlayer.movementInput.jump = true;
            wasOnGround = true;
        }
        if (isTellyStyle(mode) && (mc.thePlayer.movementInput.moveForward != 0 || mc.thePlayer.movementInput.moveStrafe != 0) && (!setting("Telly RMB") || rightHeld)) {
            mc.thePlayer.movementInput.jump = true;
        }
        if (setting("Sneak")) {
            int interval = (int) Math.round(slider("Sneak Delay") + 1.0);
            if (mc.thePlayer.ticksExisted % interval == 0) {
                mc.thePlayer.movementInput.sneak = true;
            }
        }
    }

    void onPreMotion() {
        if (jumpKeyDown()) return;
        if (towerTimerTicks > 0) {
            towerTimerTicks--;
            setTimer(0.5);
            timerModified = true;
        } else {
            resetTimer();
        }
    }

    void start() {
        EntityPlayerSP player = mc.thePlayer;
        keepYLevel = (int) (position(player).y - 1.0);
        originalSlot = mc.thePlayer.inventory.currentItem;
        blockSlot = -1;
        serverYaw = player.rotationYaw;
        serverPitch = 83.0f;
        lastTargetYaw = cameraYaw() - 180.0f;
        lastTargetPitch = 83.0f;
        wasOnGround = false;
        towerTimerTicks = 0;
    }

    void stop() {
        if (mc.thePlayer == activePlayer && originalSlot >= 0 && originalSlot < 9) mc.thePlayer.inventory.currentItem = originalSlot;
        releaseTimer();
        timerModified = false;
    }

}
