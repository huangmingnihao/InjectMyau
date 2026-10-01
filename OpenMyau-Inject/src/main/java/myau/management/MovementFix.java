package myau.management;

import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.module.modules.BridgeAssist;
import myau.module.modules.Scaffold;
import myau.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public class MovementFix {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float SAME_ANGLE = 0.01F;
    private static final float SNEAK_FACTOR = 0.3F;
    @EventTarget(Priority.MEDIUM)
    public void onMoveInput(MoveInputEvent event) {
        if (mc.thePlayer == null || mc.currentScreen != null) {
            return;
        }
        if (!RotationState.isActived() || RotationState.getPriority() < 0) {
            return;
        }
        if (BridgeAssist.isHandlingMovementFix()) {
            return;
        }
        if (Scaffold.isHandlingMovementFix()) {
            return;
        }
        if (!MoveUtil.isForwardPressed()) {
            return;
        }
        float target = RotationState.getSmoothedYaw();
        if (Math.abs(MathHelper.wrapAngleTo180_float(target - mc.thePlayer.rotationYaw)) < SAME_ANGLE) {
            return;
        }
        float sneak = mc.thePlayer.movementInput.sneak ? SNEAK_FACTOR : 1.0F;
        MoveUtil.fixStrafe(target);
        if (sneak != 1.0F) {
            mc.thePlayer.movementInput.moveForward *= sneak;
            mc.thePlayer.movementInput.moveStrafe *= sneak;
        }
    }
}
