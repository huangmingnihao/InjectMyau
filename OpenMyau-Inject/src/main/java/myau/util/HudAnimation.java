package myau.util;

/** Frame-rate independent transitions, including reversals while an animation is running. */
public final class HudAnimation {
    private HudAnimation() {}

    public static float approach(float value, float target, float elapsedMs, float durationMs) {
        if (durationMs <= 0.0F) return target;
        if (elapsedMs <= 0.0F) return value;
        double fraction = -Math.expm1(-6.0 * Math.min(elapsedMs, 1000.0F) / durationMs);
        return (float) (value + (target - value) * fraction);
    }

    public static float visibility(float value, boolean enabled, float elapsedMs, float durationMs) {
        float result = approach(value, enabled ? 1.0F : 0.0F, elapsedMs, durationMs);
        if (enabled && result > 0.999F) return 1.0F;
        if (!enabled && result < 0.001F) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, result));
    }

    public static float textX(boolean left, float boxLeft, float boxRight, float padding, float textWidth) {
        return left ? boxLeft + padding : boxRight - padding - textWidth;
    }
}
