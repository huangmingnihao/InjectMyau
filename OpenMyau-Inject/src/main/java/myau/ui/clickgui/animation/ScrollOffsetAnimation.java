package myau.ui.clickgui.animation;

public class ScrollOffsetAnimation {
    private final long duration;
    private float from;
    private float to;
    private long startedAt;
    public ScrollOffsetAnimation(long duration) {
        this.duration = duration;
    }
    public void reset(float value) {
        this.from = value;
        this.to = value;
        this.startedAt = 0L;
    }
    public void setTarget(float target) {
        this.from = this.getValue();
        this.to = target;
        this.startedAt = System.currentTimeMillis();
    }
    public void extend(float delta) {
        this.from = this.getValue();
        this.to += delta;
        this.startedAt = System.currentTimeMillis();
    }
    public void clampTarget(float min, float max) {
        this.to = Math.max(min, Math.min(max, this.to));
    }
    public float getValue() {
        if (this.startedAt == 0L) {
            return this.to;
        }
        long elapsed = System.currentTimeMillis() - this.startedAt;
        if (elapsed >= this.duration) {
            this.startedAt = 0L;
            this.from = this.to;
            return this.to;
        }
        return this.from + (this.to - this.from) * expoOut((float) elapsed / (float) this.duration);
    }
    public boolean isAnimating() {
        return this.startedAt != 0L && System.currentTimeMillis() - this.startedAt < this.duration;
    }
    public float getTarget() {
        return this.to;
    }

    private static float expoOut(float t) {
        if (t <= 0.0F) {
            return 0.0F;
        }
        if (t >= 1.0F) {
            return 1.0F;
        }
        return 1.0F - (float) Math.pow(2.0, -10.0 * t);
    }
}
