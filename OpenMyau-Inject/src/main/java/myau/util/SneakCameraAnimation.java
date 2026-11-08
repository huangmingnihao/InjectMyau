package myau.util;

/** Continuous camera height; movement and packet sneak state remain immediate. */
public final class SneakCameraAnimation {
    // Reach 95% of a new height in 120 ms, independently of tick rate and render FPS.
    private static final double RESPONSE_NANOS = 40_000_000.0;
    private boolean initialized;
    private boolean sneaking;
    private double height;
    private long lastSampleNanos;

    public void setSneaking(boolean sneaking, long nowNanos) {
        if (this.sneaking == sneaking) {
            return;
        }
        // Finish the old target's elapsed time before changing direction. Otherwise
        // the new target would retroactively apply to the time since the previous frame.
        this.advance(nowNanos);
        this.sneaking = sneaking;
    }

    public float sample(float currentCameraHeight, long nowNanos) {
        if (!this.initialized) {
            this.height = currentCameraHeight;
            this.lastSampleNanos = nowNanos;
            this.initialized = true;
        } else {
            this.advance(nowNanos);
        }
        return (float) this.height;
    }

    private void advance(long nowNanos) {
        if (!this.initialized) {
            return;
        }
        long elapsed = nowNanos - this.lastSampleNanos;
        if (elapsed <= 0L) {
            return;
        }
        double target = this.sneaking ? 1.54 : 1.62;
        this.height = target + (this.height - target) * Math.exp(-elapsed / RESPONSE_NANOS);
        this.lastSampleNanos = nowNanos;
    }

    public void reset() {
        // Retain the requested pose, but start the next animation at the camera's
        // actual height when re-entering first person or enabling visual-sneak.
        this.initialized = false;
    }
}
