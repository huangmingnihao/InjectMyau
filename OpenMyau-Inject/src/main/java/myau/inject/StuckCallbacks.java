package myau.inject;

import myau.event.EventManager;
import myau.events.StuckMoveEntityEvent;
import myau.events.StuckMoveEntityWithHeadingEvent;
import myau.events.StuckMoveInputEvent;
import myau.events.StuckPreLivingUpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;

/**
 * StuckPreLivingUpdateEvent / StuckMoveEntityEvent /
 */
public final class StuckCallbacks {
    public static final String OWNER = "myau/inject/StuckCallbacks";
    private StuckCallbacks() {
    }
    public static boolean preLivingUpdate() {
        try {
            StuckPreLivingUpdateEvent event = new StuckPreLivingUpdateEvent();
            EventManager.call(event);
            return event.isCancelled();
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return false;
        }
    }
    public static boolean moveEntity(Object self) {
        try {
            if (!(self instanceof EntityPlayerSP)) {
                return false;
            }
            StuckMoveEntityEvent event = new StuckMoveEntityEvent();
            EventManager.call(event);
            return event.isCancelled();
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return false;
        }
    }
    public static boolean moveEntityWithHeading(Object self) {
        try {
            if (!(self instanceof EntityPlayerSP)) {
                return false;
            }
            StuckMoveEntityWithHeadingEvent event = new StuckMoveEntityWithHeadingEvent();
            EventManager.call(event);
            return event.isCancelled();
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return false;
        }
    }
    public static void updatePlayerMoveState(Object self) {
        try {
            MovementInput input = (MovementInput) self;
            GameSettingsBridge.read(input);
            StuckMoveInputEvent event = new StuckMoveInputEvent(
                    input.moveForward, input.moveStrafe, input.jump, input.sneak);
            EventManager.call(event);
            input.moveForward = event.getForward();
            input.moveStrafe = event.getStrafe();
            input.jump = event.isJump();
            input.sneak = event.isSneak();
            if (input.sneak) {
                input.moveStrafe = (float) ((double) input.moveStrafe
                        * event.getSneakSlowdown());
                input.moveForward = (float) ((double) input.moveForward
                        * event.getSneakSlowdown());
            }
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            try {
                ((MovementInput) self).updatePlayerMoveState();
            } catch (Throwable fatal) {
                Log.swallowed(fatal);
            }
        }
    }
    private static final class GameSettingsBridge {
        private static void read(MovementInput input) {
            Minecraft minecraft = Minecraft.getMinecraft();
            input.moveStrafe = 0.0F;
            input.moveForward = 0.0F;
            if (minecraft.gameSettings.keyBindForward.isKeyDown()) {
                ++input.moveForward;
            }
            if (minecraft.gameSettings.keyBindBack.isKeyDown()) {
                --input.moveForward;
            }
            if (minecraft.gameSettings.keyBindLeft.isKeyDown()) {
                ++input.moveStrafe;
            }
            if (minecraft.gameSettings.keyBindRight.isKeyDown()) {
                --input.moveStrafe;
            }
            input.jump = minecraft.gameSettings.keyBindJump.isKeyDown();
            input.sneak = minecraft.gameSettings.keyBindSneak.isKeyDown();
        }
    }
}
