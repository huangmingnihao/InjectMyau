package myau.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

import java.util.function.BiPredicate;

public final class RiseRotation {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static float currentYaw;
    private static float currentPitch;
    private static boolean primed;
    private static float searchX;
    private static float searchY;
    private static float searchAngle;
    private RiseRotation() {
    }
    public static void reset() {
        primed = false;
        searchX = 0.0f;
        searchY = 0.0f;
        searchAngle = 0.0f;
    }

    public static float getYaw() {
        return primed ? currentYaw : mc.thePlayer.rotationYaw;
    }
    public static float getPitch() {
        return primed ? currentPitch : mc.thePlayer.rotationPitch;
    }
    public static float[] step(float targetYaw, float targetPitch, double speed,
                               BiPredicate<Float, Float> reachable) {
        if (!primed) {
            currentYaw = mc.thePlayer.rotationYaw;
            currentPitch = mc.thePlayer.rotationPitch;
            primed = true;
        }
        if (speed <= 0.0) {
            return new float[]{currentYaw, currentPitch};
        }
        float[] aimed = search(targetYaw, targetPitch, reachable);
        float[] result = glide(aimed[0], aimed[1], speed * SPEED_SCALE + Math.random());
        currentYaw = result[0];
        currentPitch = result[1];
        return result;
    }
    private static final double SPEED_SCALE = 36.0;

    private static float[] search(float targetYaw, float targetPitch, BiPredicate<Float, Float> reachable) {
        if (reachable == null
                || (Math.abs(targetYaw - currentYaw) <= 5.0f && Math.abs(targetPitch - currentPitch) <= 5.0f)) {
            return new float[]{targetYaw, targetPitch};
        }

        double magnitude = Math.random() * Math.random() * Math.random() * 20.0;
        searchAngle += (float) ((20.0 + (Math.random() - 0.5) * (Math.random() * Math.random() * Math.random() * 360.0))
                * (mc.thePlayer.ticksExisted / 10 % 2 == 0 ? -1 : 1));
        searchX += (float) (-MathHelper.sin((float) Math.toRadians(searchAngle)) * magnitude);
        searchY += (float) (MathHelper.cos((float) Math.toRadians(searchAngle)) * magnitude);
        float yaw = targetYaw + searchX;
        float pitch = targetPitch + searchY;
        if (!reachable.test(yaw, pitch)) {
            searchAngle = (float) Math.toDegrees(Math.atan2(targetYaw - yaw, pitch - targetPitch)) - 180.0f;
            searchX += (float) (-MathHelper.sin((float) Math.toRadians(searchAngle)) * magnitude);
            searchY += (float) (MathHelper.cos((float) Math.toRadians(searchAngle)) * magnitude);
            yaw = targetYaw + searchX;
            pitch = targetPitch + searchY;
        }
        if (!reachable.test(yaw, pitch)) {
            searchX = 0.0f;
            searchY = 0.0f;
            yaw = (float) (targetYaw + Math.random() * 2.0);
            pitch = (float) (targetPitch + Math.random() * 2.0);
        }
        return new float[]{yaw, pitch};
    }
    private static float[] glide(float targetYaw, float targetPitch, double cap) {
        float[] step = stepVector(targetYaw, targetPitch, cap);
        float yaw = currentYaw + step[0];
        float pitch = currentPitch + step[1];
        boolean moving = Math.abs(step[0]) + Math.abs(step[1]) > 1.0E-4f;
        int substeps = (int) (Minecraft.getDebugFPS() / 20.0f + Math.random() * 10.0);
        for (int i = 1; i <= substeps; i++) {
            if (moving) {
                yaw += (float) ((Math.random() - 0.5) / 1000.0);
                pitch -= (float) (Math.random() / 200.0);
            }
            float[] quantised = quantise(yaw, pitch);
            yaw = quantised[0];
            pitch = Math.max(-90.0f, Math.min(90.0f, quantised[1]));
        }
        return new float[]{yaw, pitch};
    }
    private static float[] stepVector(float targetYaw, float targetPitch, double cap) {
        double deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        double deltaPitch = targetPitch - currentPitch;
        double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance < 1.0E-4) {
            return new float[]{0.0f, 0.0f};
        }
        double capYaw = cap * Math.abs(deltaYaw / distance);
        double capPitch = cap * Math.abs(deltaPitch / distance);
        return new float[]{
                (float) Math.max(Math.min(deltaYaw, capYaw), -capYaw),
                (float) Math.max(Math.min(deltaPitch, capPitch), -capPitch)
        };
    }

    private static float[] quantise(float yaw, float pitch) {
        float sensitivity = (float) (mc.gameSettings.mouseSensitivity * (1.0 + Math.random() / 1000000.0) * 0.6f + 0.2f);
        double gridStep = sensitivity * sensitivity * sensitivity * 8.0f * 0.15;
        float baseYaw = mc.thePlayer.prevRotationYaw;
        float basePitch = mc.thePlayer.prevRotationPitch;
        return new float[]{
                baseYaw + (float) (Math.round((yaw - baseYaw) / gridStep) * gridStep),
                MathHelper.clamp_float(basePitch + (float) (Math.round((pitch - basePitch) / gridStep) * gridStep),
                        -90.0f, 90.0f)
        };
    }
}
