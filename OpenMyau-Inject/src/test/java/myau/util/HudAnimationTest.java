package myau.util;

public final class HudAnimationTest {
    public static void main(String[] args) {
        float at30 = advance(30), at60 = advance(60), at240 = advance(240);
        close(at30, at60, 0.00001F, "30/60 FPS timing");
        close(at60, at240, 0.00001F, "60/240 FPS timing");
        check(at60 > 0.99F && at60 <= 1, "appears in configured duration");
        float value = 0;
        for (int i = 0; i < 200; i++) {
            boolean enabled = i % 3 != 0;
            float next = HudAnimation.visibility(value, enabled, i % 7 == 0 ? 200 : 16, 280);
            check(next >= 0 && next <= 1, "no overshoot during rapid toggling or stalls");
            check(enabled ? next >= value : next <= value, "reversal follows new target");
            value = next;
        }
        for (int i = 0; i < 100; i++) value = HudAnimation.visibility(value, false, 16, 280);
        close(value, 0, 0, "disabled row eventually disappears");
        close(HudAnimation.visibility(0.4F, true, 1, 0), 1, 0, "zero duration enables instantly");
        close(HudAnimation.visibility(0.4F, false, 1, 0), 0, 0, "zero duration disables instantly");
        close(HudAnimation.approach(0, 100, 200, 280), 98.62362F, 0.001F, "long frame stays bounded");
        close(HudAnimation.approach(4, 9, -20, 280), 4, 0, "clock reversal does not move");
        close(HudAnimation.textX(false, 20, 150, 4, 90), 56, 0, "right anchor");
        close(HudAnimation.textX(true, 20, 150, 4, 90), 24, 0, "left anchor");
        System.out.println("HUD animation regression checks passed");
    }

    private static float advance(int fps) {
        float value = 0;
        for (int i = 0; i < fps; i++) value = HudAnimation.approach(value, 1, 280.0F / fps, 280);
        return value;
    }

    private static void close(float actual, float expected, float tolerance, String message) {
        check(Math.abs(actual - expected) <= tolerance, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
