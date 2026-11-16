package myau.util;

/** Tick-based Stasis lifecycle; capture/restore motion only on transitions. */
public final class StuckFreezeState {
    private boolean frozen;
    private int delay;
    private int frozenTicks;
    public void enable() { reset(); delay = 2; }
    public void reset() { frozen = false; delay = frozenTicks = 0; }
    public boolean isFrozen() { return frozen; }
    public boolean blocksInput() { return frozen || delay == 1; }
    public void resumeAfter(int ticks) { frozen = false; frozenTicks = 0; delay = ticks; }
    public void tick(boolean pulse, int interval) {
        // Retain Stuck's PulseDelay=0 behavior: pass physics every tick.
        if (pulse && interval <= 0) { resumeAfter(1); return; }
        if (delay > 0 && --delay == 0) { frozen = true; frozenTicks = 0; }
        if (frozen && pulse && frozenTicks++ >= interval) resumeAfter(1);
    }
}
