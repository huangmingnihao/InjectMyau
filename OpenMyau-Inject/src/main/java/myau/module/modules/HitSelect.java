package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LeftClickMouseEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HitSelect extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double HIT_RANGE = 3.0;
    private static final double HIT_RANGE_SQ = HIT_RANGE * HIT_RANGE;
    private static final long SERVER_CONFIRM_COOLDOWN_MS = 500L;
    private static final long SERVER_CONFIRM_TIMEOUT_MS = 1500L;
    private static final int BLOCK_WAIT_FIRST = 1;
    private static final int BLOCK_SERVER_COOLDOWN = 1 << 3;
    private static final int BLOCK_PREDICTED_BURST = 1 << 4;
    private static final int BLOCK_CRITICALS = 1 << 5;

    public final IntProperty pauseDuration = new IntProperty("pause-duration", 500, 0, 500);
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"BURST", "CRITICALS"});
    public final IntProperty waitForFirstHit = new IntProperty("wait-for-first-hit", 0, 0, 500);
    public final IntProperty hitLaterInTrades = new IntProperty("hit-later-in-trades", 0, 0, 500);
    public final IntProperty whenOnlyCombo = new IntProperty("when-only-combo", 0, 0, 10);
    public final BooleanProperty disableDuringKnockback = new BooleanProperty("disable-during-knockback", false);
    public final BooleanProperty onlyWhileDamaged = new BooleanProperty("only-while-damaged", false);
    public final BooleanProperty useServerAttackTime = new BooleanProperty("use-server-attack-time", false);
    public final BooleanProperty fakeSwing = new BooleanProperty("fake-swing", false);
    public final PercentProperty inCombatCancelRate = new PercentProperty("in-combat-cancel-rate", 100);
    public final PercentProperty missedSwingsCancelRate = new PercentProperty("missed-swings-cancel-rate", 0);
    private EntityPlayer currentTarget;
    private EntityPlayer engagedTarget;
    private final Map<Integer, TargetState> targetStates = new HashMap<>();
    private int lastSelfHurtTime;
    private boolean takingKnockback;
    private boolean waitFirstTracking;
    private long waitFirstStartMs = -1L;
    private boolean waitFirstUnlocked;
    public HitSelect() {
        super("Hit Select", false);
    }
    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%dms", this.pauseDuration.getValue())};
    }
    @Override
    public void onEnabled() {
        this.resetAllState();
    }
    @Override
    public void onDisabled() {
        this.resetAllState();
    }
    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
            this.resetAllState();
            return;
        }
        long now = System.currentTimeMillis();
        this.pruneTargetStates();
        this.updateCurrentTarget(this.findTarget(), now);
        this.updateSelfDamage();
        this.updateTargetDamage(now);
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
            return;
        }

        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            return;
        }

        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
            return;
        }
        boolean hitLivingEntity = mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectType.ENTITY
                && mc.objectMouseOver.entityHit instanceof EntityLivingBase;

        if (!hitLivingEntity) {
            if (this.shouldCancel(this.missedSwingsCancelRate.getValue())) {
                event.setCancelled(true);
            }
            return;
        }
        EntityLivingBase clicked = (EntityLivingBase) mc.objectMouseOver.entityHit;
        if (this.shouldBlockAttack(clicked)) {
            event.setCancelled(true);
        } else {
            this.confirmHit(clicked);
        }
    }
    public boolean shouldBlockAttack(EntityLivingBase targetEntity) {
        if (!this.isEnabled()) {
            return false;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
            return false;
        }

        long now = System.currentTimeMillis();
        EntityPlayer clickedTarget = targetEntity instanceof EntityPlayer ? this.asAttackedPlayer(targetEntity) : null;
        boolean blocked;
        if (clickedTarget == null) {
            blocked = this.shouldCancel(this.missedSwingsCancelRate.getValue());
        } else {
            this.updateCurrentTarget(clickedTarget, now);
            this.engagedTarget = clickedTarget;
            TargetState state = this.getTargetState(clickedTarget);
            if (state.comboCount < this.whenOnlyCombo.getValue()) {
                blocked = false;
            } else {
                int blockMask = this.getValidHitBlockMask(state, now);
                boolean rawBlock = (blockMask & BLOCK_WAIT_FIRST) != 0
                        || (blockMask & BLOCK_PREDICTED_BURST) != 0
                        || this.applyPauseDuration(state, blockMask & ~BLOCK_PREDICTED_BURST, now);
                blocked = rawBlock && this.shouldCancel(this.inCombatCancelRate.getValue());
            }
        }
        if (blocked && this.fakeSwing.getValue() && mc.thePlayer != null) {
            this.setSwinging();
        }
        return blocked;
    }
    public void confirmHit(EntityLivingBase targetEntity) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
            return;
        }
        if (!(targetEntity instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer target = this.asAttackedPlayer(targetEntity);
        if (target == null) {
            return;
        }
        this.recordPassedValidHit(target, System.currentTimeMillis());
    }
    private EntityPlayer findTarget() {
        EntityPlayer mouseOverTarget = mc.objectMouseOver == null ? null : this.asValidPlayer(mc.objectMouseOver.entityHit);
        return mouseOverTarget != null ? mouseOverTarget : this.findClosestTarget();
    }
    private EntityPlayer findClosestTarget() {
        if (mc.theWorld == null) {
            return null;
        }
        EntityPlayer closest = null;
        double closestDistanceSq = Double.MAX_VALUE;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!this.isValidPlayer(player)) {
                continue;
            }
            double distanceSq = RotationUtil.distanceToEntity(player);
            distanceSq *= distanceSq;
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = player;
            }
        }
        return closest;
    }
    private EntityPlayer asValidPlayer(Entity entity) {
        if (!(entity instanceof EntityPlayer)) {
            return null;
        }
        EntityPlayer player = (EntityPlayer) entity;
        return this.isValidPlayer(player) ? player : null;
    }
    private EntityPlayer asAttackedPlayer(Entity entity) {
        if (!(entity instanceof EntityPlayer)) {
            return null;
        }
        EntityPlayer player = (EntityPlayer) entity;
        if (mc.thePlayer == null || player == mc.thePlayer || player.isDead || player.deathTime != 0) {
            return null;
        }
        return this.isTargetAllowed(player) ? player : null;
    }
    private boolean isValidPlayer(EntityPlayer player) {
        if (mc.thePlayer == null || player == null || player == mc.thePlayer || player.isDead || player.deathTime != 0) {
            return false;
        }
        if (!this.isTargetAllowed(player)) {
            return false;
        }
        double distanceSq = RotationUtil.distanceToEntity(player);
        return distanceSq * distanceSq <= HIT_RANGE_SQ;
    }
    private boolean isTargetAllowed(EntityPlayer player) {
        return !TeamUtil.isFriend(player) && !TeamUtil.isSameTeam(player) && !TeamUtil.isBot(player);
    }

    private void setSwinging() {
        int armSwingEnd = mc.thePlayer.isPotionActive(Potion.digSpeed)
                ? 6 - (1 + mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier())
                : (mc.thePlayer.isPotionActive(Potion.digSlowdown)
                        ? 6 + (1 + mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) * 2
                        : 6);
        if (!mc.thePlayer.isSwingInProgress
                || mc.thePlayer.swingProgressInt >= armSwingEnd / 2
                || mc.thePlayer.swingProgressInt < 0) {
            mc.thePlayer.swingProgressInt = -1;
            mc.thePlayer.isSwingInProgress = true;
        }
    }
    private void updateCurrentTarget(EntityPlayer nextTarget, long now) {
        if (this.sameTarget(nextTarget)) {
            if (nextTarget != null) {
                this.currentTarget = nextTarget;
                this.getTargetState(nextTarget);
            }
            return;
        }

        this.currentTarget = nextTarget;
        if (nextTarget == null) {
            this.resetWaitFirstState();
        } else if (!this.waitFirstTracking) {
            this.waitFirstTracking = true;
            this.waitFirstStartMs = now;
            this.waitFirstUnlocked = false;
        }
        if (nextTarget != null) {
            this.getTargetState(nextTarget);
        }
    }
    private void updateSelfDamage() {
        int hurtTime = mc.thePlayer.hurtTime;
        boolean hurtAgain = hurtTime > this.lastSelfHurtTime;
        if (hurtAgain) {
            if (this.waitFirstTracking && !this.waitFirstUnlocked) {
                this.waitFirstUnlocked = true;
            }
            this.takingKnockback = true;
            if (this.engagedTarget != null) {
                TargetState state = this.getTargetState(this.engagedTarget);
                state.firstSelfHitSeen = true;
                state.comboCount = 0;
            }
        }

        if (this.takingKnockback && mc.thePlayer.onGround && !hurtAgain) {
            this.takingKnockback = false;
        }
        this.lastSelfHurtTime = hurtTime;
    }
    private void updateTargetDamage(long now) {
        if (this.engagedTarget == null) {
            return;
        }
        TargetState state = this.getTargetState(this.engagedTarget);
        int targetHurtTime = this.engagedTarget.hurtTime;
        if (targetHurtTime > state.lastObservedTargetHurtTime) {
            state.comboCount++;
        }
        if (this.useServerAttackTime.getValue()) {
            if (state.pendingServerConfirmationMs >= 0 && now - state.pendingServerConfirmationMs > SERVER_CONFIRM_TIMEOUT_MS) {
                state.pendingServerConfirmationMs = -1;
            }
            if (state.pendingServerConfirmationMs >= 0 && targetHurtTime > state.lastObservedTargetHurtTime) {
                state.pendingServerConfirmationMs = -1;
                state.lastConfirmedTargetDamageMs = now;
                state.rawBlockMask = BLOCK_SERVER_COOLDOWN;
                state.rawBlockStartMs = now;
            }
        }
        state.lastObservedTargetHurtTime = targetHurtTime;
    }

    private int getValidHitBlockMask(TargetState state, long now) {
        if (this.currentTarget == null) {
            return 0;
        }
        if (this.disableDuringKnockback.getValue() && this.isTakingKnockback()) {
            return 0;
        }
        int blockMask = 0;
        if (this.isWaitingForFirstHit(now)) {
            blockMask |= BLOCK_WAIT_FIRST;
        }
        blockMask |= this.getBurstBlockMask(state, now);
        if (this.isCriticalsBlocked(state)) {
            blockMask |= BLOCK_CRITICALS;
        }
        return blockMask;
    }

    private int getBurstBlockMask(TargetState state, long now) {
        if (this.useServerAttackTime.getValue()) {
            long serverCooldownMs = SERVER_CONFIRM_COOLDOWN_MS + this.tradeExtensionMs(state);
            if (state.lastConfirmedTargetDamageMs >= 0 && now - state.lastConfirmedTargetDamageMs < serverCooldownMs) {
                return BLOCK_SERVER_COOLDOWN;
            }
            return 0;
        }
        return this.isPredictedBurstWindowActive(state, now) ? BLOCK_PREDICTED_BURST : 0;
    }

    private long tradeExtensionMs(TargetState state) {
        return state.firstSelfHitSeen ? this.hitLaterInTrades.getValue() : 0L;
    }
    private boolean isCriticalsBlocked(TargetState state) {
        if (this.mode.getValue() != 1) {
            return false;
        }
        if (mc.thePlayer.onGround) {
            return false;
        }
        if (this.onlyWhileDamaged.getValue() && !state.firstSelfHitSeen) {
            return false;
        }
        if (this.disableDuringKnockback.getValue() && this.isTakingKnockback()) {
            return false;
        }
        return !this.canCriticalHit();
    }
    private boolean isWaitingForFirstHit(long now) {
        if (this.waitForFirstHit.getValue() <= 0
                || this.currentTarget == null
                || !this.waitFirstTracking
                || this.waitFirstUnlocked
                || this.waitFirstStartMs < 0) {
            return false;
        }
        return now - this.waitFirstStartMs < this.waitForFirstHit.getValue();
    }
    private boolean canCriticalHit() {
        return mc.thePlayer.fallDistance > 0.0F
                && !mc.thePlayer.onGround
                && !mc.thePlayer.isOnLadder()
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isPotionActive(Potion.blindness)
                && mc.thePlayer.ridingEntity == null;
    }
    private boolean isTakingKnockback() {
        return this.takingKnockback || mc.thePlayer.hurtTime > 0;
    }
    private boolean applyPauseDuration(TargetState state, int blockMask, long now) {
        if (blockMask == 0) {
            state.rawBlockMask = 0;
            state.rawBlockStartMs = -1L;
            return false;
        }
        if (this.pauseDuration.getValue() <= 0) {
            state.rawBlockMask = blockMask;
            state.rawBlockStartMs = now;
            return false;
        }
        if (blockMask != state.rawBlockMask) {
            state.rawBlockMask = blockMask;
            state.rawBlockStartMs = now;
        } else if (state.rawBlockStartMs < 0) {
            state.rawBlockStartMs = now;
        }
        return now - state.rawBlockStartMs < this.pauseDuration.getValue();
    }
    private void recordPassedValidHit(EntityPlayer target, long now) {
        if (target == null) {
            return;
        }
        this.updateCurrentTarget(target, now);
        TargetState state = this.getTargetState(target);
        if (this.useServerAttackTime.getValue()) {
            state.pendingServerConfirmationMs = now;
            state.lastConfirmedTargetDamageMs = -1L;
            return;
        }
        if (!this.isPredictedBurstWindowActive(state, now)) {
            this.startPredictedBurstWindow(state, now);
        }
    }
    public boolean shouldCancelMissedSwing() {
        return this.isEnabled() && this.shouldCancel(this.missedSwingsCancelRate.getValue());
    }
    private boolean shouldCancel(double chance) {
        if (chance <= 0.0) {
            return false;
        }
        if (chance >= 100.0) {
            return true;
        }
        return Math.random() * 100.0 < chance;
    }
    private boolean sameTarget(EntityPlayer nextTarget) {
        if (this.currentTarget == null || nextTarget == null) {
            return this.currentTarget == nextTarget;
        }
        return this.currentTarget.getEntityId() == nextTarget.getEntityId();
    }
    private void resetWaitFirstState() {
        this.waitFirstTracking = false;
        this.waitFirstStartMs = -1L;
        this.waitFirstUnlocked = false;
    }
    private boolean isPredictedBurstWindowActive(TargetState state, long now) {
        if (state.predictedBurstWindowStartMs < 0) {
            return false;
        }
        long effectivePauseMs = this.pauseDuration.getValue() + this.tradeExtensionMs(state);
        return effectivePauseMs > 0 && now - state.predictedBurstWindowStartMs < effectivePauseMs;
    }

    private void startPredictedBurstWindow(TargetState state, long startMs) {
        state.predictedBurstWindowStartMs = startMs;
    }
    private TargetState getTargetState(EntityPlayer target) {
        TargetState state = this.targetStates.get(target.getEntityId());
        if (state == null) {
            state = new TargetState();
            if (this.useServerAttackTime.getValue()) {
                state.lastObservedTargetHurtTime = target.hurtTime;
            }
            this.targetStates.put(target.getEntityId(), state);
        }
        return state;
    }
    private void pruneTargetStates() {
        if (mc.theWorld == null) {
            this.targetStates.clear();
            return;
        }
        Iterator<Map.Entry<Integer, TargetState>> iterator = this.targetStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TargetState> entry = iterator.next();
            Entity entity = mc.theWorld.getEntityByID(entry.getKey());
            if (!(entity instanceof EntityPlayer) || entity.isDead || ((EntityPlayer) entity).deathTime != 0) {
                iterator.remove();
            }
        }
    }
    private void resetAllState() {
        this.currentTarget = null;
        this.engagedTarget = null;
        this.targetStates.clear();
        this.lastSelfHurtTime = 0;
        this.takingKnockback = false;
        this.resetWaitFirstState();
    }
    private static class TargetState {
        boolean firstSelfHitSeen;
        long lastConfirmedTargetDamageMs = -1L;
        long pendingServerConfirmationMs = -1L;
        long predictedBurstWindowStartMs = -1L;
        int lastObservedTargetHurtTime;
        long rawBlockStartMs = -1L;
        int rawBlockMask;
        int comboCount;
    }
}
