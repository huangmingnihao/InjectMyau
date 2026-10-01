package myau.inject;

import myau.event.EventManager;
import myau.events.BridgeInputEvent;
import myau.module.modules.BridgeAssist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovementInput;

/**
 */
public final class BridgeAssistCallbacks {
    public static final String OWNER = "myau/inject/BridgeAssistCallbacks";
    private static float savedYaw;
    private static float savedPitch;
    private static float savedPrevYaw;
    private static float savedPrevPitch;
    private static boolean swapped;
    private BridgeAssistCallbacks() {
    }

    /**
     */
    public static void onGetMouseOverHead() {
        try {
            swapped = false;
            Minecraft mc = Minecraft.getMinecraft();
            Entity view = mc.getRenderViewEntity();
            if (view == null || !(view instanceof EntityPlayerSP)) {
                return;
            }
            float[] fake = BridgeAssist.getFakeRotation();
            if (fake == null) {
                return;
            }
            savedYaw = view.rotationYaw;
            savedPitch = view.rotationPitch;
            savedPrevYaw = view.prevRotationYaw;
            savedPrevPitch = view.prevRotationPitch;
            view.rotationYaw = fake[0];
            view.prevRotationYaw = fake[0];
            view.rotationPitch = fake[1];
            view.prevRotationPitch = fake[1];
            swapped = true;
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
    }

    /**
     */
    public static void onGetMouseOverReturn() {
        try {
            if (!swapped) {
                return;
            }
            Entity view = Minecraft.getMinecraft().getRenderViewEntity();
            if (view != null) {
                view.rotationYaw = savedYaw;
                view.prevRotationYaw = savedPrevYaw;
                view.rotationPitch = savedPitch;
                view.prevRotationPitch = savedPrevPitch;
            }
            swapped = false;
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
    }

    public static void onBridgeInput() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            EntityPlayerSP player = minecraft.thePlayer;
            if (player == null || player.movementInput == null) {
                return;
            }
            GameSettings settings = minecraft.gameSettings;
            float forward = 0.0F;
            float strafe = 0.0F;
            if (settings.keyBindForward.isKeyDown()) {
                ++forward;
            }
            if (settings.keyBindBack.isKeyDown()) {
                --forward;
            }
            if (settings.keyBindLeft.isKeyDown()) {
                ++strafe;
            }
            if (settings.keyBindRight.isKeyDown()) {
                --strafe;
            }
            boolean jump = settings.keyBindJump.isKeyDown();
            boolean sneak = settings.keyBindSneak.isKeyDown();

            BridgeInputEvent event = new BridgeInputEvent(forward, strafe, jump, sneak);
            EventManager.call(event);
            BridgeAssist.fixMovementForServerRotation(event);

            MovementInput input = player.movementInput;
            input.moveForward = event.getForward();
            input.moveStrafe = event.getStrafe();
            input.jump = event.isJump();
            input.sneak = event.isSneak();
            if (input.sneak) {
                input.moveStrafe = (float) ((double) input.moveStrafe * 0.3D);
                input.moveForward = (float) ((double) input.moveForward * 0.3D);
            }
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
    }
}
