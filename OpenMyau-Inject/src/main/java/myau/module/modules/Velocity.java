package myau.module.modules;

import com.google.common.base.CaseFormat;

import myau.Myau;
import myau.access.AccessorEntity;
import myau.enums.DelayModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.KnockbackEvent;
import myau.events.LivingUpdateEvent;
import myau.events.LoadWorldEvent;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.ChatUtil;
import myau.util.KeyBindUtil;
import myau.util.MoveUtil;
import myau.util.PacketUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import myau.util.TimerSpeedLease;
import myau.util.WorldTickUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int MODE_JUMP = 1;
    private static final int MODE_PREDICTION = 3;
    private static final int MODE_WATCHDOG = 6;

    private static final int MODE_SKIP_TICK = 7;

    private static final long MW_GROUND_TIMEOUT_MS = 1000L;
    private static final double FALL_IGNORE_DISTANCE = 3.0;
    private static final float JUMP_FOV = 330.0F;

    private int chanceCounter = 0;
    private int delayChanceCounter = 0;

    // ==================================================================

    //


    // ==================================================================


    public final IntProperty skipTick =
            new IntProperty("skip-tick", 5, 1, 20, () -> this.mode.getValue() == MODE_SKIP_TICK);
    public final BooleanProperty skipRangeCheck =
            new BooleanProperty("skip-range-check", true, () -> this.mode.getValue() == MODE_SKIP_TICK);
    public final FloatProperty skipRange =
            new FloatProperty("skip-range", 3.0F, 1.0F, 8.0F, () -> this.mode.getValue() == MODE_SKIP_TICK);
    public final BooleanProperty skipDelayFirst =
            new BooleanProperty("skip-delay-first", true, () -> this.mode.getValue() == MODE_SKIP_TICK);
    public final BooleanProperty skipJump =
            new BooleanProperty("skip-jump", true, () -> this.mode.getValue() == MODE_SKIP_TICK);

    private volatile boolean skipSequence;
    private volatile boolean skipPendingFreeze;
    private volatile boolean skipFreezeActive;
    private volatile boolean skipReleasingDelay;
    private volatile boolean skipAttackWindow;
    private volatile boolean skipWaitingForMovement;
    private volatile boolean skipDelayOnly;
    private volatile boolean skipWaitingForGround;
    private boolean skipDelayFirstActive;
    private long skipDelayDeadlineTick;
    private long skipReleaseMovementSequence;
    private volatile int skipBoostTicks;
    private int skipBoostTotal;
    private int skipFrozenTicks;
    private EntityPlayerSP skipPlayer;
    private WorldClient skipWorld;
    private long skipFreezeStarted;
    private boolean skipOwnsDelay;
    private volatile boolean skipSuppressPackets;
    private final TimerSpeedLease skipTimer = new TimerSpeedLease();
    private long skipAttackClaimTick = Long.MIN_VALUE;
    private boolean skipHurtConfirmed;
    private long skipHurtStatusNanos;
    private EntityPlayerSP skipHurtPlayer;

    private long killAuraSuppressedTick = Long.MIN_VALUE;
    private boolean cancellingKillAuraAttack;

    public static boolean extraAttacked = false;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;
    private boolean jumpFlag = false;
    private boolean reverseFlag = false;
    private boolean delayActive = false;

    private boolean shouldJump = false;
    private boolean setJump = false;
    private boolean ignoreNext = false;
    private int lastHurtTime = 0;
    private double lastFallDistance = 0.0;
    private int jumpCooldown = 0;

    private int mwCount = 0;
    private int mwAttackCount = 0;
    private int mwStuckTicks = 0;
    private boolean mwStrict = false;
    private boolean mwDelaying = false;
    private boolean mwServerSprint = false;
    private int mwLastAttackTick = -1;
    private long mwWorldChangeTime = 0L;
    private long mwDelayStart = 0L;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"VANILLA", "JUMP", "DELAY", "PREDICTION", "REVERSE", "LEGIT_TEST",
            "WATCHDOG", "SKIP_TICK"});
    public final IntProperty delayTicks = new IntProperty("delay-ticks", 3, 1, 20, this::isDelayMode);
    public final BooleanProperty reduce = new BooleanProperty("reduce", false,
            () -> this.mode.getValue() == MODE_PREDICTION);
    public final ModeProperty reduceMode = new ModeProperty("reduce-mode", 0,
            new String[]{"ReleaseWhenCanAttack"},
            () -> this.mode.getValue() == MODE_PREDICTION && this.reduce.getValue());
    public final BooleanProperty extraAttack = new BooleanProperty("extra-attack", false,
            () -> this.mode.getValue() == MODE_PREDICTION && this.reduce.getValue());
    public final BooleanProperty lookingAtPlayer = new BooleanProperty("looking-at-player", false,
            this::isDelayMode);
    public final BooleanProperty requireLeftMouse = new BooleanProperty("require-left-mouse", false,
            this::isDelayMode);
    public final BooleanProperty onlyWeapon = new BooleanProperty("only-weapon", false,
            this::isDelayMode);
    public final IntProperty mwDelayTicks = new IntProperty("mw-delay-ticks", 1, 0, 100,
            () -> this.mode.getValue() == MODE_WATCHDOG);
    public final IntProperty mwWorldTimeout = new IntProperty("mw-world-timeout", 5000, 0, 10000,
            () -> this.mode.getValue() == MODE_WATCHDOG);
    public final BooleanProperty mwCancelExplosion = new BooleanProperty("mw-cancel-explosion", false,
            () -> this.mode.getValue() == MODE_WATCHDOG);
    public final BooleanProperty mwIgnoreExplosion = new BooleanProperty("mw-ignore-explosion", false,
            () -> this.mode.getValue() == MODE_WATCHDOG);
    public final BooleanProperty mwJumpReset = new BooleanProperty("mw-jump-reset", false,
            () -> this.mode.getValue() == MODE_WATCHDOG);
    public final BooleanProperty mwAttackReduce = new BooleanProperty("mw-attack-reduce", false,
            () -> this.mode.getValue() == MODE_WATCHDOG);
    public final BooleanProperty mwDisableOnFlag = new BooleanProperty("mw-disable-on-flag", false,
            () -> this.mode.getValue() == MODE_WATCHDOG);
    public final BooleanProperty mwAirOnly = new BooleanProperty("mw-air-only", true,
            () -> this.mode.getValue() == MODE_WATCHDOG);
    public final BooleanProperty mwUntilGround = new BooleanProperty("mw-until-ground", false,
            () -> this.mode.getValue() == MODE_WATCHDOG);
    public final PercentProperty delayChance = new PercentProperty("delay-chance", 100, this::isDelayMode);
    public final PercentProperty chance =
            new PercentProperty("chance", 100, () -> !this.isJumpMode());
    public final PercentProperty horizontal =
            new PercentProperty("horizontal", 0, () -> !this.isJumpMode());
    public final PercentProperty vertical =
            new PercentProperty("vertical", 100, () -> !this.isJumpMode());
    public final PercentProperty explosionHorizontal =
            new PercentProperty("explosions-horizontal", 100, () -> !this.isJumpMode());
    public final PercentProperty explosionVertical =
            new PercentProperty("explosions-vertical", 100, () -> !this.isJumpMode());
    public final PercentProperty jumpChance =
            new PercentProperty("jump-chance", 80, this::jumpResetActive);
    public final BooleanProperty requireMouseDown = new BooleanProperty("require-mouse-down", false,
            this::jumpResetActive);
    public final BooleanProperty requireMovingForward =
            new BooleanProperty("require-moving-forward", true, this::jumpResetActive);
    public final BooleanProperty requireAim = new BooleanProperty("require-aim", true,
            this::jumpResetActive);
    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);
    public final BooleanProperty debugLog = new BooleanProperty("debug-log", false);

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || AccessorEntity.getIsInWeb(mc.thePlayer);
    }


    private boolean canDelay() {
        return mc.thePlayer.onGround;
    }


    private boolean canTriggerDelay() {
        if (this.lookingAtPlayer.getValue() && !isAimingAtPlayer()) {
            return false;
        }
        if (this.requireLeftMouse.getValue() && !mc.gameSettings.keyBindAttack.isKeyDown()) {
            return false;
        }
        if (this.onlyWeapon.getValue() && !myau.util.ItemUtil.isHoldingSword()) {
            return false;
        }
        return true;
    }

    public Velocity() {
        super("Velocity", false);
    }

    private boolean isJumpMode() {
        return this.mode.getValue() == MODE_JUMP || this.mode.getValue() == MODE_PREDICTION;
    }

    private boolean isDelayMode() {
        return this.mode.getValue() == 2 || this.mode.getValue() == MODE_PREDICTION;
    }

    private boolean canOwnDelay() {
        return Myau.delayManager != null
                && (Myau.delayManager.getDelayModule() == DelayModules.VELOCITY
                || Myau.delayManager.getDelayModule() == DelayModules.NONE
                && Myau.delayManager.delayedPacket.isEmpty());
    }


    public boolean isAttackReduceActive() {
        return this.isEnabled() && this.mode.getValue() == MODE_WATCHDOG
                && this.mwAttackReduce.getValue() && this.mwAttackCount > 0;
    }

    private boolean mwInactive() {
        return !this.isEnabled() || this.mode.getValue() != MODE_WATCHDOG
                || mc.thePlayer == null || mc.theWorld == null
                || System.currentTimeMillis() - this.mwWorldChangeTime
                < (long) this.mwWorldTimeout.getValue();
    }

    private boolean jumpResetActive() {
        return this.isJumpMode()
                || this.mode.getValue() == MODE_WATCHDOG && this.mwJumpReset.getValue();
    }

    private boolean mwOwnsDelay() {
        return Myau.delayManager.getDelayModule() == DelayModules.VELOCITY;
    }

    private void mwRelease() {
        if (this.mwOwnsDelay()) {
            Myau.delayManager.setDelayState(false, DelayModules.VELOCITY);
        }
        this.mwDelaying = false;
    }

    private void mwReset() {
        this.mwRelease();
        this.mwCount = 0;
        this.mwAttackCount = 0;
        this.mwStuckTicks = 0;
        this.mwStrict = false;
        this.mwDelaying = false;
    }

    @EventTarget
    public void onLoadWorldMw(LoadWorldEvent event) {
        this.mwWorldChangeTime = System.currentTimeMillis();
        this.mwRelease();
        this.mwCount = 0;
        this.mwAttackCount = 0;
        this.mwStuckTicks = 0;
        this.mwStrict = false;
        this.mwDelaying = false;
    }

    @EventTarget
    public void onMoveInputMw(MoveInputEvent event) {
        if (this.mwInactive() || mc.thePlayer.movementInput == null) {
            return;
        }
        if (this.mwStrict) {
            mc.thePlayer.movementInput.moveForward = 1.0F;
            mc.thePlayer.movementInput.moveStrafe = 0.0F;
        }
    }


    @EventTarget
    public void onUpdateMw(UpdateEvent event) {
        if (this.mode.getValue() != MODE_WATCHDOG) {
            return;
        }
        if (event.getType() == EventType.POST) {
            if (this.mwInactive()) {
                if (this.mwOwnsDelay()) {
                    this.mwRelease();
                }
                return;
            }
            this.mwAdvanceDelay();
            return;
        }
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (this.mwInactive()) {
            if (this.mwOwnsDelay()) {
                this.mwRelease();
            }
            return;
        }
        if (this.mwStuckTicks > 0) {
            this.mwStuckTicks--;
        }
        if (this.mwAttackCount > 0) {
            if (this.mwAttackReduce.getValue()
                    && this.mwLastAttackTick != mc.thePlayer.ticksExisted
                    && this.mwServerSprint) {
                this.mwForceAttack();
            }
            this.mwAttackCount--;
        } else if (this.mwStrict) {
            this.mwStrict = false;
        }
    }

    private void mwAdvanceDelay() {
        if (!this.mwDelaying) {
            if (this.mwOwnsDelay()) {
                this.mwRelease();
            }
            return;
        }
        this.mwCount++;
        if (this.mwUntilGround.getValue()) {
            boolean settled = mc.thePlayer.onGround || mc.thePlayer.isOnLadder()
                    || this.isInLiquidOrWeb()
                    || System.currentTimeMillis() - this.mwDelayStart >= MW_GROUND_TIMEOUT_MS;
            if (settled) {
                this.mwCount = 0;
                this.mwRelease();
            }
            return;
        }
        if (this.mwCount >= this.mwDelayTicks.getValue()) {
            this.mwCount = 0;
            this.mwRelease();
        }
    }

    private void mwForceAttack() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) {
            return;
        }
        net.minecraft.entity.EntityLivingBase target = killAura.getTarget();
        if (target == null || target.isDead || target == mc.thePlayer) {
            return;
        }
        net.minecraft.util.AxisAlignedBB box = target.getEntityBoundingBox();
        double range = killAura.attackRange.getValue();
        if (RotationUtil.distanceToBox(box) > range) {
            return;
        }
        if (RotationUtil.rayTrace(box, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                range) == null) {
            return;
        }
        this.mwLastAttackTick = mc.thePlayer.ticksExisted;
        mc.thePlayer.swingItem();
        myau.access.AccessorPlayerControllerMP.callSyncCurrentPlayItem(mc.playerController);
        myau.util.PacketUtil.sendPacket(new net.minecraft.network.play.client.C02PacketUseEntity(
                target, net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK));
        if (mc.playerController.getCurrentGameType()
                != net.minecraft.world.WorldSettings.GameType.SPECTATOR) {
            myau.util.PlayerUtil.attackEntity(target);
        }
    }


    private boolean mwBeginDelay(PacketEvent event, net.minecraft.network.Packet<?> packet) {
        if (this.mwDelayTicks.getValue() == 0) {
            return false;
        }
        if (this.mwStuckTicks > 0) {
            return false;
        }
        if (this.mwAirOnly.getValue() && mc.thePlayer.onGround) {
            return false;
        }
        if (Myau.delayManager.getDelayModule() != DelayModules.NONE && !this.mwOwnsDelay()) {
            return false;
        }
        this.mwCount = 0;
        this.mwDelayStart = System.currentTimeMillis();
        this.mwDelaying = true;
        Myau.delayManager.setDelayState(true, DelayModules.VELOCITY);
        Myau.delayManager.delayedPacket.offer(
                (net.minecraft.network.Packet<net.minecraft.network.play.INetHandlerPlayClient>) packet);
        event.setCancelled(true);
        return true;
    }

    private boolean mwHandlePacket(PacketEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != MODE_WATCHDOG
                || mc.thePlayer == null) {
            return false;
        }
        net.minecraft.network.Packet<?> packet = event.getPacket();
        if (event.getType() == EventType.SEND) {
            if (packet instanceof net.minecraft.network.play.client.C02PacketUseEntity
                    && ((net.minecraft.network.play.client.C02PacketUseEntity) packet).getAction()
                    == net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK) {
                this.mwLastAttackTick = mc.thePlayer.ticksExisted;
            }
            if (packet instanceof net.minecraft.network.play.client.C0BPacketEntityAction) {
                net.minecraft.network.play.client.C0BPacketEntityAction action =
                        (net.minecraft.network.play.client.C0BPacketEntityAction) packet;
                if (action.getAction()
                        == net.minecraft.network.play.client.C0BPacketEntityAction.Action.START_SPRINTING) {
                    this.mwServerSprint = true;
                } else if (action.getAction()
                        == net.minecraft.network.play.client.C0BPacketEntityAction.Action.STOP_SPRINTING) {
                    this.mwServerSprint = false;
                }
            }
            return false;
        }
        if (event.getType() != EventType.RECEIVE || event.isCancelled()) {
            return false;
        }
        if (packet instanceof net.minecraft.network.play.server.S01PacketJoinGame
                || packet instanceof net.minecraft.network.play.server.S07PacketRespawn) {
            this.mwRelease();
            this.mwCount = 0;
            this.mwStuckTicks = 0;
            return false;
        }
        if (packet instanceof net.minecraft.network.play.server.S08PacketPlayerPosLook
                && this.mwDisableOnFlag.getValue()) {
            this.mwWorldChangeTime = System.currentTimeMillis();
            this.mwReset();
            return false;
        }
        if (this.mwInactive()) {
            return false;
        }
        if (this.mwDelaying) {
            return false;
        }
        if (packet instanceof S27PacketExplosion) {
            if (this.mwCancelExplosion.getValue()) {
                event.setCancelled(true);
                return true;
            }
            if (this.mwIgnoreExplosion.getValue()) {
                return false;
            }
            return this.mwBeginDelay(event, packet);
        }
        if (packet instanceof S12PacketEntityVelocity
                && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()) {
            if (this.mwAttackReduce.getValue()) {
                this.mwStrict = true;
                this.mwAttackCount = 2;
            }
            return this.mwBeginDelay(event, packet);
        }
        return false;
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            this.pendingExplosion = false;
            this.allowNext = true;
        } else if (this.isSkipTickMode()) {


            this.pendingExplosion = false;
            this.allowNext = true;
            boolean hurtConfirmed = event.getY() > 0.0;
            this.skipHurtConfirmed = hurtConfirmed;
            this.beginSkipSequence(null, hurtConfirmed);
        } else if (this.isJumpMode()) {
            this.pendingExplosion = false;
            this.allowNext = true;
        } else if (!this.allowNext || !(Boolean) this.fakeCheck.getValue()) {
            this.allowNext = true;
            if (this.pendingExplosion) {
                this.pendingExplosion = false;
                if (this.explosionHorizontal.getValue() > 0) {
                    event.setX(event.getX() * (double) this.explosionHorizontal.getValue() / 100.0);
                    event.setZ(event.getZ() * (double) this.explosionHorizontal.getValue() / 100.0);
                } else {
                    event.setX(mc.thePlayer.motionX);
                    event.setZ(mc.thePlayer.motionZ);
                }
                if (this.explosionVertical.getValue() > 0) {
                    event.setY(event.getY() * (double) this.explosionVertical.getValue() / 100.0);
                } else {
                    event.setY(mc.thePlayer.motionY);
                }
            } else {
                this.chanceCounter = this.chanceCounter % 100 + this.chance.getValue();
                if (this.chanceCounter >= 100) {
                    this.jumpFlag = this.mode.getValue() == 2 && event.getY() > 0.0;
                    this.delayActive = this.mode.getValue() == 4;
                    if (this.horizontal.getValue() > 0) {
                        event.setX(event.getX() * (double) this.horizontal.getValue() / 100.0);
                        event.setZ(event.getZ() * (double) this.horizontal.getValue() / 100.0);
                    } else {
                        event.setX(mc.thePlayer.motionX);
                        event.setZ(mc.thePlayer.motionZ);
                    }
                    if (this.vertical.getValue() > 0) {
                        event.setY(event.getY() * (double) this.vertical.getValue() / 100.0);
                    } else {
                        event.setY(mc.thePlayer.motionY);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.POST) {
            boolean reduceRelease = false;
            if (this.mode.getValue() == MODE_PREDICTION && this.reduce.getValue()
                    && this.reverseFlag) {
                KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                reduceRelease = killAura != null && killAura.isEnabled()
                        && killAura.velocityCanReduce(1, killAura.blockTick)
                        && killAura.shouldAutoBlock();
            }
            if (this.reverseFlag
                    && (
                    reduceRelease
                            ||
                    this.canDelay()
                            || this.isInLiquidOrWeb()
                            || Myau.delayManager.getDelay() >= (long) this.delayTicks.getValue()
            )) {
                if (this.debugLog.getValue()) {
                    ChatUtil.sendFormatted(String.format(
                            "%sDELAY &aend&r &7(delay %d/%d, ground %s)&r",
                            Myau.clientName, Myau.delayManager.getDelay(),
                            this.delayTicks.getValue(), mc.thePlayer.onGround));
                }
                Myau.delayManager.setDelayState(false, DelayModules.VELOCITY);
                this.reverseFlag = false;
                if (this.mode.getValue() == MODE_PREDICTION && this.reduce.getValue()
                        && this.extraAttack.getValue()) {
                    KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                    if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
                        PacketUtil.sendPacket(new net.minecraft.network.play.client.C02PacketUseEntity(
                                killAura.getTarget(),
                                net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK));
                    }
                }
            }
            if (this.delayActive) {
                MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                this.delayActive = false;
            }

            if (this.mode.getValue() == 5) {
                int hurtTime = mc.thePlayer.hurtTime;

                if (hurtTime >= 8) {
                    if (jumpCooldown <= 0) {
                        shouldJump = true;
                        jumpCooldown = 2;
                    }
                } else if (hurtTime <= 1) {
                    shouldJump = false;
                    jumpCooldown = 0;
                }

                if (shouldJump && mc.thePlayer.onGround && jumpCooldown <= 0) {
                    mc.thePlayer.jump();
                    shouldJump = false;
                }

                if (jumpCooldown > 0) {
                    jumpCooldown--;
                }
            }
        }
    }

    @EventTarget
    public void onJumpReset(UpdateEvent event) {
        if (!this.isEnabled() || !this.jumpResetActive() || mc.thePlayer == null) {
            return;
        }
        if (event.getType() == EventType.POST) {
            if (this.setJump && !KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
                this.setJump = false;
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            }
            return;
        }
        int hurtTime = mc.thePlayer.hurtTime;
        boolean onGround = mc.thePlayer.onGround;
        if (onGround && this.lastFallDistance > FALL_IGNORE_DISTANCE
                && !mc.thePlayer.capabilities.allowFlying) {
            this.ignoreNext = true;
        }
        if (hurtTime > this.lastHurtTime) {
            boolean mouseDown = !this.requireMouseDown.getValue()
                    || mc.gameSettings.keyBindAttack.isKeyDown();
            boolean aimingAt = !this.requireAim.getValue() || isAimingAtPlayer();
            boolean forward = !this.requireMovingForward.getValue()
                    || mc.gameSettings.keyBindForward.isKeyDown();
            boolean rolled = this.jumpChance.getValue() >= 100
                    || Math.random() * 100.0 < this.jumpChance.getValue();
            if (!this.ignoreNext && !mc.thePlayer.isBurning() && onGround && aimingAt && forward
                    && mouseDown && rolled && !hasBadEffect() && knockbackInFov()) {
                this.setJump = true;
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            }
            this.ignoreNext = false;
        }
        this.lastHurtTime = hurtTime;
        this.lastFallDistance = mc.thePlayer.fallDistance;
    }

    private static boolean isAimingAtPlayer() {
        MovingObjectPosition hit = mc.objectMouseOver;
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && hit.entityHit instanceof EntityPlayer;
    }

    private static boolean hasBadEffect() {
        return mc.thePlayer.isPotionActive(Potion.jump)
                || mc.thePlayer.isPotionActive(Potion.poison)
                || mc.thePlayer.isPotionActive(Potion.wither);
    }

    private static boolean knockbackInFov() {
        float heading = MoveUtil.getMoveYaw();
        float pushYaw = (float) (Math.atan2(mc.thePlayer.motionX, mc.thePlayer.motionZ)
                * 57.29578 * -1.0);
        float half = JUMP_FOV * 0.5F;
        double delta = MathHelper.wrapAngleTo180_double((heading - pushYaw) % 360.0F);
        return delta > 0.0 ? delta < half : delta > -half;
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.jumpFlag) {
            this.jumpFlag = false;
            if (mc.thePlayer.onGround && mc.thePlayer.isSprinting() && !mc.thePlayer.isPotionActive(Potion.jump) && !this.isInLiquidOrWeb()) {
                mc.thePlayer.movementInput.jump = true;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {

        if (this.isEnabled() && this.isSkipTickMode()
                && event.getType() == EventType.SEND && this.skipSuppressPackets
                && myau.util.SkipTickPacketPolicy.isGameplayPacket(event.getPacket())) {
            PacketUtil.markSendCancelled(event.getPacket());
            event.setCancelled(true);
            return;
        }

        if (this.isEnabled() && this.isSkipTickMode()
                && event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
                if (mc.thePlayer != null
                        && packet.getEntityID() == mc.thePlayer.getEntityId()) {
                    boolean hurtConfirmed = mc.thePlayer.hurtTime > 0;
                    this.skipHurtConfirmed = hurtConfirmed;
                    if (!this.skipReleasingDelay
                            && !this.isStuckActive()
                            && (packet.getMotionX() != 0 || packet.getMotionY() != 0
                            || packet.getMotionZ() != 0)
                            && this.beginSkipSequence(packet, hurtConfirmed)) {
                        event.setCancelled(true);
                    }


                    this.skipHurtConfirmed = false;
                    return;
                }
            }
        }
        if (this.mwHandlePacket(event)) {
            return;
        }



        if (this.isEnabled() && this.isSkipTickMode() && this.skipHurtConfirmed
                && event.getType() == EventType.RECEIVE && !event.isCancelled()
                && event.getPacket() instanceof S12PacketEntityVelocity
                && mc.thePlayer != null) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                if (!this.skipReleasingDelay && !this.isStuckActive()
                        && (packet.getMotionX() != 0 || packet.getMotionY() != 0
                        || packet.getMotionZ() != 0)
                        && this.beginSkipSequence(packet, true)) {
                    event.setCancelled(true);
                }
                return;
            }
        }
        if (this.isEnabled() && event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
                if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                    LongJump longJump = (LongJump) Myau.moduleManager.modules.get(LongJump.class);
                    if (this.isDelayMode()
                            && !this.reverseFlag
                            && this.canOwnDelay()
                            && this.canTriggerDelay()
                            && !this.canDelay()
                            && !this.isInLiquidOrWeb()
                            && !this.pendingExplosion
                            && (!this.allowNext || !(Boolean) this.fakeCheck.getValue())
                            && (!longJump.isEnabled() || !longJump.canStartJump())) {
                        this.delayChanceCounter = this.delayChanceCounter % 100 + this.delayChance.getValue();
                        if (this.delayChanceCounter >= 100) {
                            Myau.delayManager.setDelayState(true, DelayModules.VELOCITY);
                            Myau.delayManager.delayedPacket.offer(packet);
                            event.setCancelled(true);
                            this.reverseFlag = true;
                            if (this.debugLog.getValue()) {
                                ChatUtil.sendFormatted(String.format(
                                        "%sDELAY &astart&r &7(tick %d, ground %s, hurt %d)&r",
                                        Myau.clientName, mc.thePlayer.ticksExisted,
                                        mc.thePlayer.onGround, mc.thePlayer.hurtTime));
                            }
                            return;
                        }
                    }
                    if (this.debugLog.getValue() && this.reverseFlag) {
                        ChatUtil.sendFormatted(String.format(
                                "%sDELAY &cskip (S12 while delaying)&r", Myau.clientName));
                    }
                    if (this.debugLog.getValue()) {
                        ChatUtil.sendFormatted(
                                String.format(
                                        "%sVelocity (&otick: %d, x: %.2f, y: %.2f, z: %.2f&r)&r",
                                        Myau.clientName,
                                        mc.thePlayer.ticksExisted,
                                        (double) packet.getMotionX() / 8000.0,
                                        (double) packet.getMotionY() / 8000.0,
                                        (double) packet.getMotionZ() / 8000.0
                                )
                        );
                    }
                }
            } else if (!(event.getPacket() instanceof S27PacketExplosion)) {
                if (event.getPacket() instanceof S19PacketEntityStatus) {
                    S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
                    Entity entity = packet.getEntity(mc.theWorld);
                    if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
                        this.allowNext = false;
                    }
                }
            } else {
                S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
                if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                    this.pendingExplosion = true;
                    if (this.explosionHorizontal.getValue() == 0 || this.explosionVertical.getValue() == 0) {
                        event.setCancelled(true);
                    }
                    if (this.debugLog.getValue()) {
                        ChatUtil.sendFormatted(
                                String.format(
                                        "%sExplosion (&otick: %d, x: %.2f, y: %.2f, z: %.2f&r)&r",
                                        Myau.clientName,
                                        mc.thePlayer.ticksExisted,
                                        mc.thePlayer.motionX + (double) packet.func_149149_c(),
                                        mc.thePlayer.motionY + (double) packet.func_149144_d(),
                                        mc.thePlayer.motionZ + (double) packet.func_149147_e()
                                )
                        );
                    }
                }
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.onDisabled();
    }

    @Override
    public void onDisabled() {
        this.pendingExplosion = false;
        this.allowNext = true;
        this.shouldJump = false;
        this.jumpCooldown = 0;

        this.releaseSkipDelayed();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }

    // ======================================================================

    //





    // ======================================================================

    public boolean isSkipTickMode() {
        return this.mode.getValue() == MODE_SKIP_TICK;
    }


    private boolean isStuckActive() {
        if (Myau.moduleManager == null) {
            return false;
        }
        LeaderStuck stuck = (LeaderStuck) Myau.moduleManager.getModule(LeaderStuck.class);
        return stuck != null && stuck.isStuckActive();
    }

    private LeaderStuck stuckModule() {
        return Myau.moduleManager == null
                ? null
                : (LeaderStuck) Myau.moduleManager.getModule(LeaderStuck.class);
    }


    private boolean isSkipTargetInRange() {
        if (!this.skipRangeCheck.getValue()) {
            return true;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) {
            return false;
        }
        float range = this.skipRange.getValue();
        if (!Float.isFinite(range) || range < 0) {
            return false;
        }
        KillAura aura = Myau.moduleManager == null
                ? null
                : (KillAura) Myau.moduleManager.getModule(KillAura.class);
        for (Object o : mc.theWorld.loadedEntityList) {
            Entity entity = (Entity) o;
            if (!(entity instanceof EntityLivingBase) || entity == mc.thePlayer || entity.isDead) {
                continue;
            }
            EntityLivingBase target = (EntityLivingBase) entity;
            if (target instanceof EntityPlayer && ((EntityPlayer) target).isSpectator()) {
                continue;
            }
            if (target.deathTime > 0) {
                continue;
            }
            double distance = RotationUtil.distanceToEntity(target);
            if (!Double.isFinite(distance) || distance > range) {
                continue;
            }
            if (aura != null && aura.isEnabled()) {
                if (aura.isVelocityRangeTarget(target)) {
                    return true;
                }
            } else if (target instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) target;
                if (!myau.util.TeamUtil.isFriend(player)
                        && !myau.util.TeamUtil.isSameTeam(player)
                        && !myau.util.TeamUtil.isBot(player)) {
                    return true;
                }
            }
        }
        return false;
    }


    private synchronized boolean beginSkipSequence(Packet<?> packet, boolean hurtConfirmed) {
        if (this.skipSequence || this.skipAttackWindow || this.skipWaitingForMovement
                || this.skipDelayOnly || mc.thePlayer == null || mc.theWorld == null
                || this.isStuckActive()
                || Myau.delayManager == null
                || Myau.delayManager.getDelayModule() != DelayModules.NONE
                || !Myau.delayManager.delayedPacket.isEmpty()
                || WorldTickUtil.getSkipTicks() > 0
                || mc.thePlayer.isDead
                || (mc.thePlayer.hurtTime <= 0 && !hurtConfirmed)
                || mc.thePlayer.isRiding()
                || mc.currentScreen != null || !mc.inGameHasFocus) {
            return false;
        }

        boolean delayOnly = !this.isSkipTargetInRange();
        boolean waitForGround = !delayOnly && this.skipDelayFirst.getValue() && !mc.thePlayer.onGround;







        DelayModules owner = waitForGround ? DelayModules.VELOCITY : DelayModules.VELOCITY_SKIP_TICK;
        if (delayOnly) {
            Myau.delayManager.setDelayState(true, DelayModules.VELOCITY);
            if (packet != null) {
                Myau.delayManager.delayedPacket.offer((Packet<INetHandlerPlayClient>) packet);
            }
        } else if (!Myau.delayManager.beginDelay(
                owner, (Packet<INetHandlerPlayClient>) packet)) {
            return false;
        }

        this.skipOwnsDelay = true;
        this.skipSuppressPackets = false;
        this.skipPlayer = mc.thePlayer;
        this.skipWorld = mc.theWorld;
        this.skipBoostTotal = this.skipTick.getValue();
        this.skipDelayDeadlineTick = this.combatTick + this.skipBoostTotal;
        this.skipFreezeStarted = System.nanoTime();
        this.skipDelayOnly = delayOnly;
        this.skipDelayFirstActive = waitForGround;
        this.skipWaitingForGround = waitForGround;
        this.skipSequence = !delayOnly;
        this.skipFreezeActive = false;
        this.skipReleasingDelay = false;
        this.skipAttackWindow = false;
        this.skipPendingFreeze = !delayOnly;
        this.skipFrozenTicks = 0;









        return true;
    }


    private void resetSkipState(boolean releaseDelay) {
        if (this.skipTimer.isActive()) {
            this.skipTimer.release();
        }
        if (this.skipOwnsDelay && Myau.delayManager != null) {
            DelayModules owner = this.skipDelayOwner();
            if (Myau.delayManager.getDelayModule() == owner) {
                this.skipReleasingDelay = true;
                try {
                    if (releaseDelay && mc.thePlayer == this.skipPlayer
                            && mc.theWorld == this.skipWorld
                            && mc.getNetHandler() != null && !mc.thePlayer.isDead) {
                        Myau.delayManager.releaseDelay(owner);
                    } else {
                        Myau.delayManager.discardDelay(owner);
                    }
                } finally {
                    this.skipReleasingDelay = false;
                }
            }
        }
        this.skipSequence = false;
        this.skipPendingFreeze = false;
        this.skipFreezeActive = false;
        this.skipReleasingDelay = false;
        this.skipAttackWindow = false;
        this.skipWaitingForMovement = false;
        this.skipDelayOnly = false;
        this.skipWaitingForGround = false;
        this.skipDelayFirstActive = false;
        this.skipDelayDeadlineTick = 0L;
        this.skipBoostTicks = 0;
        this.skipBoostTotal = 0;
        this.skipFrozenTicks = 0;
        this.skipSuppressPackets = false;
        this.skipOwnsDelay = false;
        this.cancellingKillAuraAttack = false;
        this.skipPlayer = null;
        this.skipWorld = null;
        this.skipFreezeStarted = 0L;
        this.jumpFlag = false;
    }


    private void updateSkipTick(TickEvent event) {
        if (!this.isEnabled() || !this.isSkipTickMode()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null
                || mc.thePlayer.isDead || mc.currentScreen != null || !mc.inGameHasFocus) {
            if (this.skipSequence || this.skipAttackWindow
                    || this.skipWaitingForMovement || this.skipDelayOnly) {
                this.resetSkipState(true);
            }
            return;
        }

        if ((this.skipSequence || this.skipAttackWindow
                || this.skipWaitingForMovement || this.skipDelayOnly)
                && (mc.thePlayer != this.skipPlayer || mc.theWorld != this.skipWorld)) {
            this.resetSkipState(true);
            return;
        }

        if (event.getType() == EventType.PRE) {
            this.cancellingKillAuraAttack = !this.skipWaitingForGround
                    && (this.skipSequence || this.skipAttackWindow || this.skipWaitingForMovement);


            if ((this.skipSequence || this.skipWaitingForMovement)
                    && System.nanoTime() - this.skipFreezeStarted > 2_000_000_000L) {
                this.resetSkipState(true);
                return;
            }

            if (this.skipPendingFreeze && mc.thePlayer.hurtTime <= 0) {
                this.resetSkipState(true);
                return;
            }

            if (!this.skipDelayOnly
                    && (this.skipSequence || this.skipAttackWindow || this.skipWaitingForMovement)
                    && !this.isSkipTargetInRange()) {
                this.useSkipDelayOnly();
                return;
            }
            if (this.skipDelayOnly) {
                return;
            }
            if (this.skipWaitingForGround) {
                if (this.combatTick > this.skipDelayDeadlineTick) {
                    this.resetSkipState(true);
                    return;
                }
                if (!mc.thePlayer.onGround) {
                    return;
                }
                this.skipWaitingForGround = false;
                this.cancellingKillAuraAttack = true;
            }
            if (this.skipPendingFreeze) {
                int freezeTicks = this.skipDelayFirstActive
                        ? (int) Math.max(0L, Math.min((long) this.skipBoostTotal,
                        this.skipDelayDeadlineTick - this.combatTick + 1L))
                        : this.skipBoostTotal;
                if (freezeTicks <= 0) {
                    this.resetSkipState(true);
                    return;
                }
                KillAura killAura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
                if (killAura != null && killAura.isEnabled()) {
                    killAura.prepareVelocityAttackWindow();
                }
                LeaderStuck stuck = this.stuckModule();
                if (stuck == null || !stuck.beginExternalSkipFreeze(freezeTicks)) {
                    this.resetSkipState(true);
                } else {
                    this.skipPendingFreeze = false;
                    this.skipFreezeActive = true;
                    this.skipSuppressPackets = true;
                    this.skipFrozenTicks = freezeTicks;
                }
            }

            if (this.skipAttackWindow
                    && (this.isStuckActive() || !this.skipTimer.isActive())) {
                this.resetSkipState(true);
            }
            return;
        }

        if (event.getType() != EventType.POST) {
            return;
        }
        if (this.skipDelayOnly) {
            if (this.combatTick >= this.skipDelayDeadlineTick) {
                this.finishSkipDelayOnly();
            }
            return;
        }
        if (this.skipWaitingForGround) {
            if (this.combatTick >= this.skipDelayDeadlineTick) {
                this.resetSkipState(true);
            }
            return;
        }
        if (this.skipFreezeActive) {
            LeaderStuck stuck = this.stuckModule();
            if (stuck == null || !stuck.isExternalSkipFreezeActive()) {
                this.resetSkipState(true);
                return;
            }
            int remaining = stuck.getFreezeTicksRemaining();
            if (remaining > 0
                    && (!this.skipDelayFirstActive || this.combatTick < this.skipDelayDeadlineTick)) {
                return;
            }
            this.skipFrozenTicks = Math.max(0, this.skipFrozenTicks - remaining);
            stuck.endExternalSkipFreeze();

            this.skipFreezeActive = false;
            this.skipSuppressPackets = false;
            this.skipReleasingDelay = true;
            try {
                DelayModules owner = this.skipDelayOwner();
                if (Myau.delayManager.getDelayModule() != owner) {
                    this.resetSkipState(false);
                    return;
                }
                Myau.delayManager.releaseDelay(owner);
            } finally {
                this.skipReleasingDelay = false;
                this.skipOwnsDelay = false;
            }
            this.skipSequence = false;
            this.skipSuppressPackets = false;
            if (mc.thePlayer != this.skipPlayer || mc.theWorld != this.skipWorld
                    || mc.thePlayer.isDead) {
                this.resetSkipState(false);
                return;
            }
            this.jumpFlag = this.skipJump.getValue() && mc.thePlayer.onGround
                    && !mc.thePlayer.isBurning();
            this.skipWaitingForMovement = true;
            this.skipReleaseMovementSequence = Myau.playerStateManager.getMovementSequence();
            KillAura killAura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
            if (killAura != null && killAura.isEnabled()) {
                killAura.prepareVelocityAttackWindow();
            }
            return;
        }
        if (this.skipWaitingForMovement) {
            if (Myau.playerStateManager.getMovementSequence() > this.skipReleaseMovementSequence) {
                this.beginSkipBoost();
            }
            return;
        }
        if (this.skipAttackWindow && --this.skipBoostTicks <= 0) {
            this.resetSkipState(false);
        }
        if (!this.skipSequence && !this.skipAttackWindow && !this.skipWaitingForMovement) {
            this.cancellingKillAuraAttack = false;
        }
    }


    private void beginSkipBoost() {
        if (this.skipBoostTicks > 0 || mc.thePlayer == null) {
            return;
        }


        if (this.skipFrozenTicks <= 0) {
            this.resetSkipState(false);
            return;
        }
        if (!this.skipTimer.acquire(this.skipFrozenTicks, this.skipFrozenTicks)) {
            this.resetSkipState(false);
            return;
        }
        this.skipWaitingForMovement = false;
        this.skipBoostTicks = this.skipFrozenTicks;
        this.skipAttackWindow = true;
    }


    private void useSkipDelayOnly() {
        boolean pendingJump = this.jumpFlag;
        DelayModules delayOwner = this.skipDelayOwner();
        LeaderStuck stuck = this.stuckModule();
        if (this.skipFreezeActive && stuck != null && stuck.isExternalSkipFreezeActive()
                && !stuck.isTimedSkipFreezeActive()) {
            stuck.endExternalSkipFreeze();
        }
        this.skipTimer.release();
        this.skipFreezeActive = false;
        this.skipPendingFreeze = false;
        this.skipAttackWindow = false;
        this.skipWaitingForMovement = false;
        this.skipWaitingForGround = false;
        this.skipDelayFirstActive = false;
        this.skipSuppressPackets = false;
        this.skipBoostTicks = 0;
        this.skipFrozenTicks = 0;
        this.cancellingKillAuraAttack = false;
        if (!this.skipOwnsDelay || Myau.delayManager.getDelayModule() != delayOwner) {
            this.resetSkipState(false);
            this.jumpFlag = pendingJump;
            return;
        }
        Myau.delayManager.setDelayState(true, delayOwner);
        this.skipDelayOnly = true;
    }

    private DelayModules skipDelayOwner() {
        return this.skipDelayFirstActive ? DelayModules.VELOCITY : DelayModules.VELOCITY_SKIP_TICK;
    }


    private void finishSkipDelayOnly() {
        boolean shouldJump = mc.thePlayer != null && mc.theWorld != null
                && mc.thePlayer == this.skipPlayer && mc.theWorld == this.skipWorld
                && mc.getNetHandler() != null && !mc.thePlayer.isDead
                && this.skipJump.getValue() && mc.thePlayer.onGround
                && !mc.thePlayer.isBurning();
        this.resetSkipState(true);
        this.jumpFlag = shouldJump;
    }










    private void releaseSkipDelayed() {
        this.skipAttackWindow = false;
        this.skipSuppressPackets = false;
        LeaderStuck stuck = this.stuckModule();
        if (this.skipFreezeActive && stuck != null
                && stuck.isExternalSkipFreezeActive()) {
            stuck.endExternalSkipFreeze();
        }
        this.skipFreezeActive = false;
        this.skipPendingFreeze = false;
        this.skipBoostTicks = 0;
        if (this.skipOwnsDelay && Myau.delayManager != null) {
            DelayModules owner = this.skipDelayOwner();
            if (Myau.delayManager.getDelayModule() == owner) {
                Myau.delayManager.releaseDelay(owner);
            }
        }
        this.resetSkipState(false);
    }




    public static boolean isSkipTickAttackWindow() {
        if (Myau.moduleManager == null) return false;
        Velocity v = (Velocity) Myau.moduleManager.getModule(Velocity.class);
        return v != null && v.isEnabled() && v.isSkipTickMode()
                && !v.skipDelayOnly && !v.skipWaitingForGround
                && (v.skipSequence || v.skipWaitingForMovement || v.skipAttackWindow);
    }

    public static boolean isSkipTickCombatActive() {
        return isSkipTickAttackWindow();
    }


    public static boolean canPerformSkipTickAttack() {
        if (Myau.moduleManager == null || mc.thePlayer == null || mc.theWorld == null
                || !mc.isCallingFromMinecraftThread() || mc.currentScreen != null
                || !mc.inGameHasFocus) return false;
        Velocity v = (Velocity) Myau.moduleManager.getModule(Velocity.class);
        return v != null && v.isEnabled() && v.isSkipTickMode()
                && v.skipAttackWindow && !v.skipSequence && !v.skipSuppressPackets
                && mc.thePlayer == v.skipPlayer && mc.theWorld == v.skipWorld
                && !mc.thePlayer.isDead && mc.thePlayer.hurtTime > 0
                && !v.isStuckActive()
                && Myau.playerStateManager != null && !Myau.playerStateManager.digging
                && !Myau.playerStateManager.placing
                && !Myau.playerStateManager.hasInputAction()
                && v.isSkipTargetInRange();
    }


    public static boolean tryClaimSkipTickAttack() {
        if (!canPerformSkipTickAttack()) return false;
        Velocity v = (Velocity) Myau.moduleManager.getModule(Velocity.class);
        if (v.skipAttackClaimTick == v.combatTick) return false;
        v.skipAttackClaimTick = v.combatTick;
        v.suppressKillAuraForTick();
        return true;
    }

    public static boolean isSkipTickSequenceActive() {
        if (Myau.moduleManager == null) return false;
        Velocity v = (Velocity) Myau.moduleManager.getModule(Velocity.class);
        return v != null && v.isEnabled() && v.isSkipTickMode()
                && !v.skipDelayOnly
                && (v.skipSuppressPackets || v.skipSequence || v.skipPendingFreeze
                || v.skipFreezeActive || v.skipReleasingDelay
                || v.skipWaitingForMovement || v.skipAttackWindow);
    }

    public static boolean isSkipTickPacketFreezeActive() {
        if (Myau.moduleManager == null) return false;
        Velocity v = (Velocity) Myau.moduleManager.getModule(Velocity.class);
        return v != null && v.isEnabled() && v.isSkipTickMode() && v.skipSuppressPackets;
    }

    public static boolean shouldCancelKillAuraAttack() {
        if (Myau.moduleManager == null) return false;
        Velocity v = (Velocity) Myau.moduleManager.getModule(Velocity.class);
        return v != null && v.isEnabled()
                && (v.cancellingKillAuraAttack
                || v.killAuraSuppressedTick == v.combatTick
                || isSkipTickCombatActive());
    }

    private void suppressKillAuraForTick() {
        this.killAuraSuppressedTick = this.combatTick;
        this.cancellingKillAuraAttack = true;
    }


    private long combatTick = 0L;







    @EventTarget(Priority.LOWEST)
    public void onSkipTickAttack(UpdateEvent event) {
        if (event.getType() != EventType.PRE || !canPerformSkipTickAttack()) {
            return;
        }
        KillAura killAura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
        if (killAura != null) {
            killAura.performVelocityAttack(event.getNewYaw(), event.getNewPitch());
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onCombatTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            this.combatTick++;
            this.cancellingKillAuraAttack = false;
        }

        this.updateSkipTick(event);
    }
}