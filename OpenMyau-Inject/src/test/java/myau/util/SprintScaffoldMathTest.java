package myau.util;

public final class SprintScaffoldMathTest {
    public static void main(String[] args) {
        check(SprintScaffoldMath.blockY(64.49) == 64, "below half block");
        check(SprintScaffoldMath.blockY(64.5) == 65, "script rounds Y up at half block");
        check(SprintScaffoldMath.blockY(-.51) == -1, "negative Y floors correctly");
        for (int yaw = -720; yaw <= 720; yaw += 15) {
            for (int turn = -180; turn <= 180; turn += 45) {
                for (int f = -1; f <= 1; f++) for (int s = -1; s <= 1; s++) {
                    float[] result = SprintScaffoldMath.fixInput(yaw, yaw + turn, f * .3F, s * .3F);
                    float magnitude = Math.max(Math.abs(result[0]), Math.abs(result[1]));
                    check(Math.abs(magnitude - (f == 0 && s == 0 ? 0 : .3F)) < .0001F,
                            "preserve reduced input speed without applying sneak twice");
                    if (f == 0 && s == 0) continue;
                    double before = Math.toRadians(SprintScaffoldMath.moveYaw(yaw, f, s));
                    double after = Math.toRadians(SprintScaffoldMath.moveYaw(yaw + turn, result[0], result[1]));
                    check(Math.abs(Math.sin(before) - Math.sin(after)) < .0001 &&
                            Math.abs(Math.cos(before) - Math.cos(after)) < .0001,
                            "world movement direction survives a silent rotation");
                }
            }
        }
        System.out.println("SprintScaffold script semantics passed (half-block Y, all input octants, reduced speed, stationary input)");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
