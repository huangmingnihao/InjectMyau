package myau.util;

import myau.access.AccessorMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;


public final class TimerSpeedLease {
    private static volatile TimerSpeedLease active;
    private static long generation;
    private static long tickGeneration = -1;
    private Timer timer;
    private float savedSpeed;
    private float speed;
    private int ticksRemaining;
    private long acquiredGeneration;

    public boolean acquire(float speed, int ticks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (ticks <= 0 || !Float.isFinite(speed) || speed < 1.0F) {
            return false;
        }
        if (active != null) {
            return false;
        }
        Timer timer = AccessorMinecraft.getTimer(mc);
        if (timer == null || timer.timerSpeed != 1.0F) {
            return false;
        }
        this.timer = timer;
        this.savedSpeed = timer.timerSpeed;
        this.speed = speed;
        this.ticksRemaining = ticks;
        this.acquiredGeneration = ++generation;
        active = this;
        timer.timerSpeed = speed;
        return true;
    }

    public boolean isActive() {
        Timer currentTimer = this.timer;
        return active == this && currentTimer != null && currentTimer.timerSpeed == this.speed;
    }

    public void release() {
        if (active != this) {
            return;
        }
        if (this.timer != null && this.timer.timerSpeed == this.speed) {
            this.timer.timerSpeed = this.savedSpeed;
        }
        active = null;
        this.timer = null;
        this.ticksRemaining = 0;
    }

    public static void cancelActive() {
        if (active != null) {
            active.release();
        }
    }

    public static void beginTick() {
        tickGeneration = active == null ? -1 : active.acquiredGeneration;
    }

    public static void endTick() {
        TimerSpeedLease lease = active;
        if (lease != null
                && lease.acquiredGeneration == tickGeneration
                && (--lease.ticksRemaining <= 0 || !lease.isActive())) {
            lease.release();
        }
        tickGeneration = -1;
    }

    public static void limitFrame(Timer timer) {
        TimerSpeedLease lease = active;
        if (lease == null || lease.timer != timer) {
            return;
        }
        if (!lease.isActive()) {
            lease.release();
            return;
        }
        timer.elapsedTicks = Math.min(timer.elapsedTicks, lease.ticksRemaining);
    }
}
