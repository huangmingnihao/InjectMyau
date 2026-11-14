package myau.module.modules;

import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.base.CaseFormat;

import myau.Myau;
import myau.util.MeowtilsCompat;
import myau.access.AccessorMinecraft;
import myau.access.AccessorPlayerControllerMP;
import myau.enums.BlinkModules;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.AttackEvent;
import myau.events.CancelUseEvent;
import myau.events.HitBlockEvent;
import myau.events.LeftClickMouseEvent;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.RightClickMouseEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.management.RotationState;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.ItemUtil;
import myau.util.KeyBindUtil;
import myau.util.MoveUtil;
import myau.util.PacketUtil;
import myau.util.PlayerUtil;
import myau.util.RandomUtil;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Timer;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings.GameType;

public class KillAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode;
    public final ModeProperty sort;
    public ModeProperty autoBlock;
    public ModeProperty hypixelMode;
    public ModeProperty lagClass;
    public ModeProperty tickMode;
    public ModeProperty fullMode;
    public ModeProperty swapMode;
    private final BooleanProperty noStop = new BooleanProperty("NoSwap",true,this::isOldHypixel);
    private final BooleanProperty test = new BooleanProperty("MoreAttack",false,this::isOldHypixel);
    private final IntProperty moreAttackDelay = new IntProperty("MoreAttackDelay",1,0,3,() -> this.isOldHypixel() && test.getValue());
    public final IntProperty maxTick = new IntProperty("MaxTick",3,1,5,this::isHypixelCustom);
    private final IntProperty startBlinkTick = new IntProperty("StartBlinkTick",0,1,5,this::isHypixelCustom);
    private final IntProperty stopBlinkTick = new IntProperty("StopBlinkTick",2,1,5,this::isHypixelCustom);
    private final IntProperty swapTick = new IntProperty("SwapTick",2,1,5,this::isHypixelCustom);
    private final IntProperty switchBackTick = new IntProperty("SwitchBackTick",2,1,5,this::isHypixelCustom);
    private final IntProperty stopBlockTick = new IntProperty("StopBlockTick",2,1,5,this::isHypixelCustom);
    public final IntProperty attackTick = new IntProperty("AttackTick",0,1,5,this::isHypixelCustom);
    private final IntProperty startBlockTick = new IntProperty("StartBlockTick",0,1,5,this::isHypixelCustom);
    private final BooleanProperty postStartBlock = new BooleanProperty("PostBlock",false,this::isHypixelCustom);
    private final BooleanProperty alwaysRenderBlocking = new BooleanProperty("AlwaysRenderBlocking",true,this::isLag);
    private final BooleanProperty c09Instead = new BooleanProperty("C09Instead",true,this::isLag3Tick);
    private final BooleanProperty fullC09 = new BooleanProperty("FullC09(Will Cause Damage Less)",false,() -> isLag4Tick() | isLag5Tick());
    private final IntProperty startHurtTime = new IntProperty("StartHurtTime", 6, 1, 10, this::isPredict);
    private final IntProperty holdBlockTick = new IntProperty("HoldBlockTick", 2, 0, 6, this::isPredict);
    public final BooleanProperty autoBlockRequirePress;
    public final IntProperty autoBlockCPS;
    public final FloatProperty autoBlockRange;

    public final FloatProperty swingRange;
    public final FloatProperty attackRange;
    public final IntProperty fov;
    public final IntProperty minCPS;
    public final IntProperty maxCPS;
    public final IntProperty switchDelay;
    public final ModeProperty rotations;
    public final ModeProperty moveFix;
    public ModeProperty rotationMode;
    public final PercentProperty smoothing;
    public final IntProperty angleStep;
    public final BooleanProperty throughWalls;
    public final BooleanProperty requirePress;
    public final BooleanProperty allowMining;
    public final BooleanProperty allowPlayerBlocking;
    public final BooleanProperty weaponsOnly;
    public final BooleanProperty allowTools;
    public final BooleanProperty inventoryCheck;
    public final BooleanProperty lowTimerCheck;
    public final BooleanProperty botCheck;
    public final BooleanProperty players;
    public final BooleanProperty bosses;
    public final BooleanProperty mobs;
    public final BooleanProperty animals;
    public final BooleanProperty golems;
    public final BooleanProperty silverfish;
    public final BooleanProperty teams;

    public final BooleanProperty targetBox = new BooleanProperty("Target Box", true);
    public final ColorProperty targetBoxColor = new ColorProperty("Target Box Color", 0xFFFFFF, this.targetBox::getValue);
    public final PercentProperty targetBoxOpacity = new PercentProperty("Target Box Opacity", 20, this.targetBox::getValue);
    public final BooleanProperty targetBoxHurt = new BooleanProperty("Target Box Hurt", false, this.targetBox::getValue);
    public final BooleanProperty targetBoxThroughWalls = new BooleanProperty("Target Box Through Walls", true, this.targetBox::getValue);

    private final TimerUtil timer = new TimerUtil();
    private AttackData target = null;
    private int switchTick = 0;
    private boolean hitRegistered = false;
    public boolean blockingState = false;
    public boolean isBlocking = false;
    private boolean fakeBlockState = false;
    private long attackDelayMS = 0L;
    public int blockTick = 0;

    // ==================================================================

    // ==================================================================
    private static final int SKIP_CYCLE_TICKS = 4;
    private static final int SKIP_BURST_TICKS = 2;
    private static final int SKIP_BLOCK_TICKS = SKIP_CYCLE_TICKS - SKIP_BURST_TICKS - 1;
    private static final float SKIP_BURST_SPEED = 2.0F;
    private final myau.util.TimerSpeedLease skipAutoBlockTimer = new myau.util.TimerSpeedLease();

    private boolean skipAutoBlockBoostPending;
    private int skipAutoBlockAttackTicksRemaining;
    private volatile boolean skipAutoBlockSequence;
    private long skipAutoBlockReleaseMovement;
    private long skipAutoBlockFreezeRequest;
    private long skipAutoBlockClockNanos;
    private EntityPlayerSP skipAutoBlockPlayer;
    private WorldClient skipAutoBlockWorld;
    private boolean swapped = false;
    private boolean postBlock = false;
    private boolean postBlink = false;
    private boolean postBlinkReset = false;
    private boolean postSwap = false;
    private boolean predictBlocking = false;
    private int holdTicks = 0;
    private short abClickAction = 0;
    private int testAttackTick = 0;

    public KillAura(){
        super("KillAura", false);
        this.mode = new ModeProperty("Mode", 0, new String[]{"Single", "Switch"});
        this.sort = new ModeProperty("Sort", 0, new String[]{"Distance", "Health", "Hurt Time", "FOV"});

        this.autoBlock = new ModeProperty(
                "AutoBlock", 0, new String[]{"None", "Vanilla", "Hypixel", "Legit", "Fake"}
        );
        this.hypixelMode = new ModeProperty(
                "HypixelMode", 0,
                new String[]{"OldHypixel", "Without NoSlow", "Custom", "Lag", "Predict"},
                () -> "Hypixel".equals(this.autoBlock.getModeString())
        );
        this.lagClass = new ModeProperty(
                "LagClass", 0,
                new String[]{"Tick", "Combo", "Full", "Swap", "Stop", "Click", "SkipTick"},
                this::isLag
        );
        this.tickMode = new ModeProperty(
                "TickMode", 1, new String[]{"2Tick", "3Tick", "4Tick", "5Tick", "6Tick"},
                () -> this.isLag() && "Tick".equals(this.lagClass.getModeString())
        );
        this.fullMode = new ModeProperty(
                "FullMode", 0, new String[]{"3TickFull", "4TickFull"},
                () -> this.isLag() && "Full".equals(this.lagClass.getModeString())
        );
        this.swapMode = new ModeProperty(
                "SwapMode", 0, new String[]{"Swap", "TestPostSwap", "Swap+"},
                () -> this.isLag() && "Swap".equals(this.lagClass.getModeString())
        );
        this.autoBlockRequirePress = new BooleanProperty("AutoBlock Require Press", false);
        this.autoBlockCPS = new IntProperty("AutoBlock Aps", 10, 1, 20);
        this.autoBlockRange = new FloatProperty("AutoBlock Range", 6.0F, 3.0F, 8.0F);
        this.swingRange = new FloatProperty("Swing Range", 3.5F, 3.0F, 6.0F);
        this.attackRange = new FloatProperty("Attack Range", 3.0F, 3.0F, 6.0F);
        this.fov = new IntProperty("Fov", 360, 30, 360);
        this.minCPS = new IntProperty("Min Aps", 14, 1, 20);
        this.maxCPS = new IntProperty("Max Aps", 14, 1, 20);
        this.switchDelay = new IntProperty("Switch Delay", 150, 0, 1000);
        this.rotations = new ModeProperty("Rotations", 2, new String[]{"None", "Legit", "Silent", "Lock View"});
        this.moveFix = new ModeProperty("Move Fix", 1, new String[]{"None", "Silent", "Strict"});
        this.rotationMode = new ModeProperty("RotationMode", 2, new String[]{"Normal", "Nearest", "Smart"});
        this.smoothing = new PercentProperty("Smoothing", 0);
        this.angleStep = new IntProperty("Angle Step", 90, 30, 180);
        this.throughWalls = new BooleanProperty("Through Walls", true);
        this.requirePress = new BooleanProperty("Require Press", false);
        this.allowPlayerBlocking = new BooleanProperty("Allow Player Blocking", true);
        this.allowMining = new BooleanProperty("Allow Mining", false);
        this.weaponsOnly = new BooleanProperty("Weapons Only", false);
        this.allowTools = new BooleanProperty("Allow Tools", false, this.weaponsOnly::getValue);
        this.inventoryCheck = new BooleanProperty("Inventory Check", true);
        this.lowTimerCheck = new BooleanProperty("Low Timer Check", true);
        this.botCheck = new BooleanProperty("Bot Check", true);
        this.players = new BooleanProperty("Players", true);
        this.bosses = new BooleanProperty("Bosses", false);
        this.mobs = new BooleanProperty("Mobs", false);
        this.animals = new BooleanProperty("Animals", false);
        this.golems = new BooleanProperty("Golems", false);
        this.silverfish = new BooleanProperty("Silverfish", false);
        this.teams = new BooleanProperty("Teams", true);
    }
    private long getAttackDelay() {



        return this.isBlocking || this.isLagSkipTick()
                ? (long) (1000.0F / this.autoBlockCPS.getValue())
                : 1000L / RandomUtil.nextLong(this.minCPS.getValue(), this.maxCPS.getValue());
    }

    private boolean performAttack(float yaw, float pitch) {

        if (Velocity.shouldCancelKillAuraAttack()) {
            return false;
        }

        if (this.isLagSkipTick()
                && (this.attackDelayMS > 0L
                || (Myau.playerStateManager != null
                && Myau.playerStateManager.hasInputAction()))) {
            return false;
        }
        if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
            if (this.isPlayerBlocking() && this.autoBlock.getValue() != 1) {
                return false;
            } else if (this.attackDelayMS > 0L) {
                return false;
            }
            else {
                Timer timer = AccessorMinecraft.getTimer(mc);
                if (this.lowTimerCheck.getValue() && timer != null && timer.timerSpeed < 1F){
                    return false;
                }
                else {
                    this.attackDelayMS = this.attackDelayMS + this.getAttackDelay();
                    mc.thePlayer.swingItem();
                    if ((this.rotations.getValue() != 0 || !this.isBoxInAttackRange(this.target.getBox()))
                            && RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, this.attackRange.getValue()) == null) {
                        return false;
                    } else {
                        AttackEvent event = new AttackEvent(this.target.getEntity());
                        EventManager.call(event);
                        MeowtilsCompat.onAttack(mc.thePlayer, this.target.getEntity());
                        AccessorPlayerControllerMP.callSyncCurrentPlayItem(mc.playerController);
                        PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.ATTACK));
                        if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
                            PlayerUtil.attackEntity(this.target.getEntity());
                        }
                        this.hitRegistered = true;
                        return true;
                    }
                }
            }
        } else {
            return false;
        }
    }

    private void sendUseItem() {
        AccessorPlayerControllerMP.callSyncCurrentPlayItem(mc.playerController);
        this.startBlock(mc.thePlayer.getHeldItem());
    }
    private void startBlock(ItemStack itemStack) {
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
        mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
        this.blockingState = true;
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        this.blockingState = false;
    }

    private void interactAttack(float yaw, float pitch) {
        if (this.target != null) {
            MovingObjectPosition mop = RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, 8.0);
            if (mop != null) {
                AccessorPlayerControllerMP.callSyncCurrentPlayItem(mc.playerController);
                PacketUtil.sendPacket(
                        new C02PacketUseEntity(
                                this.target.getEntity(),
                                new Vec3(mop.hitVec.xCoord - this.target.getX(), mop.hitVec.yCoord - this.target.getY(), mop.hitVec.zCoord - this.target.getZ())
                        )
                );
                PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.INTERACT));
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                this.blockingState = true;
            }
        }
    }
    private boolean isNormalTargetVisible(AxisAlignedBB box) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
        double minTargetY = box.minY + 0.05 * (box.maxY - box.minY);
        double maxTargetY = box.minY + 0.75 * (box.maxY - box.minY);
        double targetY = MathHelper.clamp_double(eyePos.yCoord, minTargetY, maxTargetY);
        double targetX = (box.minX + box.maxX) / 2.0;
        double targetZ = (box.minZ + box.maxZ) / 2.0;
        Vec3 targetPoint = new Vec3(targetX, targetY, targetZ);
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyePos, targetPoint, false, true, false);
        return mop == null;
    }
    private boolean canAttack() {
        if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) {
            return false;
        } else if (!(Boolean) this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (AccessorPlayerControllerMP.getIsHittingBlock(mc.playerController)) {
                return false;
            } else if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) {
                return false;
            } else {
                AutoHeal autoHeal = (AutoHeal) Myau.moduleManager.modules.get(AutoHeal.class);
                if (autoHeal.isEnabled() && autoHeal.isSwitching()) {
                    return false;
                } else {
                    BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
                    if (bedNuker.isEnabled() && bedNuker.isReady()) {
                        return false;
                    } else if (Myau.moduleManager.modules.get(Scaffold.class).isEnabled()) {
                        return false;
                    } else if (isBlockingIn()) {
                        return false;
                    } else if (this.requirePress.getValue()) {
                        return PlayerUtil.isAttacking();
                    } else {
                        return !this.allowMining.getValue() || !mc.objectMouseOver.typeOfHit.equals(MovingObjectType.BLOCK) || !PlayerUtil.isAttacking();
                    }
                }
            }
        } else {
            return false;
        }
    }

    private static boolean isBlockingIn() {
        BlockIn blockIn = (BlockIn) Myau.moduleManager.modules.get(BlockIn.class);
        return blockIn != null && blockIn.isEnabled() && blockIn.isPlacing();
    }

    private boolean canAutoBlock() {
        if (this.isAutoBlockSuspended()) {
            return false;
        }
        if (!ItemUtil.isHoldingSword()) {
            return false;
        } else {
            return !this.autoBlockRequirePress.getValue() || PlayerUtil.isUsingItem();
        }
    }

    private boolean hasValidTarget() {
        return mc.theWorld
                .loadedEntityList
                .stream()
                .anyMatch(
                        entity -> entity instanceof EntityLivingBase
                                && this.isValidTarget((EntityLivingBase) entity)
                                && this.isInBlockRange((EntityLivingBase) entity)
                );
    }


    public boolean isVelocityRangeTarget(EntityLivingBase target) {
        return target != null && this.isValidTarget(target);
    }





    private boolean isAutoBlockSuspended() {
        if (Myau.moduleManager == null) {
            return false;
        }
        LeaderStuck stuck = (LeaderStuck) Myau.moduleManager.getModule(LeaderStuck.class);
        if (stuck != null && stuck.isStuckActive()) {
            return true;
        }
        return Velocity.isSkipTickCombatActive();
    }

    private boolean isValidTarget(EntityLivingBase entityLivingBase) {
        if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) {
            return false;
        } else if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.thePlayer.ridingEntity) {
            if (entityLivingBase == mc.getRenderViewEntity() || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityLivingBase.deathTime > 0) {
                return false;
            } else if (RotationUtil.angleToEntity(entityLivingBase) > this.fov.getValue().floatValue()) {
                return false;
            } else if (!this.throughWalls.getValue() && RotationUtil.rayTrace(entityLivingBase) != null) {
                return false;
            } else if (entityLivingBase instanceof EntityOtherPlayerMP) {
                if (!this.players.getValue()) {
                    return false;
                } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                    return false;
                } else {
                    return (!this.teams.getValue() || !TeamUtil.isSameTeam((EntityPlayer) entityLivingBase)) && (!this.botCheck.getValue() || !TeamUtil.isBot((EntityPlayer) entityLivingBase));
                }
            } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
                return this.bosses.getValue();
            } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
                if (entityLivingBase instanceof EntityAnimal
                        || entityLivingBase instanceof EntityBat
                        || entityLivingBase instanceof EntitySquid
                        || entityLivingBase instanceof EntityVillager) {
                    return this.animals.getValue();
                } else if (!(entityLivingBase instanceof EntityIronGolem)) {
                    return false;
                } else {
                    return this.golems.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
                }
            } else if (!(entityLivingBase instanceof EntitySilverfish)) {
                return this.mobs.getValue();
            } else {
                return this.silverfish.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
            }
        } else {
            return false;
        }
    }

    private boolean isInRange(EntityLivingBase entityLivingBase) {
        return this.isInBlockRange(entityLivingBase) || this.isInSwingRange(entityLivingBase) || this.isInAttackRange(entityLivingBase);
    }

    private boolean isInBlockRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.autoBlockRange.getValue();
    }

    private boolean isInSwingRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.swingRange.getValue();
    }

    private boolean isBoxInSwingRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.swingRange.getValue();
    }

    private boolean isInAttackRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.attackRange.getValue();
    }

    private boolean isBoxInAttackRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.attackRange.getValue();
    }

    private boolean isPlayerTarget(EntityLivingBase entityLivingBase) {
        return entityLivingBase instanceof EntityPlayer && TeamUtil.isTarget((EntityPlayer) entityLivingBase);
    }

    private static int randomSwapSlot() {
        int current = mc.thePlayer.inventory.currentItem;
        int slot = ThreadLocalRandom.current().nextInt(9);
        while (slot == current) {
            slot = ThreadLocalRandom.current().nextInt(9);
        }
        return slot;
    }

    private static int altSlot(int handle) {
        return handle % 8 + 1;
    }

    public EntityLivingBase getTarget() {
        return this.target != null ? this.target.getEntity() : null;
    }

    public boolean isAttackAllowed() {
        Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled()) {
            return false;
        } else if (!this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            return !this.requirePress.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
        } else {
            return false;
        }
    }

    public boolean isOldHypixel() {
        return this.isHypixelMode("OldHypixel");
    }

    public boolean isHypixelWithoutNoSlow() {
        return this.isHypixelMode("Without NoSlow");
    }

    public boolean isHypixelCustom() {
        return this.isHypixelMode("Custom");
    }

    public boolean isLag() {
        return this.isHypixelMode("Lag");
    }

    public boolean isPredict() {
        return this.isHypixelMode("Predict");
    }

    public boolean isLag3Tick() {
        return this.isLag() && "3Tick".equals(this.getLagModeName());
    }

    public boolean isLag4Tick() {
        return this.isLag() && "4Tick".equals(this.getLagModeName());
    }

    public boolean isLag5Tick() {
        if (!this.isLag()) {
            return false;
        }
        switch (this.getLagModeName()) {
            case "Combo":
            case "5Tick":
            case "6Tick":
            case "3TickFull":
            case "4TickFull":
            case "Swap":
            case "Swap+":
                return true;
            default:
                return false;
        }
    }

    private boolean isHypixelMode(String name) {
        return "Hypixel".equals(this.autoBlock.getModeString())
                && name.equals(this.hypixelMode.getModeString());
    }






    public boolean velocityCanReduce(int phase, int tick) {
        switch (this.autoBlock.getModeString()) {
            case "None":
            case "Vanilla":
            case "Fake":
                return true;
            case "Hypixel":
                switch (this.hypixelMode.getModeString()) {
                    case "OldHypixel":
                    case "Without NoSlow":
                        return phase == 2 ? tick == 2 : tick == 0;
                    case "Custom": {
                        int maxT = Math.max(1, this.maxTick.getValue() - 1);
                        switch (phase) {
                            case 0: return tick == this.attackTick.getValue();
                            case 1: return tick == this.attackTick.getValue() % maxT;
                            default: return tick == (this.attackTick.getValue() - 2 + maxT) % maxT;
                        }
                    }
                    case "Lag":
                        switch (this.getLagModeName()) {
                            case "2Tick": return phase == 2 ? tick == 1 : tick == 0;
                            case "3Tick": return phase == 2 ? tick == 2
                                    : phase == 1 ? tick == 0 : (tick == 0 || tick == 2);
                            case "4Tick": return phase == 2 ? tick == 3
                                    : phase == 1 ? tick == 0 : (tick == 0 || tick == 3);
                            case "Combo": return phase == 2 ? tick == 4
                                    : phase == 1 ? tick == 0 : (tick == 0 || tick == 2 || tick == 4);
                            case "5Tick": return phase == 2 ? tick == 4
                                    : phase == 1 ? tick == 0 : (tick == 0 || tick == 4);
                            case "6Tick": return phase == 2 ? tick == 5
                                    : phase == 1 ? tick == 0 : (tick == 0 || tick == 5);
                            case "3TickFull": return phase == 2 ? tick == 2
                                    : phase == 1 ? tick == 0 : (tick == 0 || tick == 2);
                            case "4TickFull": return phase == 2 ? tick == 3
                                    : phase == 1 ? tick == 0 : (tick == 0 || tick == 3);
                            case "Swap": return phase == 2 ? tick == 3
                                    : phase == 1 ? tick == 0 : (tick == 0 || tick == 3);
                            case "TestPostSwap": return phase == 2 ? tick == 2 : tick == 0;
                            case "Swap+": return phase == 2 ? tick == 3
                                    : phase == 1 ? tick == 0 : (tick == 0 || tick == 3);
                            case "Stop": return phase == 2 ? tick == 4
                                    : phase == 1 ? tick == 0 : (tick == 0 || tick == 4);
                            case "Click": return phase == 2 ? tick == 2 : tick == 0;
                            default: return false;
                        }
                    case "Predict":
                        return true;
                    default:
                        return false;
                }
            case "Legit":
                return phase == 2 ? tick == 1 : tick == 0;
            default:
                return true;
        }
    }

    private String getLagModeName() {
        switch (this.lagClass.getModeString()) {
            case "Tick":
                return this.tickMode.getModeString();
            case "Full":
                return this.fullMode.getModeString();
            case "Swap":
                return this.swapMode.getModeString();
            default:
                return this.lagClass.getModeString();
        }
    }
    public boolean shouldAutoBlock() {
        if (this.autoBlock.getValue() <= 1 || this.autoBlock.getValue() == 4) {
            return this.hasValidTarget();
        }
        if (this.isPlayerBlocking() && this.isBlocking) {
            return !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava() && (this.autoBlock.getValue() == 2 || this.autoBlock.getValue() == 3);
        } else {
            return false;
        }
    }

    public boolean isBlocking() {
        return this.fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword();
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {

            if (this.isLagSkipTick()) {
                this.updateSkipTickAttackClock(System.nanoTime());
            }
            if (this.attackDelayMS > 0L) {
                this.attackDelayMS -= 50L;
            }
            boolean attack = this.target != null && this.canAttack();
            boolean block = attack && this.canAutoBlock();
            if (!block) {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                if (isOldHypixel() && isBlocking && Myau.moduleManager.getModule(NoSlow.class).isEnabled()) {
                    this.isBlocking = false;
                    stopBlock();
                }
                if (this.predictBlocking) {
                    if (this.isPlayerBlocking()) {
                        this.stopBlock();
                    }
                    this.predictBlocking = false;
                    this.holdTicks = 0;
                }
                if (swapped){
                    int handle = mc.thePlayer.inventory.currentItem;
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                    swapped = false;
                }
                else this.isBlocking = false;
                this.fakeBlockState = false;
                this.blockTick = 0;
                if (this.blockingState && !PlayerUtil.isUsingItem()) {
                    this.stopBlock();
                }
            }
            if (attack) {
                if (this.predictBlocking) {
                    this.holdTicks++;
                }
                AutoBlockAction action = block ? this.updateAutoBlock() : new AutoBlockAction();
                attack = action.attack;
                boolean swap = action.startBlock;
                boolean blocked = action.startBlink;
                boolean attacked = false;
                if (this.isBoxInSwingRange(this.target.getBox())) {
                    if (this.rotations.getValue() == 2 || this.rotations.getValue() == 3) {
                        AxisAlignedBB box = this.target.getBox();
                        float currentYaw = event.getYaw();
                        float currentPitch = event.getPitch();
                        float angleStep = (float) this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F);
                        float smooth = (float) this.smoothing.getValue() / 100.0F;
                        float[] rotations;
                        int mode = this.rotationMode.getValue();
                        if (mode == 1) {
                            rotations = nearestRotation(box, currentYaw, currentPitch, angleStep, smooth);
                        } else if (mode == 2) {
                            if (this.isNormalTargetVisible(box)) {
                                rotations = normalRotation(box, currentYaw, currentPitch, angleStep, smooth);
                            } else {
                                rotations = nearestRotation(box, currentYaw, currentPitch, angleStep, smooth);
                            }
                        } else {
                            rotations = normalRotation(box, currentYaw, currentPitch, angleStep, smooth);
                        }
                        if (rotations != null) {
                            event.setRotation(rotations[0], rotations[1], 1);
                        }
                        if (this.rotations.getValue() == 3) {
                            if (rotations != null) {
                                Myau.rotationManager.setRotation(rotations[0], rotations[1], 1, true);
                            }
                        }
                        if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3) {
                            if (rotations != null) {
                                event.setPervRotation(rotations[0], 1);
                            }
                        }
                    }
                    if (attack) {
                        attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
                    }
                }
                if (swap) {
                    if (attacked) {
                        this.interactAttack(event.getNewYaw(), event.getNewPitch());
                    } else {
                        if (!postBlock) this.sendUseItem();
                    }
                }
                if (blocked) {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                }

                this.finishSkipTickAutoBlock();
            }
        }
        if (event.getType() == EventType.POST && this.isEnabled()) {

            if (this.skipAutoBlockBoostPending
                    && Myau.playerStateManager != null
                    && Myau.playerStateManager.getMovementSequence() > this.skipAutoBlockReleaseMovement) {
                this.skipAutoBlockBoostPending = false;
                if (!this.isEnabled() || !this.isLagSkipTick()
                        || !this.skipAutoBlockTimer.acquire(SKIP_BURST_SPEED, SKIP_BURST_TICKS)) {
                    this.resetSkipTickAutoBlock(false);
                } else {
                    this.skipAutoBlockAttackTicksRemaining = SKIP_BURST_TICKS;
                }
            }

            if (this.isLagSkipTick()) {
                this.completeSkipTickBurstTick();
            }
            if (postBlinkReset) {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                postBlinkReset = false;
            }
            if (postBlink) {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                postBlink = false;
            }
            if (postSwap) {
                PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSwapSlot()));
                mc.getNetHandler().addToSendQueue(
                        new C17PacketCustomPayload("send", new PacketBuffer(Unpooled.buffer())));
                PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                this.stopBlock();
                postSwap = false;
            }
            if (postBlock) {
                sendUseItem();
                postBlock = false;
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    if (this.target == null
                            || !this.isValidTarget(this.target.getEntity())
                            || !this.isBoxInAttackRange(this.target.getBox())
                            || !this.isBoxInSwingRange(this.target.getBox())
                            || this.timer.hasTimeElapsed(this.switchDelay.getValue().longValue())) {
                        this.timer.reset();
                        ArrayList<EntityLivingBase> targets = new ArrayList<>();
                        for (Entity entity : mc.theWorld.loadedEntityList) {
                            if (entity instanceof EntityLivingBase
                                    && this.isValidTarget((EntityLivingBase) entity)
                                    && this.isInRange((EntityLivingBase) entity)) {
                                targets.add((EntityLivingBase) entity);
                            }
                        }
                        if (targets.isEmpty()) {
                            this.target = null;
                        } else {
                            if (targets.stream().anyMatch(this::isInSwingRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInSwingRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isInAttackRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInAttackRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isPlayerTarget)) {
                                targets.removeIf(entityLivingBase -> !this.isPlayerTarget(entityLivingBase));
                            }
                            targets.sort(
                                    (entityLivingBase1, entityLivingBase2) -> {
                                        int sortBase = 0;
                                        switch (this.sort.getValue()) {
                                            case 1:
                                                sortBase = Float.compare(TeamUtil.getHealthScore(entityLivingBase1), TeamUtil.getHealthScore(entityLivingBase2));
                                                break;
                                            case 2:
                                                sortBase = Integer.compare(entityLivingBase1.hurtResistantTime, entityLivingBase2.hurtResistantTime);
                                                break;
                                            case 3:
                                                sortBase = Float.compare(
                                                        RotationUtil.angleToEntity(entityLivingBase1),
                                                        RotationUtil.angleToEntity(entityLivingBase2)
                                                );
                                        }
                                        return sortBase != 0
                                                ? sortBase
                                                : Double.compare(RotationUtil.distanceToEntity(entityLivingBase1), RotationUtil.distanceToEntity(entityLivingBase2));
                                    }
                            );
                            if (this.mode.getValue() == 1 && this.hitRegistered) {
                                this.hitRegistered = false;
                                this.switchTick++;
                            }
                            if (this.mode.getValue() == 0 || this.switchTick >= targets.size()) {
                                this.switchTick = 0;
                            }
                            this.target = new AttackData(targets.get(this.switchTick));
                        }
                    }
                    if (this.target != null) {
                        this.target = new AttackData(this.target.getEntity());
                    }
                    break;
                case POST:
                    if (this.isPlayerBlocking() && !mc.thePlayer.isBlocking()) {
                        mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                    }
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled()) {
            if (event.getPacket() instanceof C07PacketPlayerDigging) {
                C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
                if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                    this.blockingState = false;
                }
            }
            if (event.getPacket() instanceof C09PacketHeldItemChange) {
                this.blockingState = false;
                if (this.isBlocking) {
                    mc.thePlayer.stopUsingItem();
                }
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.moveFix.getValue() == 1
                    && this.rotations.getValue() != 3
                    && RotationState.isActived()
                    && RotationState.getPriority() == 1.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.isEnabled() || !this.targetBox.getValue() || this.targetBoxOpacity.getValue() == 0
                || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        EntityLivingBase entity = this.getTarget();
        if (entity == null || !entity.isEntityAlive() || !TeamUtil.isEntityLoaded(entity)
                || !this.isAttackAllowed()) {
            return;
        }
        int color = this.targetBoxHurt.getValue() && entity.hurtTime > 0
                ? 0xFF5555 : this.targetBoxColor.getValue();
        int alpha = Math.round(this.targetBoxOpacity.getValue() * 255.0F / 100.0F);
        RenderUtil.drawEntityFilledBox(entity, event.getPartialTicks(), color, alpha,
                this.targetBoxThroughWalls.getValue());
    }
    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onCancelUse(CancelUseEvent event) {
        if (this.isBlocking && this.blockingState) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        this.target = null;
        this.switchTick = 0;
        this.hitRegistered = false;
        this.attackDelayMS = 0L;
        this.blockTick = 0;
        this.postBlock = false;
        this.postBlink = false;
        this.postBlinkReset = false;
        this.postSwap = false;
        this.predictBlocking = false;
        this.holdTicks = 0;
        this.abClickAction = 0;
    }

    @Override
    public void onDisabled() {

        this.resetSkipTickAutoBlock(true);
        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.blockingState = false;
        this.fakeBlockState = false;
        if (swapped){
            int handle = mc.thePlayer.inventory.currentItem;
            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
            swapped = false;
        }
        this.postBlock = false;
        this.postBlink = false;
        this.postBlinkReset = false;
        this.postSwap = false;
        this.predictBlocking = false;
        this.holdTicks = 0;
        if (isOldHypixel() && isBlocking && Myau.moduleManager.getModule(NoSlow.class).isEnabled()) {
            this.isBlocking = false;
            stopBlock();
        }
        else this.isBlocking = false;
    }

    @Override
    public void verifyValue(String mode) {
        if (!this.autoBlock.getName().equals(mode) && !this.autoBlockCPS.getName().equals(mode)) {
            if (this.swingRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.attackRange.setValue(this.swingRange.getValue());
                }
            } else if (this.attackRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.swingRange.setValue(this.attackRange.getValue());
                }
            } else if (this.minCPS.getName().equals(mode)) {
                if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.maxCPS.setValue(this.minCPS.getValue());
                }
            } else {
                if (this.maxCPS.getName().equals(mode) && this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.minCPS.setValue(this.maxCPS.getValue());
                }
            }
        }
    }
    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }

    private AutoBlockAction updateAutoBlock() {
        switch (this.autoBlock.getModeString()) {
            case "None":
                return updateManualAutoBlock();
            case "Vanilla":
                return updateVanillaAutoBlock();
            case "Hypixel":
                return updateHypixelAutoBlock();
            case "Legit":
                return updateLegitAutoBlock();
            case "Fake":
                return updateFakeAutoBlock();
            default:
                return new AutoBlockAction();
        }
    }

    private AutoBlockAction updateHypixelAutoBlock() {
        switch (this.hypixelMode.getModeString()) {
            case "OldHypixel":
                return updateOldHypixelAutoBlock();
            case "Without NoSlow":
                return updateHypixelWithoutNoSlowAutoBlock();
            case "Custom":
                return updateCustomHypixelAutoBlock();
            case "Lag":
                return updateLagAutoBlock();
            case "Predict":
                return updatePredictAutoBlock();
            default:
                return new AutoBlockAction();
        }
    }

    private AutoBlockAction updateLagAutoBlock() {
        switch (getLagModeName()) {
            case "2Tick":
                return updateLagTwoTickAutoBlock();
            case "3Tick":
                return updateLagThreeTickAutoBlock();
            case "4Tick":
                return updateLagFourTickAutoBlock();
            case "5Tick":
                return updateLagFiveTickAutoBlock();
            case "6Tick":
                return updateLagSixTickAutoBlock();
            case "Combo":
                return updateLagComboAutoBlock();
            case "3TickFull":
                return updateLagThreeTickFullAutoBlock();
            case "4TickFull":
                return updateLagFourTickFullAutoBlock();
            case "Swap":
                return updateLagSwapAutoBlock();
            case "TestPostSwap":
                return updateLagPostSwapAutoBlock();
            case "Swap+":
                return updateLagSwapPlusAutoBlock();
            case "Stop":
                return updateLagStopAutoBlock();
            case "Click":
                return updateLagClickAutoBlock();
            case "SkipTick":
                return this.updateLagSkipTickAutoBlock();
        }
        return new AutoBlockAction();
    }













    private AutoBlockAction updateLagSkipTickAutoBlock() {
        AutoBlockAction action = new AutoBlockAction(false, false, false);
        this.skipAutoBlockPlayer = mc.thePlayer;
        this.skipAutoBlockWorld = mc.theWorld;
        this.skipAutoBlockSequence = true;

        if (!this.hasValidTarget()) {
            if (this.isPlayerBlocking()) this.stopBlock();
            this.resetSkipTickAutoBlock(false);
            this.isBlocking = false;
            this.fakeBlockState = false;
            Velocity.extraAttacked = false;
            return action;
        }

        if (Myau.playerStateManager != null
                && !Myau.playerStateManager.digging
                && !Myau.playerStateManager.placing) {
            switch (this.blockTick) {
                case 0:
                    action.attack = !this.isPlayerBlocking();
                    action.startBlock = !this.isPlayerBlocking();



                    action.startBlink = true;
                    this.blockTick = 1;
                    break;
                case 1:
                    if (!this.isPlayerBlocking()) action.startBlock = true;


                    if (this.isPlayerBlocking()) {
                        this.requestSkipTickBlockFreeze();
                    }
                    break;
                case 2: {
                    LeaderStuck stuck = (LeaderStuck) Myau.moduleManager.getModule(LeaderStuck.class);
                    if (stuck == null
                            || !stuck.hasStartedFreezeRequest(this.skipAutoBlockFreezeRequest)) {
                        this.blockTick = 1;
                        break;
                    }
                    if (this.target == null
                            || !this.isBoxInAttackRange(this.target.getBox())) break;
                    if (this.isPlayerBlocking()) this.stopBlock();
                    this.skipAutoBlockReleaseMovement =
                            Myau.playerStateManager.getMovementSequence();
                    this.skipAutoBlockBoostPending = true;
                    this.blockTick = 3;
                    break;
                }
                case 3:
                    if (Myau.playerStateManager.getMovementSequence()
                            > this.skipAutoBlockReleaseMovement) {
                        if (this.skipAutoBlockTimer.isActive()
                                && this.skipAutoBlockAttackTicksRemaining > 0) {
                            action.attack = this.attackDelayMS <= 0L;
                            action.startBlock = this.skipAutoBlockAttackTicksRemaining == 1;
                        } else if (!this.skipAutoBlockBoostPending) {
                            action.startBlock = true;
                        }
                    }
                    break;
                default:
                    this.blockTick = 0;
            }
        }

        this.isBlocking = this.isPlayerBlocking();
        this.fakeBlockState = this.alwaysRenderBlocking.getValue();
        return action;
    }



    private int skipFreezeFailures = 0;
    private static final int SKIP_FREEZE_MAX_FAILURES = 10;

    private void requestSkipTickBlockFreeze() {
        if (!this.isEnabled()
                || !this.isLagSkipTick()
                || this.blockTick != 1
                || this.skipAutoBlockAttackTicksRemaining > 0
                || !this.isPlayerBlocking()
                || this.target == null
                || !this.isBoxInAttackRange(this.target.getBox())) return;





        if (Myau.blinkManager != null
                && Myau.blinkManager.getBlinkingModule() == BlinkModules.AUTO_BLOCK) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        }

        LeaderStuck stuck = (LeaderStuck) Myau.moduleManager.getModule(LeaderStuck.class);
        if (stuck != null && stuck.freezeForTicks(SKIP_BLOCK_TICKS, false)) {
            this.skipAutoBlockFreezeRequest = stuck.getFreezeRequestSequence();
            this.blockTick = 2;
            this.skipFreezeFailures = 0;
        } else if (++this.skipFreezeFailures >= SKIP_FREEZE_MAX_FAILURES) {


            this.skipFreezeFailures = 0;
            this.stopBlock();
            this.resetSkipTickAutoBlock(false);
            this.blockTick = 0;
        }
    }


    private void finishSkipTickAutoBlock() {
        if (!this.isLagSkipTick()) return;
        this.isBlocking = this.isPlayerBlocking();
        if (this.skipAutoBlockAttackTicksRemaining > 0) return;
        if (this.blockTick == 3
                && this.isBlocking
                && Myau.playerStateManager.getMovementSequence() > this.skipAutoBlockReleaseMovement) {
            this.blockTick = 1;
        }
        this.requestSkipTickBlockFreeze();
    }


    private void completeSkipTickBurstTick() {
        if (this.skipAutoBlockAttackTicksRemaining <= 0) return;
        if (!this.skipAutoBlockTimer.isActive()) {
            this.skipAutoBlockAttackTicksRemaining = 0;
            return;
        }
        if (--this.skipAutoBlockAttackTicksRemaining > 0) return;
        this.skipAutoBlockTimer.release();
        if (this.isEnabled() && this.isLagSkipTick()
                && this.blockTick == 3 && this.isPlayerBlocking()) {
            this.blockTick = 1;
            this.requestSkipTickBlockFreeze();
        }
    }


    private void resetSkipTickAutoBlock(boolean discardBlink) {
        this.skipAutoBlockTimer.release();
        this.skipAutoBlockBoostPending = false;
        this.skipAutoBlockAttackTicksRemaining = 0;
        this.skipAutoBlockSequence = false;
        this.skipAutoBlockPlayer = null;
        this.skipAutoBlockWorld = null;
        this.skipAutoBlockFreezeRequest = 0;
        if (this.isLagSkipTick()) {
            this.blockTick = 0;
            this.attackDelayMS = Math.max(0L, this.attackDelayMS);
        }
    }


    private void updateSkipTickAttackClock(long now) {
        if (this.skipAutoBlockClockNanos == 0L || now < this.skipAutoBlockClockNanos) {
            this.skipAutoBlockClockNanos = now;
            return;
        }
        boolean accelerated =
                this.skipAutoBlockTimer.isActive() && this.skipAutoBlockAttackTicksRemaining > 0;
        long elapsedMillis = accelerated ? 50L : (now - this.skipAutoBlockClockNanos) / 1_000_000L;
        if (elapsedMillis > 0) {
            if (accelerated) this.skipAutoBlockClockNanos = now;
            else this.skipAutoBlockClockNanos += elapsedMillis * 1_000_000L;
            long maximumDue = this.getAttackDelay() * (SKIP_BURST_TICKS - 1L);
            this.attackDelayMS = Math.max(-maximumDue, this.attackDelayMS - elapsedMillis);
        }
    }









    public boolean performVelocityAttack(float yaw, float pitch) {
        if (!Velocity.canPerformSkipTickAttack()
                || !this.isEnabled()
                || !this.canAttack()
                || this.target == null
                || !this.isValidTarget(this.target.getEntity())
                || !this.isBoxInAttackRange(this.target.getBox())) {
            return false;
        }
        if (this.rotations.getValue() != 0
                && RotationUtil.rayTrace(
                        this.target.getBox(), yaw, pitch, this.attackRange.getValue())
                == null) {
            return false;
        }
        if (!Velocity.tryClaimSkipTickAttack()) {
            return false;
        }
        this.prepareVelocityAttackWindow();
        return this.sendAttackPacketWithSwing();
    }


    public void prepareVelocityAttackWindow() {
        if (mc.thePlayer == null) {
            return;
        }
        this.resetSkipTickAutoBlock(false);
        if (Myau.blinkManager != null
                && Myau.blinkManager.getBlinkingModule() == BlinkModules.AUTO_BLOCK) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        }
        this.postBlock = false;
        this.postSwap = false;
        this.postBlink = false;
        this.postBlinkReset = false;
        if (this.isPlayerBlocking() || this.blockingState) {
            this.stopBlock();
        }
        this.isBlocking = false;
        this.blockingState = false;
        this.fakeBlockState = false;
        this.predictBlocking = false;
        this.holdTicks = 0;
        this.blockTick = 0;
    }





    private boolean sendAttackPacketWithSwing() {
        if (!Velocity.canPerformSkipTickAttack()) {
            return false;
        }
        AttackEvent event = new AttackEvent(this.target.getEntity());
        EventManager.call(event);
        if (!Velocity.canPerformSkipTickAttack()) {
            return false;
        }
        AccessorPlayerControllerMP.callSyncCurrentPlayItem(mc.playerController);
        if (!Velocity.canPerformSkipTickAttack()) {
            return false;
        }
        mc.thePlayer.swingItem();
        if (!Velocity.canPerformSkipTickAttack()) {
            return false;
        }
        if (!PacketUtil.trySendPacket(
                new C02PacketUseEntity(this.target.getEntity(), Action.ATTACK))) {
            return false;
        }
        if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
            PlayerUtil.attackEntity(this.target.getEntity());
        }
        this.hitRegistered = true;
        return true;
    }


    public boolean isLagSkipTick() {
        return this.isLag() && "SkipTick".equals(this.getLagModeName());
    }

    private AutoBlockAction updateManualAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (PlayerUtil.isUsingItem()) {
            this.isBlocking = true;
            if (!this.isPlayerBlocking()
                    && !Myau.playerStateManager.digging
                    && !Myau.playerStateManager.placing) {
                swap = true;
            }
        } else {
            this.isBlocking = false;
            if (this.isPlayerBlocking()
                    && !Myau.playerStateManager.digging
                    && !Myau.playerStateManager.placing) {
                this.stopBlock();
            }
        }
        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.fakeBlockState = false;
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateVanillaAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!this.isPlayerBlocking()
                    && !Myau.playerStateManager.digging
                    && !Myau.playerStateManager.placing) {
                swap = true;
            }
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = true;
            this.fakeBlockState = false;
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateOldHypixelAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        blocked = true;
                        this.blockTick = 1;
                        break;
                    case 1:
                        attack = false;
                        this.blockTick = 2;
                        break;
                    case 2:
                        if (this.isPlayerBlocking()) {
                            if (!noStop.getValue()) {
                                int handle = mc.thePlayer.inventory.currentItem;
                                PacketUtil.sendPacket(
                                        new C09PacketHeldItemChange(altSlot(handle)));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            }
                            this.stopBlock();
                        }
                        if (test.getValue()) {
                            if (testAttackTick >= moreAttackDelay.getValue()) {
                                testAttackTick = 0;
                            } else {
                                testAttackTick++;
                                attack = false;
                            }
                        } else {
                            attack = false;
                        }
                        this.blockTick = 0;
                        break;
                    default:
                        this.blockTick = 0;
                        break;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = true;
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
            PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSwapSlot()));
            PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateHypixelWithoutNoSlowAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        blocked = true;
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        this.blockTick = 1;
                        break;
                    case 1:
                        attack = false;
                        blockTick = 2;
                        break;
                    case 2:
                        if (this.isPlayerBlocking()) {
                            this.stopBlock();
                        }
                        if (test.getValue()) {
                            if (testAttackTick >= moreAttackDelay.getValue()) {
                                testAttackTick = 0;
                            } else {
                                testAttackTick++;
                                attack = false;
                            }
                        }
                        this.blockTick = 0;
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = true;
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateCustomHypixelAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                if (blockTick + 1 == startBlinkTick.getValue()) {
                    blocked = true;
                }
                if (blockTick + 1 != attackTick.getValue()) {
                    attack = false;
                }
                if (blockTick + 1 == startBlockTick.getValue()) {
                    if (!this.isPlayerBlocking()) {
                        swap = true;
                        if (postStartBlock.getValue()) postBlock = true;
                    }
                }
                if (blockTick + 1 == stopBlinkTick.getValue()) {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                }
                if (blockTick + 1 == swapTick.getValue()) {
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSwapSlot()));
                    swapped = true;
                }
                if (blockTick + 1 == switchBackTick.getValue()) {
                    if (swapped) {
                        PacketUtil.sendPacket(
                                new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                        swapped = false;
                    }
                }
                if (blockTick + 1 == stopBlockTick.getValue()) {
                    if (this.isPlayerBlocking()) {
                        this.stopBlock();
                    }
                }
                blockTick++;
                if (blockTick >= maxTick.getValue() - 1) {
                    blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = true;
        } else {
            if (swapped) {
                PacketUtil.sendPacket(
                        new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                swapped = false;
            }
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLagTwoTickAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        this.blockTick = 1;
                        break;
                    case 1:
                        Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                        if (this.isPlayerBlocking()) {
                            this.stopBlock();
                        }
                        attack = false;
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = alwaysRenderBlocking.getValue();
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLagThreeTickAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        blocked = true;
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        this.blockTick = 1;
                        break;
                    case 1:
                        if (this.isPlayerBlocking()) {
                            if (c09Instead.getValue()) {
                                int handle = mc.thePlayer.inventory.currentItem;
                                PacketUtil.sendPacket(
                                        new C09PacketHeldItemChange(altSlot(handle)));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            } else this.stopBlock();
                        }
                        attack = false;
                        blockTick = 2;
                        break;
                    case 2:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = alwaysRenderBlocking.getValue();
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLagFourTickAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        blocked = true;
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        this.blockTick = 1;
                        break;
                    case 1:
                        if (this.isPlayerBlocking()) {
                            if (fullC09.getValue()) {
                                int handle = mc.thePlayer.inventory.currentItem;
                                PacketUtil.sendPacket(
                                        new C09PacketHeldItemChange(altSlot(handle)));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                this.stopBlock();
                            }
                        }
                        attack = false;
                        blockTick = 2;
                        break;
                    case 2:
                        int handle = mc.thePlayer.inventory.currentItem;
                        PacketUtil.sendPacket(
                                new C09PacketHeldItemChange(altSlot(handle)));
                        PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                        this.stopBlock();
                        blockTick = 3;
                        break;
                    case 3:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = alwaysRenderBlocking.getValue();
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLagComboAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        blocked = true;
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        this.blockTick = 1;
                        break;
                    case 1:
                        if (this.isPlayerBlocking()) {
                            if (fullC09.getValue()) {
                                int handle = mc.thePlayer.inventory.currentItem;
                                PacketUtil.sendPacket(
                                        new C09PacketHeldItemChange(altSlot(handle)));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            }
                            this.stopBlock();
                        }
                        attack = false;
                        blockTick = 2;
                        break;
                    case 2:
                        blocked = true;
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        blockTick = 3;
                        break;
                    case 3:
                        if (this.isPlayerBlocking()) {
                            if (fullC09.getValue()) {
                                int handle = mc.thePlayer.inventory.currentItem;
                                PacketUtil.sendPacket(
                                        new C09PacketHeldItemChange(altSlot(handle)));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            }
                            this.stopBlock();
                        }
                        blockTick = 4;
                        break;
                    case 4:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = alwaysRenderBlocking.getValue();
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLagFiveTickAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        this.blockTick = 1;
                        break;
                    case 1:
                        Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                        if (this.isPlayerBlocking()) {
                            if (fullC09.getValue()) {
                                int handle = mc.thePlayer.inventory.currentItem;
                                PacketUtil.sendPacket(
                                        new C09PacketHeldItemChange(altSlot(handle)));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            }
                            this.stopBlock();
                        }
                        attack = false;
                        blockTick = 2;
                        break;
                    case 2:
                        if (fullC09.getValue()) {
                            int handle = mc.thePlayer.inventory.currentItem;
                            PacketUtil.sendPacket(
                                    new C09PacketHeldItemChange(altSlot(handle)));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            this.stopBlock();
                        }
                        attack = false;
                        blockTick = 3;
                        break;
                    case 3:
                        if (fullC09.getValue()) {
                            int handle = mc.thePlayer.inventory.currentItem;
                            PacketUtil.sendPacket(
                                    new C09PacketHeldItemChange(altSlot(handle)));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            this.stopBlock();
                        }
                        attack = false;
                        blockTick = 4;
                        break;
                    case 4:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = alwaysRenderBlocking.getValue();
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLagSixTickAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        this.blockTick = 1;
                        break;
                    case 1:
                        Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                        if (this.isPlayerBlocking()) {
                            if (fullC09.getValue()) {
                                int handle = mc.thePlayer.inventory.currentItem;
                                PacketUtil.sendPacket(
                                        new C09PacketHeldItemChange(altSlot(handle)));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            }
                            this.stopBlock();
                        }
                        attack = false;
                        this.blockTick = 2;
                        break;
                    case 2:
                        if (fullC09.getValue()) {
                            int handle = mc.thePlayer.inventory.currentItem;
                            PacketUtil.sendPacket(
                                    new C09PacketHeldItemChange(altSlot(handle)));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            this.stopBlock();
                        }
                        attack = false;
                        blockTick = 3;
                        break;
                    case 3:
                        if (fullC09.getValue()) {
                            int handle = mc.thePlayer.inventory.currentItem;
                            PacketUtil.sendPacket(
                                    new C09PacketHeldItemChange(altSlot(handle)));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            this.stopBlock();
                        }
                        attack = false;
                        blockTick = 4;
                        break;
                    case 4:
                        if (fullC09.getValue()) {
                            int handle = mc.thePlayer.inventory.currentItem;
                            PacketUtil.sendPacket(
                                    new C09PacketHeldItemChange(altSlot(handle)));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            this.stopBlock();
                        }
                        attack = false;
                        blockTick = 5;
                        break;
                    case 5:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = alwaysRenderBlocking.getValue();
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLagThreeTickFullAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        postBlink = true;
                        this.blockTick = 1;
                        break;
                    case 1:
                        Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                        if (this.isPlayerBlocking()) {
                            if (fullC09.getValue()) {
                                int handle = mc.thePlayer.inventory.currentItem;
                                PacketUtil.sendPacket(
                                        new C09PacketHeldItemChange(altSlot(handle)));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            }
                            this.stopBlock();
                        }
                        attack = false;
                        this.blockTick = 2;
                        break;
                    case 2:
                        if (fullC09.getValue()) {
                            int handle = mc.thePlayer.inventory.currentItem;
                            PacketUtil.sendPacket(
                                    new C09PacketHeldItemChange(altSlot(handle)));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            this.stopBlock();
                        }
                        attack = false;
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = alwaysRenderBlocking.getValue();
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLagFourTickFullAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        postBlink = true;
                        this.blockTick = 1;
                        break;
                    case 1:
                        Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                        if (this.isPlayerBlocking()) {
                            if (fullC09.getValue()) {
                                int handle = mc.thePlayer.inventory.currentItem;
                                PacketUtil.sendPacket(
                                        new C09PacketHeldItemChange(altSlot(handle)));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            }
                            this.stopBlock();
                        }
                        attack = false;
                        this.blockTick = 2;
                        break;
                    case 2:
                        if (fullC09.getValue()) {
                            int handle = mc.thePlayer.inventory.currentItem;
                            PacketUtil.sendPacket(
                                    new C09PacketHeldItemChange(altSlot(handle)));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            this.stopBlock();
                        }
                        attack = false;
                        this.blockTick = 3;
                        break;
                    case 3:
                        if (fullC09.getValue()) {
                            int handle = mc.thePlayer.inventory.currentItem;
                            PacketUtil.sendPacket(
                                    new C09PacketHeldItemChange(altSlot(handle)));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            this.stopBlock();
                        }
                        attack = false;
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = alwaysRenderBlocking.getValue();
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLagSwapAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        this.blockTick = 1;
                        break;
                    case 1:
                        Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                        if (this.isPlayerBlocking()) {
                            if (fullC09.getValue()) {
                                int handle = mc.thePlayer.inventory.currentItem;
                                PacketUtil.sendPacket(
                                        new C09PacketHeldItemChange(altSlot(handle)));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            }
                            this.stopBlock();
                        }
                        attack = false;
                        this.blockTick = 2;
                        break;
                    case 2:
                        if (fullC09.getValue()) {
                            int handle = mc.thePlayer.inventory.currentItem;
                            PacketUtil.sendPacket(
                                    new C09PacketHeldItemChange(altSlot(handle)));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            this.stopBlock();
                        }
                        attack = false;
                        this.blockTick = 3;
                        break;
                    case 3:
                        postBlink = true;
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = alwaysRenderBlocking.getValue();
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLagPostSwapAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        postBlinkReset = true;
                        this.blockTick = 1;
                        break;
                    case 1:
                        Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                        if (this.isPlayerBlocking()) {
                            postSwap = true;
                        }
                        attack = false;
                        this.blockTick = 2;
                        break;
                    case 2:
                        int handle = mc.thePlayer.inventory.currentItem;
                        PacketUtil.sendPacket(
                                new C09PacketHeldItemChange(altSlot(handle)));
                        PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                        PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                        attack = false;
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = alwaysRenderBlocking.getValue();
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLagSwapPlusAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        this.blockTick = 1;
                        break;
                    case 1:
                        Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                        if (this.isPlayerBlocking()) {
                            if (fullC09.getValue()) {
                                int handle = mc.thePlayer.inventory.currentItem;
                                PacketUtil.sendPacket(
                                        new C09PacketHeldItemChange(altSlot(handle)));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            }
                            this.stopBlock();
                        }
                        attack = false;
                        this.blockTick = 2;
                        break;
                    case 2:
                        if (fullC09.getValue()) {
                            int handle = mc.thePlayer.inventory.currentItem;
                            PacketUtil.sendPacket(
                                    new C09PacketHeldItemChange(altSlot(handle)));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            this.stopBlock();
                        }
                        this.blockTick = 3;
                        break;
                    case 3:
                        postBlink = true;
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = alwaysRenderBlocking.getValue();
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLagStopAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        blocked = true;
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        this.blockTick = 1;
                        break;
                    case 1:
                        attack = false;
                        this.blockTick = 2;
                        break;
                    case 2:
                        attack = false;
                        if (this.isPlayerBlocking()) {
                            int handle = mc.thePlayer.inventory.currentItem;
                            PacketUtil.sendPacket(
                                    new C09PacketHeldItemChange(altSlot(handle)));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                            this.stopBlock();
                        }
                        this.blockTick = 3;
                        break;
                    case 3:
                        attack = false;
                        this.blockTick = 4;
                        break;
                    case 4:
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = alwaysRenderBlocking.getValue();
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLagClickAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        blocked = true;
                        if (!this.isPlayerBlocking() && ItemUtil.isHoldingSword()) {
                            swap = true;
                        }
                        this.blockTick = 1;
                        break;
                    case 1:
                        attack = false;
                        PacketUtil.sendPacket(
                                new C0EPacketClickWindow(
                                        0,
                                        mc.thePlayer.inventory.currentItem + 36,
                                        altSlot(mc.thePlayer.inventory.currentItem),
                                        2,
                                        null,
                                        this.abClickAction++));
                        PacketUtil.sendPacket(
                                new C0EPacketClickWindow(
                                        0,
                                        altSlot(mc.thePlayer.inventory.currentItem)
                                                + 36,
                                        mc.thePlayer.inventory.currentItem,
                                        2,
                                        null,
                                        this.abClickAction++));
                        this.stopBlock();
                        this.blockTick = 2;
                        break;
                    case 2:
                        attack = false;
                        PacketUtil.sendPacket(
                                new C0EPacketClickWindow(
                                        0,
                                        mc.thePlayer.inventory.currentItem + 36,
                                        altSlot(mc.thePlayer.inventory.currentItem),
                                        2,
                                        null,
                                        this.abClickAction++));
                        PacketUtil.sendPacket(
                                new C0EPacketClickWindow(
                                        0,
                                        altSlot(mc.thePlayer.inventory.currentItem)
                                                + 36,
                                        mc.thePlayer.inventory.currentItem,
                                        2,
                                        null,
                                        this.abClickAction++));
                        this.stopBlock();
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            this.isBlocking = true;
            this.fakeBlockState = alwaysRenderBlocking.getValue();
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updatePredictAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                int hurt = mc.thePlayer.hurtTime;
                if (hurt != 0 && hurt <= startHurtTime.getValue()) {
                    if (!predictBlocking) {
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        this.predictBlocking = true;
                        holdTicks = 0;
                    }
                }
                if (holdTicks >= holdBlockTick.getValue() && this.predictBlocking) {
                    if (this.isPlayerBlocking()) {
                        this.stopBlock();
                    }
                    predictBlocking = false;
                    holdTicks = 0;
                }
            }
            this.isBlocking = this.predictBlocking;
            this.fakeBlockState = this.predictBlocking;
        } else {
            if (this.predictBlocking) {
                if (this.isPlayerBlocking()) {
                    this.stopBlock();
                }
                holdTicks = 0;
                this.predictBlocking = false;
            }
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateLegitAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        if (this.hasValidTarget()) {
            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                switch (this.blockTick) {
                    case 0:
                        if (!this.isPlayerBlocking()) {
                            swap = true;
                        }
                        this.blockTick = 1;
                        break;
                    case 1:
                        if (this.isPlayerBlocking()) {
                            this.stopBlock();
                            attack = false;
                        }
                        if (this.attackDelayMS <= 50L) {
                            this.blockTick = 0;
                        }
                        break;
                    default:
                        this.blockTick = 0;
                }
            }
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = true;
            this.fakeBlockState = false;
        } else {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private AutoBlockAction updateFakeAutoBlock() {
        boolean attack = true;
        boolean swap = false;
        boolean blocked = false;
        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.isBlocking = false;
        this.fakeBlockState = this.hasValidTarget();
        if (PlayerUtil.isUsingItem()
                && !this.isPlayerBlocking()
                && !Myau.playerStateManager.digging
                && !Myau.playerStateManager.placing) {
            swap = true;
        }
        return new AutoBlockAction(attack, swap, blocked);
    }

    private static final class AutoBlockAction {
        private boolean attack = true;
        private boolean startBlock;
        private boolean startBlink;

        private AutoBlockAction() {}

        private AutoBlockAction(boolean attack, boolean startBlock, boolean startBlink) {
            this.attack = attack;
            this.startBlock = startBlock;
            this.startBlink = startBlink;
        }
    }

    private static float[] nearestRotation(AxisAlignedBB box, float currentYaw, float currentPitch,
                                          float maxAngle, float smoothFactor) {
        if (mc.thePlayer == null) {
            return null;
        }
        Vec3 targetPoint = getNearestPointBB(box);
        if (targetPoint == null) {
            return null;
        }
        Vec3 eyePos = new Vec3(
                mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ
        );
        double diffX = targetPoint.xCoord - eyePos.xCoord;
        double diffY = targetPoint.yCoord - eyePos.yCoord;
        double diffZ = targetPoint.zCoord - eyePos.zCoord;
        double horizontalDist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yawDelta = MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0f - currentYaw
        );
        float pitchDelta = MathHelper.wrapAngleTo180_float(
                (float) (-Math.atan2(diffY, horizontalDist) * 180.0 / Math.PI) - currentPitch
        );
        yawDelta = Math.abs(yawDelta) <= 1.0f
                ? 0.0f
                : smoothAngle(clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f
                ? 0.0f
                : smoothAngle(clampAngle(pitchDelta, maxAngle), smoothFactor);
        return new float[]{
                quantizeAngle(currentYaw + yawDelta),
                quantizeAngle(currentPitch + pitchDelta)
        };
    }

    private static float[] normalRotation(AxisAlignedBB box, float currentYaw, float currentPitch,
                                          float maxAngle, float smoothFactor) {
        if (mc.thePlayer == null) {
            return null;
        }
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
        double minTargetY = box.minY + 0.05 * (box.maxY - box.minY);
        double maxTargetY = box.minY + 0.75 * (box.maxY - box.minY);
        double deltaX = (box.minX + box.maxX) / 2.0 - eyePos.xCoord;
        double deltaY = eyePos.yCoord >= maxTargetY
                ? maxTargetY - eyePos.yCoord
                : (eyePos.yCoord <= minTargetY ? minTargetY - eyePos.yCoord : 0.0);
        double deltaZ = (box.minZ + box.maxZ) / 2.0 - eyePos.zCoord;
        double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yawDelta = MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f - currentYaw
        );
        float pitchDelta = MathHelper.wrapAngleTo180_float(
                (float) (-Math.atan2(deltaY, horizontalDist) * 180.0 / Math.PI) - currentPitch
        );
        yawDelta = Math.abs(yawDelta) <= 1.0f
                ? 0.0f
                : smoothAngle(clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f
                ? 0.0f
                : smoothAngle(clampAngle(pitchDelta, maxAngle), smoothFactor);
        return new float[]{
                quantizeAngle(currentYaw + yawDelta),
                quantizeAngle(currentPitch + pitchDelta)
        };
    }

    private static Vec3 getNearestPointBB(AxisAlignedBB box) {
        Vec3 eyePos = new Vec3(
                mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ
        );
        Vec3 nearestPoint = new Vec3(
                MathHelper.clamp_double(eyePos.xCoord, box.minX, box.maxX),
                MathHelper.clamp_double(eyePos.yCoord, box.minY, box.maxY),
                MathHelper.clamp_double(eyePos.zCoord, box.minZ, box.maxZ)
        );
        if (isPointVisible(nearestPoint)) {
            return nearestPoint;
        }
        AxisAlignedBB scanBox = box.expand(-0.002, -0.002, -0.002);
        double stepX = (scanBox.maxX - scanBox.minX) / 2.0;
        double stepY = (scanBox.maxY - scanBox.minY) / 2.0;
        double stepZ = (scanBox.maxZ - scanBox.minZ) / 2.0;
        Vec3 bestPoint = null;
        double minDistanceSq = Double.MAX_VALUE;
        for (double x = scanBox.minX; x <= scanBox.maxX; x += stepX) {
            for (double y = scanBox.minY; y <= scanBox.maxY; y += stepY) {
                for (double z = scanBox.minZ; z <= scanBox.maxZ; z += stepZ) {
                    Vec3 point = new Vec3(x, y, z);
                    double distanceSq = eyePos.squareDistanceTo(point);
                    if (isPointVisible(point) && distanceSq < minDistanceSq) {
                        minDistanceSq = distanceSq;
                        bestPoint = point;
                    }
                }
            }
        }
        return bestPoint != null ? bestPoint : nearestPoint;
    }

    private static boolean isPointVisible(Vec3 target) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        if (eyePos.squareDistanceTo(target) > 4096.0) {
            return false;
        }
        Vec3 toTarget = target.subtract(eyePos).normalize();
        return mc.thePlayer.getLookVec().dotProduct(toTarget) >= 0
                && mc.theWorld.rayTraceBlocks(eyePos, target, false, true, false) == null;
    }

    private static float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0f, Math.min(180.0f, maxAngle));
        if (angle > maxAngle) {
            angle = maxAngle;
        } else if (angle < -maxAngle) {
            angle = -maxAngle;
        }
        return angle;
    }

    private static float smoothAngle(float angle, float smoothFactor) {
        return angle * (0.5f + 0.5f * (1.0f - Math.max(0.0f, Math.min(1.0f, smoothFactor + RandomUtil.nextFloat(-0.1f, 0.1f)))));
    }

    private static float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % (double) 0.0096f);
    }

    public static class AttackData {
        private final EntityLivingBase entity;
        private final AxisAlignedBB box;
        private final double x;
        private final double y;
        private final double z;

        public AttackData(EntityLivingBase entityLivingBase) {
            this.entity = entityLivingBase;
            double collisionBorderSize = entityLivingBase.getCollisionBorderSize();
            this.box = entityLivingBase.getEntityBoundingBox().expand(collisionBorderSize, collisionBorderSize, collisionBorderSize);
            this.x = entityLivingBase.posX;
            this.y = entityLivingBase.posY;
            this.z = entityLivingBase.posZ;
        }

        public EntityLivingBase getEntity() {
            return this.entity;
        }

        public AxisAlignedBB getBox() {
            return this.box;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }
    }
}