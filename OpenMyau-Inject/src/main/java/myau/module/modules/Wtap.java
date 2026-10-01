package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AttackEvent;
import myau.events.SprintEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.PercentProperty;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

public class Wtap extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final IntProperty delayUntilReset = new IntProperty("delay-until-reset", 150, 0, 1000);
    public final IntProperty delayBetweenReset = new IntProperty("delay-between-reset", 300, 0, 1000);
    public final BooleanProperty playersOnly = new BooleanProperty("players-only", true);

    private long pendingResetAtMs;
    private long lastResetStartMs;
    private boolean waitingForSprintRestart;
    private boolean wasSprinting;
    private boolean stopSprintPending;
    public Wtap() {
        super("WTap", false);
    }
    @Override
    public void onEnabled() {
        this.resetState();
    }
    @Override
    public void onDisabled() {
        this.resetState();
    }
    private void resetState() {
        this.pendingResetAtMs = 0L;
        this.lastResetStartMs = 0L;
        this.waitingForSprintRestart = false;
        this.wasSprinting = false;
        this.stopSprintPending = false;
    }
    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || !mc.thePlayer.isSprinting()) {
            return;
        }
        Entity target = event.getTarget();
        if (this.playersOnly.getValue()) {
            if (!(target instanceof EntityPlayer)) {
                return;
            }
            if (TeamUtil.isBot((EntityPlayer) target)) {
                return;
            }
        } else if (!(target instanceof EntityLivingBase)) {
            return;
        }
        if (((EntityLivingBase) target).deathTime != 0) {
            return;
        }
        if (this.pendingResetAtMs > 0L) {
            return;
        }
        long now = System.currentTimeMillis();

        if (this.lastResetStartMs > 0L && now - this.lastResetStartMs < this.delayBetweenReset.getValue()) {
            return;
        }

        double chanceValue = this.chance.getValue();
        if (chanceValue < 100.0 && Math.random() * 100.0 >= chanceValue) {
            return;
        }
        this.pendingResetAtMs = now + this.delayUntilReset.getValue();
    }
    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
            this.resetState();
            return;
        }
        long now = System.currentTimeMillis();
        boolean sprintingNow = mc.thePlayer.isSprinting();
        if (this.waitingForSprintRestart && sprintingNow && !this.wasSprinting) {
            this.lastResetStartMs = now;
            this.waitingForSprintRestart = false;
        }
        if (this.pendingResetAtMs > 0L && now >= this.pendingResetAtMs) {
            this.stopSprintPending = true;
            this.pendingResetAtMs = 0L;
            this.waitingForSprintRestart = true;
        }
        this.wasSprinting = sprintingNow;
    }
    @EventTarget
    public void onSprint(SprintEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || !this.stopSprintPending) {
            return;
        }
        this.stopSprintPending = false;
        if (mc.thePlayer.isSprinting()) {
            mc.thePlayer.setSprinting(false);
        }
    }
    @Override
    public String[] getSuffix() {
        return new String[]{"Legit"};
    }
}
