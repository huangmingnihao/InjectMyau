package myau.util;

/** OpenB4's script position and movement-input conventions. */
public final class SprintScaffoldMath {
    private SprintScaffoldMath() {}

    public static int blockY(double y) { return (int) Math.floor(y + 0.5); }

    public static float moveYaw(float yaw, float forward, float strafe) {
        float factor = 1F;
        if (forward < 0) { yaw += 180F; factor = -.5F; }
        else if (forward > 0) factor = .5F;
        if (strafe > 0) yaw -= 90F * factor;
        else if (strafe < 0) yaw += 90F * factor;
        return yaw;
    }

    private static float wrap(float a) {
        a %= 360F;
        if (a >= 180F) a -= 360F;
        if (a < -180F) a += 360F;
        return a;
    }

    public static float[] fixInput(float cameraYaw, float serverYaw, float forward, float strafe) {
        if (forward == 0F && strafe == 0F) return new float[]{0F, 0F};
        float target = wrap(moveYaw(cameraYaw, forward, strafe));
        float best = -1F, bestForward = 0F, bestStrafe = 0F;
        for (float f = -1; f <= 1; f++) {
            for (float s = -1; s <= 1; s++) {
                if (f == 0 && s == 0) continue;
                float diff = Math.abs(wrap(target - wrap(moveYaw(serverYaw, f, s))));
                if (best != -1F && diff >= best) continue;
                best = diff; bestForward = f; bestStrafe = s;
            }
        }
        float speed = Math.max(Math.abs(forward), Math.abs(strafe));
        return new float[]{bestForward * speed, bestStrafe * speed};
    }
}
