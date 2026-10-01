package myau.module.modules;

import myau.Myau;
import myau.access.AccessorKeyBinding;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LeftClickMouseEvent;
import myau.events.PlayerUpdateEvent;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.events.RightClickMouseEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.util.ProgressBar;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.KeyProperty;
import myau.property.properties.PercentProperty;
import myau.util.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockWall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class BlockIn extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final EnumFacing[] HORIZONTALS = {
            EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.NORTH
    };
    private static final SupportOffset[] SUPPORTS = {
            new SupportOffset(0, 1, 0, EnumFacing.DOWN),
            new SupportOffset(0, -1, 0, EnumFacing.UP),
            new SupportOffset(0, 0, -1, EnumFacing.NORTH),
            new SupportOffset(0, 0, 1, EnumFacing.SOUTH),
            new SupportOffset(1, 0, 0, EnumFacing.EAST),
            new SupportOffset(-1, 0, 0, EnumFacing.WEST),
    };
    private static final double REACH = 4.5;
    private static final double GRID_INSET = 0.05;
    private static final double GRID_STEP = 0.2;
    private static final int GRID_N = (int) Math.round(1.0 / GRID_STEP);

    private static final float ENCLOSING_CELLS = 9.0F;
    public final IntProperty speed = new IntProperty("speed", 10, 1, 30);
    public final PercentProperty randomization = new PercentProperty("randomization", 10);
    public final IntProperty rotationTolerance = new IntProperty("rotation-tolerance", 25, 20, 100);
    public final KeyProperty selectKey = new KeyProperty("select-key", KeyProperty.NONE);
    public final BooleanProperty progressBar = new BooleanProperty("progress-bar", true);
    private boolean placing;
    private boolean slotWasSwapped;
    private int prevSlot = -1;
    private int plannedSlot = -1;
    private boolean placeQueued;
    private BlockPos targetHitPos;
    private EnumFacing targetSide;
    private float aimYaw;
    private float aimPitch;
    private BlockPos hitAt;
    private EnumFacing hitSide;
    private Vec3 placeAt;
    private float fillCount;
    private float lastFillCount = -1.0F;
    private boolean lastTargetAdjacent;
    private float heldYaw;
    private float heldPitch;
    private boolean holding;
    public BlockIn() {
        super("Block In", false);
    }
    @Override
    public void onDisabled() {
        this.holding = false;
        this.disablePlacing();
        this.placeQueued = false;
        this.fillCount = 0.0F;
        this.lastFillCount = -1.0F;
    }
    public boolean isPlacing() {
        return this.placing;
    }
    private float aimBaseYaw() {
        return this.holding ? this.heldYaw : mc.thePlayer.rotationYaw;
    }
    private float aimBasePitch() {
        return this.holding ? this.heldPitch : mc.thePlayer.rotationPitch;
    }

    private boolean isSelectHeld() {
        return this.selectKey.isHeld();
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        this.runTargetSelection();
        if (mc.currentScreen != null) {
            this.disablePlacing();
        }
        if (!this.placing || this.targetHitPos == null) {
            return;
        }

        if (!this.holding) {
            this.heldYaw = event.getNewYaw();
            this.heldPitch = event.getNewPitch();
            this.holding = true;
        }
        float[] smoothed = smoothRotation(this.heldYaw, this.heldPitch, this.aimYaw, this.aimPitch,
                this.speed.getValue(), this.randomization.getValue());
        this.heldYaw = smoothed[0];
        this.heldPitch = smoothed[1];
        MovingObjectPosition hit = rayCastBlock(REACH, smoothed[0], smoothed[1]);
        if (hit != null) {
            BlockPos hitBlock = hit.getBlockPos();
            EnumFacing side = hit.sideHit;
            if (hitBlock.equals(this.targetHitPos) && side == this.targetSide) {

                double tolerance = this.rotationTolerance.getValue();
                if (Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - this.aimYaw)) <= tolerance
                        && Math.abs(smoothed[1] - this.aimPitch) <= tolerance) {
                    this.hitAt = hitBlock;
                    this.hitSide = side;
                    this.placeAt = hit.hitVec;
                    this.placeQueued = true;
                }
            }
        }
        event.setRotation(smoothed[0], smoothed[1], ROTATION_PRIORITY);
        event.setPervRotation(smoothed[0], ROTATION_PRIORITY);
    }
    private static final int ROTATION_PRIORITY = 2;
    private void runTargetSelection() {
        this.clearAim();
        if (!this.isSelectHeld() || mc.currentScreen != null) {
            this.disablePlacing();
                return;
        }
        int strongSlot = this.pickBlockSlot(true);
        int weakSlot = this.pickBlockSlot(false);
        if (strongSlot == -1 && weakSlot == -1) {
            this.disablePlacing();
            return;
        }
        this.plannedSlot = strongSlot != -1 ? strongSlot : weakSlot;
        if (!this.getTarget()) {
            this.disablePlacing();
            return;
        }
        if (this.lastTargetAdjacent) {
            this.plannedSlot = strongSlot != -1 ? strongSlot : weakSlot;
        } else {
            this.plannedSlot = weakSlot != -1 ? weakSlot : strongSlot;
        }
        if (!this.placing) {
            this.enablePlacing();
        }
        if (mc.gameSettings.keyBindAttack.isKeyDown() || mc.gameSettings.keyBindUseItem.isKeyDown()) {
            this.clearAim();
        }

        AccessorKeyBinding.setPressed(mc.gameSettings.keyBindAttack, false);
        AccessorKeyBinding.setPressed(mc.gameSettings.keyBindUseItem, false);
        this.equipPlannedSlot();
    }

    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (this.placeQueued) {
            this.placeQueued = false;
            if (this.hitAt != null && this.hitSide != null && this.placeAt != null) {
                if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                        mc.thePlayer.getHeldItem(), this.hitAt, this.hitSide, this.placeAt)) {
                    mc.thePlayer.swingItem();
                }
            }
        }
        this.fillCount = 0.0F;
        if (!this.isSelectHeld() || mc.currentScreen != null) {
            return;
        }
        BlockPos feet = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY),
                MathHelper.floor_double(mc.thePlayer.posZ));
        if (!BlockUtil.isReplaceable(feet.up().up())) {
            this.fillCount++;
        }
        for (EnumFacing dir : HORIZONTALS) {
            BlockPos side = feet.offset(dir);
            if (!BlockUtil.isReplaceable(side)) {
                this.fillCount++;
            }
            if (!BlockUtil.isReplaceable(side.up())) {
                this.fillCount++;
            }
        }

        this.lastFillCount = this.fillCount;
    }
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || !this.progressBar.getValue() || mc.thePlayer == null) {
            return;
        }
        if (this.fillCount > 0.0F) {
            HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
            ProgressBar.push(this.fillCount / ENCLOSING_CELLS,
                    hud == null ? 1.0F : hud.scale.getValue());
        }
        ProgressBar.render();
    }
    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            ProgressBar.tick();
        }
    }
    @EventTarget(Priority.HIGHEST)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled() && this.placing) {
            event.setCancelled(true);
        }
    }
    @EventTarget(Priority.HIGHEST)
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled() && this.placing) {
            event.setCancelled(true);
        }
    }
    private void enablePlacing() {
        if (this.placing) {
            return;
        }
        this.placing = true;
        this.slotWasSwapped = false;
        this.prevSlot = mc.thePlayer.inventory.currentItem;
    }

    private void disablePlacing() {
        this.holding = false;
        if (!this.placing) {
            return;
        }
        if (this.slotWasSwapped && this.prevSlot != -1 && this.prevSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = this.prevSlot;
        }
        this.placing = false;
        this.slotWasSwapped = false;
        this.prevSlot = -1;
        this.plannedSlot = -1;

        if (mc.currentScreen == null) {
            AccessorKeyBinding.setPressed(mc.gameSettings.keyBindAttack, Mouse.isButtonDown(0));
            AccessorKeyBinding.setPressed(mc.gameSettings.keyBindUseItem, Mouse.isButtonDown(1));
        }
    }
    private void clearAim() {
        this.targetHitPos = null;
        this.targetSide = null;
    }
    private void equipPlannedSlot() {
        int current = mc.thePlayer.inventory.currentItem;
        if (this.plannedSlot != -1 && this.plannedSlot != current) {
            mc.thePlayer.inventory.currentItem = this.plannedSlot;
            this.slotWasSwapped = true;
        }
    }
    private int pickBlockSlot(boolean preferStrong) {
        int best = -1;
        float bestScore = preferStrong ? -1.0F : Float.MAX_VALUE;
        for (int slot = 8; slot >= 0; slot--) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
            if (stack == null || stack.stackSize == 0 || !(stack.getItem() instanceof ItemBlock)) {
                continue;
            }
            float score = fistBreakTicks(((ItemBlock) stack.getItem()).getBlock());
            if (preferStrong ? score > bestScore : score < bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private boolean getTarget() {
        AimResult result = this.roofAim();
        if (result == null) {
            result = this.sidesAim();
        }
        if (result == null) {
            return false;
        }
        this.lastTargetAdjacent = this.isDirectAdjacent(result.support.offset(result.face));
        this.targetHitPos = result.support;
        this.targetSide = result.face;
        this.aimYaw = result.yaw;
        this.aimPitch = result.pitch;
        return true;
    }
    private AimResult roofAim() {
        BlockPos aboveHead = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY) + 2,
                MathHelper.floor_double(mc.thePlayer.posZ));
        if (!BlockUtil.isReplaceable(aboveHead)) {
            return null;
        }
        if (this.plannedSlot < 0 || this.plannedSlot > 8) {
            return null;
        }
        ItemStack held = mc.thePlayer.inventory.mainInventory[this.plannedSlot];
        Vec3 eye = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        double reachSq = REACH * REACH;
        double looseSq = (REACH + 1) * (REACH + 1);
        int minY = MathHelper.floor_double(eye.yCoord) + 1;
        int maxY = MathHelper.floor_double(eye.yCoord + REACH);
        List<BlockCandidate> candidates = new ArrayList<BlockCandidate>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = MathHelper.floor_double(eye.xCoord - REACH); x <= MathHelper.floor_double(eye.xCoord + REACH); x++) {
                for (int z = MathHelper.floor_double(eye.zCoord - REACH); z <= MathHelper.floor_double(eye.zCoord + REACH); z++) {
                    double dx = x + 0.5 - eye.xCoord;
                    double dy = y + 0.5 - eye.yCoord;
                    double dz = z + 0.5 - eye.zCoord;

                    if (dx * dx + dy * dy + dz * dz > looseSq) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (BlockUtil.isReplaceable(pos)) {
                        continue;
                    }
                    Block block = mc.theWorld.getBlockState(pos).getBlock();
                    if (BlockUtil.isInteractable(block) || block instanceof BlockFence || block instanceof BlockWall) {
                        continue;
                    }
                    double distance = distanceToBox(eye, pos);
                    if (distance > reachSq) {
                        continue;
                    }
                    candidates.add(new BlockCandidate(distance, pos));
                }
            }
        }

        Collections.sort(candidates);
        for (BlockCandidate candidate : candidates) {
            AimResult result = this.bestRotationTo(held, candidate.pos, eye, minY);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private AimResult bestRotationTo(ItemStack held, BlockPos cell, Vec3 eye, int minY) {
        float baseYaw = this.aimBaseYaw();
        float basePitch = this.aimBasePitch();
        boolean faceUp = Math.abs(eye.yCoord - (cell.getY() + 1)) < Math.abs(eye.yCoord - cell.getY());
        boolean faceSouth = Math.abs(eye.zCoord - (cell.getZ() + 1)) < Math.abs(eye.zCoord - cell.getZ());
        boolean faceEast = Math.abs(eye.xCoord - (cell.getX() + 1)) < Math.abs(eye.xCoord - cell.getX());
        double bx = cell.getX();
        double by = cell.getY();
        double bz = cell.getZ();
        double jitter = GRID_STEP * 0.1;
        List<RotationCandidate> candidates = new ArrayList<RotationCandidate>();
        candidates.add(new RotationCandidate(0.0, baseYaw, basePitch));
        for (int row = 0; row <= GRID_N; row++) {
            double v = clamp01(row * GRID_STEP + jitter(jitter));
            for (int col = 0; col <= GRID_N; col++) {
                double u = clamp01(col * GRID_STEP + jitter(jitter));
                addCandidate(candidates, eye, baseYaw, basePitch,
                        bx + u, faceUp ? by + 1 - GRID_INSET : by + GRID_INSET, bz + v);
                addCandidate(candidates, eye, baseYaw, basePitch,
                        bx + u, by + v, faceSouth ? bz + 1 - GRID_INSET : bz + GRID_INSET);
                addCandidate(candidates, eye, baseYaw, basePitch,
                        faceEast ? bx + 1 - GRID_INSET : bx + GRID_INSET, by + v, bz + u);
            }
        }

        Collections.sort(candidates);
        for (RotationCandidate candidate : candidates) {
            MovingObjectPosition hit = rayCastBlock(REACH, candidate.yaw, candidate.pitch);
            if (hit == null) {
                continue;
            }
            BlockPos hitBlock = hit.getBlockPos();
            EnumFacing face = hit.sideHit;
            if (hitBlock.equals(cell) && hitBlock.getY() >= minY
                    && !(face == EnumFacing.DOWN && cell.getY() == minY)
                    && canPlaceOn(held, hitBlock, face)) {
                return new AimResult(hitBlock, face, candidate.yaw, candidate.pitch);
            }
        }
        return null;
    }

    private static void addCandidate(List<RotationCandidate> into, Vec3 eye, float baseYaw, float basePitch,
                                     double x, double y, double z) {
        float[] rotation = rotationsFromEye(eye, x, y, z);
        double cost = Math.abs(MathHelper.wrapAngleTo180_float(rotation[0] - baseYaw))
                + Math.abs(rotation[1] - basePitch);
        into.add(new RotationCandidate(cost, rotation[0], rotation[1]));
    }
    private AimResult sidesAim() {
        BlockPos feet = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY),
                MathHelper.floor_double(mc.thePlayer.posZ));
        BlockPos head = feet.up();
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        List<BlockPos> baseline = new ArrayList<BlockPos>();
        for (EnumFacing dir : HORIZONTALS) {
            baseline.add(feet.offset(dir));
            baseline.add(head.offset(dir));
        }
        List<BlockPos> goals = new ArrayList<BlockPos>();
        for (BlockPos pos : baseline) {
            if (BlockUtil.isReplaceable(pos) && hasAirNeighbour(pos, feet, head)) {
                goals.add(pos);
            }
        }
        if (goals.isEmpty()) {
            return null;
        }
        Vec3 enemy = closestPlayerPos(100.0);
        if (enemy != null) {
            final Vec3 target = enemy;
            Collections.sort(baseline, (a, b) -> Double.compare(centreDistanceSq(a, target), centreDistanceSq(b, target)));
            int tried = 0;
            for (int i = 0; i < baseline.size() && tried < 3; i++) {
                BlockPos pos = baseline.get(i);
                if (!BlockUtil.isReplaceable(pos) || !hasAirNeighbour(pos, feet, head)) {
                    continue;
                }
                AimResult result = this.bestForGoals(Collections.singletonList(pos), eye);
                if (result != null) {
                    return result;
                }
                tried++;
            }
        }
        AimResult result = this.bestForGoals(goals, eye);
        if (result != null) {
            return result;
        }
        List<BlockPos> frontier = new ArrayList<BlockPos>(goals);
        HashSet<Long> seen = new HashSet<Long>();
        for (BlockPos pos : frontier) {
            seen.add(pos.toLong());
        }
        for (int iteration = 0; iteration < 5 && !frontier.isEmpty(); iteration++) {
            List<BlockPos> layer = new ArrayList<BlockPos>();
            for (BlockPos pos : frontier) {
                for (EnumFacing face : EnumFacing.values()) {
                    BlockPos next = pos.offset(face);
                    if (BlockUtil.isReplaceable(next) && seen.add(next.toLong())) {
                        layer.add(next);
                    }
                }
            }
            if (!layer.isEmpty()) {
                AimResult layerResult = this.bestForGoals(layer, eye);
                if (layerResult != null) {
                    return layerResult;
                }
            }
            frontier = layer;
        }
        return null;
    }
    private AimResult bestForGoals(List<BlockPos> goals, Vec3 eye) {
        if (goals == null || goals.isEmpty() || this.plannedSlot < 0 || this.plannedSlot > 8) {
            return null;
        }
        ItemStack held = mc.thePlayer.inventory.mainInventory[this.plannedSlot];
        float currentYaw = this.aimBaseYaw();
        float currentPitch = this.aimBasePitch();
        MovingObjectPosition now = rayCastBlock(REACH, currentYaw, currentPitch);
        if (now != null) {
            BlockPos support = now.getBlockPos();
            EnumFacing face = now.sideHit;
            if (!BlockUtil.isReplaceable(support) && canPlaceOn(held, support, face)) {
                for (BlockPos goal : goals) {
                    AimResult ok = tryPlacement(currentYaw, currentPitch, support, face, goal);
                    if (ok != null) {
                        return ok;
                    }
                }
            }
        }
        double jitter = GRID_STEP * 0.1;
        double insetTop = 1 - GRID_INSET - 1.0E-3;
        double insetBottom = GRID_INSET + 1.0E-3;
        List<PlacementCandidate> candidates = new ArrayList<PlacementCandidate>();
        for (BlockPos goal : goals) {
            for (SupportOffset support : SUPPORTS) {
                BlockPos block = new BlockPos(goal.getX() + support.dx, goal.getY() + support.dy, goal.getZ() + support.dz);
                if (BlockUtil.isReplaceable(block) || !canPlaceOn(held, block, support.face)) {
                    continue;
                }
                double sx = block.getX();
                double sy = block.getY();
                double sz = block.getZ();
                for (int row = 0; row <= GRID_N; row++) {
                    boolean leftToRight = (row & 1) == 0;
                    double v = clamp01(row * GRID_STEP + jitter(jitter));
                    for (int col = 0; col <= GRID_N; col++) {
                        double raw = clamp01(col * GRID_STEP + jitter(jitter));
                        double u = leftToRight ? raw : 1.0 - raw;
                        double px;
                        double py;
                        double pz;
                        if (support.dy != 0) {
                            px = sx + u;
                            pz = sz + v;
                            py = sy + (support.dy < 0 ? insetTop : insetBottom);
                        } else if (support.dz != 0) {
                            px = sx + u;
                            py = sy + v;
                            pz = sz + (support.dz < 0 ? insetTop : insetBottom);
                        } else {
                            pz = sz + u;
                            py = sy + v;
                            px = sx + (support.dx < 0 ? insetTop : insetBottom);
                        }
                        float[] rotation = rotationsFromEye(eye, px, py, pz);
                        float dYaw = Math.abs(MathHelper.wrapAngleTo180_float(rotation[0] - currentYaw));
                        float dPitch = Math.abs(rotation[1] - currentPitch);

                        if (dYaw < 0.1F && dPitch < 0.1F) {
                            continue;
                        }
                        candidates.add(new PlacementCandidate(dYaw + dPitch, rotation[0], rotation[1],
                                block, support.face, goal));
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        Collections.sort(candidates);
        for (PlacementCandidate candidate : candidates) {
            AimResult ok = tryPlacement(candidate.yaw, candidate.pitch, candidate.support, candidate.face, candidate.goal);
            if (ok != null) {
                return ok;
            }
        }
        return null;
    }
    private static AimResult tryPlacement(float yaw, float pitch, BlockPos support, EnumFacing face, BlockPos goal) {
        MovingObjectPosition hit = rayCastBlock(REACH, yaw, pitch);
        if (hit == null) {
            return null;
        }
        BlockPos hitBlock = hit.getBlockPos();
        EnumFacing hitFace = hit.sideHit;
        if (!hitBlock.equals(support) || hitFace != face || !hitBlock.offset(hitFace).equals(goal)) {
            return null;
        }
        return new AimResult(hitBlock, hitFace, yaw, pitch);
    }
    private boolean isDirectAdjacent(BlockPos pos) {
        BlockPos feet = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY),
                MathHelper.floor_double(mc.thePlayer.posZ));
        int dx = pos.getX() - feet.getX();
        int dy = pos.getY() - feet.getY();
        int dz = pos.getZ() - feet.getZ();
        if (dx == 0 && dz == 0 && dy == 2) {
            return true;
        }
        return (dy == 0 || dy == 1)
                && (Math.abs(dx) == 1 && dz == 0 || Math.abs(dz) == 1 && dx == 0);
    }
    private static float[] smoothRotation(float baseYaw, float basePitch, float targetYaw, float targetPitch,
                                          int speed, float randomizationPercent) {
        if (speed <= 0) {
            return new float[]{baseYaw, clampPitch(basePitch)};
        }
        if (speed >= 30) {
            return new float[]{targetYaw, clampPitch(targetPitch)};
        }
        float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - baseYaw);
        float deltaPitch = targetPitch - basePitch;
        float magnitude = MathHelper.sqrt_float(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (magnitude < 0.001F) {
            return new float[]{targetYaw, clampPitch(targetPitch)};
        }
        float t = speed / 30.0F;
        float step = t * t * 180.0F;
        float range = 0.6F * (randomizationPercent / 100.0F);
        float multiplier = range <= 0.001F ? 1.0F : 1.0F - range / 2.0F + (float) (Math.random() * range);
        step *= multiplier;
        float proximity = (float) Math.pow(Math.min(1.0F, magnitude / 180.0F), 0.7);
        float maxSlowdown = randomizationPercent / 100.0F;
        step *= Math.max(0.8F, 1.0F - maxSlowdown * (1.0F - proximity));
        float scale = Math.min(step, magnitude) / magnitude;
        return new float[]{baseYaw + deltaYaw * scale, clampPitch(basePitch + deltaPitch * scale)};
    }
    private static float clampPitch(float pitch) {
        return MathHelper.clamp_float(pitch, -90.0F, 90.0F);
    }
    private static MovingObjectPosition rayCastBlock(double distance, float yaw, float pitch) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = lookVector(yaw, pitch);
        Vec3 end = eye.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, end, false, false, false);
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK ? hit : null;
    }
    private static Vec3 lookVector(float yaw, float pitch) {
        float f = MathHelper.cos(-yaw * ((float) Math.PI / 180.0F) - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * ((float) Math.PI / 180.0F) - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * ((float) Math.PI / 180.0F));
        float f3 = MathHelper.sin(-pitch * ((float) Math.PI / 180.0F));
        return new Vec3(f1 * f2, f3, f * f2);
    }
    private static float[] rotationsFromEye(Vec3 eye, double x, double y, double z) {
        double dx = x - eye.xCoord;
        double dy = y - eye.yCoord;
        double dz = z - eye.zCoord;
        double flat = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
                (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F,
                (float) -Math.toDegrees(Math.atan2(dy, flat))
        };
    }
    private static double distanceToBox(Vec3 point, BlockPos block) {
        double cx = Math.max(block.getX(), Math.min(block.getX() + 1, point.xCoord));
        double cy = Math.max(block.getY(), Math.min(block.getY() + 1, point.yCoord));
        double cz = Math.max(block.getZ(), Math.min(block.getZ() + 1, point.zCoord));
        double dx = point.xCoord - cx;
        double dy = point.yCoord - cy;
        double dz = point.zCoord - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static float fistBreakTicks(Block block) {
        float hardness = block.getBlockHardness(mc.theWorld, null);
        if (hardness < 0.0F) {
            return Float.MAX_VALUE;
        }
        if (hardness == 0.0F) {
            return 0.0F;
        }
        return hardness * (block.getMaterial().isToolNotRequired() ? 30.0F : 100.0F);
    }
    private static boolean canPlaceOn(ItemStack stack, BlockPos pos, EnumFacing side) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }
        return ((ItemBlock) stack.getItem()).canPlaceBlockOnSide(mc.theWorld, pos, side, mc.thePlayer, stack);
    }
    private static boolean hasAirNeighbour(BlockPos pos, BlockPos... exclude) {
        for (EnumFacing face : EnumFacing.values()) {
            BlockPos next = pos.offset(face);
            if (mc.theWorld.getBlockState(next).getBlock() != Blocks.air) {
                continue;
            }
            boolean excluded = false;
            for (BlockPos ex : exclude) {
                if (next.equals(ex)) {
                    excluded = true;
                    break;
                }
            }
            if (!excluded) {
                return true;
            }
        }
        return false;
    }
    private static Vec3 closestPlayerPos(double maxDistanceSq) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return null;
        }
        Vec3 closest = null;
        double best = maxDistanceSq;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer) {
                continue;
            }
            if (mc.getNetHandler() == null || mc.getNetHandler().getPlayerInfo(player.getUniqueID()) == null) {
                continue;
            }
            double dx = player.posX - mc.thePlayer.posX;
            double dy = player.posY - mc.thePlayer.posY;
            double dz = player.posZ - mc.thePlayer.posZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < best) {
                best = distance;
                closest = new Vec3(player.posX, player.posY, player.posZ);
            }
        }
        return closest;
    }
    private static double centreDistanceSq(BlockPos pos, Vec3 to) {
        double dx = pos.getX() + 0.5 - to.xCoord;
        double dy = pos.getY() + 0.5 - to.yCoord;
        double dz = pos.getZ() + 0.5 - to.zCoord;
        return dx * dx + dy * dy + dz * dz;
    }
    private static float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }
    private static float quadInOut(float t) {
        return t < 0.5F ? 2.0F * t * t : -1.0F + (4.0F - 2.0F * t) * t;
    }
    private static double clamp01(double value) {
        return value < 0 ? 0 : Math.min(value, 1);
    }
    private static double jitter(double range) {
        return range > 0 ? (Math.random() * 2 - 1) * range : 0;
    }
    private static void drawCircle(float x, float y, float radius, int segments, float width,
                                   float r, float g, float b, float a) {
        beginLines(width);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i <= segments; i++) {
            double theta = 2 * Math.PI * i / segments;
            GL11.glVertex2f((float) (radius * Math.cos(theta)) + x, (float) (radius * Math.sin(theta)) + y);
        }
        GL11.glEnd();
        endLines();
    }
    private static void drawArc(float x, float y, float radius, float startAngle, float endAngle,
                                float width, int colour) {
        beginLines(width);
        GL11.glColor4f((colour >> 16 & 0xFF) / 255.0F, (colour >> 8 & 0xFF) / 255.0F,
                (colour & 0xFF) / 255.0F, (colour >> 24 & 0xFF) / 255.0F);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (float angle = startAngle; angle <= endAngle; angle += 1.0F) {
            double theta = Math.toRadians(angle + 180.0);
            GL11.glVertex2f((float) (radius * Math.cos(theta)) + x, (float) (radius * Math.sin(theta)) + y);
        }
        GL11.glEnd();
        endLines();
    }
    private static void beginLines(float width) {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(width);
    }

    private static void endLines() {
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glLineWidth(1.0F);
        GL11.glPopMatrix();
    }
    private static final class SupportOffset {
        final int dx;
        final int dy;
        final int dz;
        final EnumFacing face;
        SupportOffset(int dx, int dy, int dz, EnumFacing face) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.face = face;
        }
    }
    private static final class BlockCandidate implements Comparable<BlockCandidate> {
        final double distance;
        final BlockPos pos;
        BlockCandidate(double distance, BlockPos pos) {
            this.distance = distance;
            this.pos = pos;
        }
        @Override
        public int compareTo(BlockCandidate other) {
            return Double.compare(this.distance, other.distance);
        }
    }
    private static final class RotationCandidate implements Comparable<RotationCandidate> {
        final double cost;
        final float yaw;
        final float pitch;
        RotationCandidate(double cost, float yaw, float pitch) {
            this.cost = cost;
            this.yaw = yaw;
            this.pitch = pitch;
        }
        @Override
        public int compareTo(RotationCandidate other) {
            return Double.compare(this.cost, other.cost);
        }
    }
    private static final class PlacementCandidate implements Comparable<PlacementCandidate> {
        final double cost;
        final float yaw;
        final float pitch;
        final BlockPos support;
        final BlockPos goal;
        final EnumFacing face;
        PlacementCandidate(double cost, float yaw, float pitch, BlockPos support, EnumFacing face, BlockPos goal) {
            this.cost = cost;
            this.yaw = yaw;
            this.pitch = pitch;
            this.support = support;
            this.face = face;
            this.goal = goal;
        }
        @Override
        public int compareTo(PlacementCandidate other) {
            return Double.compare(this.cost, other.cost);
        }
    }
    private static final class AimResult {
        final BlockPos support;
        final EnumFacing face;
        final float yaw;
        final float pitch;
        AimResult(BlockPos support, EnumFacing face, float yaw, float pitch) {
            this.support = support;
            this.face = face;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
