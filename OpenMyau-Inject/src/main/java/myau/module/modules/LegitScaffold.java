package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.LoadWorldEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.ItemUtil;
import myau.util.KeyBindUtil;
import myau.util.MoveUtil;
import myau.util.PlayerUtil;
import myau.util.SneakCameraAnimation;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovementInput;
import org.apache.commons.lang3.RandomUtils;

import java.util.Objects;

public class LegitScaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int sneakDelay = 0;
    private Entity visualPlayer;
    private int visualTick = Integer.MIN_VALUE;
    private boolean visualSneaking;
    private final SneakCameraAnimation cameraAnimation = new SneakCameraAnimation();
    public final IntProperty minDelay = new IntProperty("min-delay", 2, 0, 10);
    public final IntProperty maxDelay = new IntProperty("max-delay", 3, 0, 10);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty pitchCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneaking-only", false);
    public final BooleanProperty visualSneak = new BooleanProperty("visual-sneak", false);

    public static float getCameraTranslationY(Entity view, float x, float y, float z) {
        // orientCamera also translates the sleeping and third-person cameras. Only the
        // final, purely vertical eye-height translation belongs to first-person crouching.
        if (x != 0.0F || z != 0.0F || y >= 0.0F || Myau.moduleManager == null) {
            return y;
        }
        LegitScaffold scaffold = (LegitScaffold) Myau.moduleManager.modules.get(LegitScaffold.class);
        if (scaffold == null) {
            return y;
        }
        if (!scaffold.isEnabled() || !scaffold.visualSneak.getValue()
                || mc.thePlayer == null || mc.theWorld == null || view != mc.thePlayer
                || mc.gameSettings.thirdPersonView != 0 || mc.currentScreen != null
                || mc.thePlayer.isPlayerSleeping() || mc.thePlayer.isRiding()
                || mc.thePlayer.movementInput == null
                || scaffold.visualPlayer != view || scaffold.visualTick > mc.thePlayer.ticksExisted
                || mc.thePlayer.ticksExisted - scaffold.visualTick > 1) {
            scaffold.cameraAnimation.reset();
            return y;
        }
        // Keep the module's decision: launchers can restore the held sneak key after input events.
        // In sneaking-only mode that key enables the module, not a permanent crouch pose.
        // Apply after launcher eye-height modifiers, which can discard getEyeHeight's result.
        // Replace the translation instead of adding a second crouch offset.
        return -scaffold.cameraAnimation.sample(-y, System.nanoTime());
    }

    private boolean canMoveSafely() {
        double[] offset = MoveUtil.predictMovement();
        return PlayerUtil.canMove(mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
    }

    private boolean shouldSneak() {
        if (this.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
            return false;
        } else if (this.pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
            return false;
        } else if(sneakOnly.getValue() && !KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())){
            return false;
        } else {
            return (!this.blocksOnly.getValue() || ItemUtil.isHoldingBlock()) && mc.thePlayer.onGround;
        }
    }

    public LegitScaffold() {
        super("LegitScaffold", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (mc.thePlayer == null || mc.theWorld == null) {
                this.resetSneakState();
                return;
            }
            if (this.sneakDelay > 0) {
                this.sneakDelay--;
            }
            if (this.sneakDelay == 0 && this.canMoveSafely()) {
                this.sneakDelay = RandomUtils.nextInt(this.minDelay.getValue(), this.maxDelay.getValue() + 1);
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.currentScreen != null || mc.thePlayer == null
                || mc.theWorld == null || mc.thePlayer.movementInput == null) {
            this.visualPlayer = null;
            this.cameraAnimation.reset();
            return;
        }
        if (this.visualPlayer != mc.thePlayer || this.visualTick > mc.thePlayer.ticksExisted
                || mc.thePlayer.ticksExisted - this.visualTick > 1) {
            this.cameraAnimation.reset();
        }
        MovementInput input = mc.thePlayer.movementInput;
        boolean before = input.sneak;
        boolean eligible = this.shouldSneak();
        boolean sneaking = before && !(this.sneakOnly.getValue() && eligible);
        if (!sneaking && eligible && (this.sneakDelay > 0 || this.canMoveSafely())) {
            sneaking = true;
        }
        if (before != sneaking) {
            float multiplier = sneaking ? 0.3F : 1.0F / 0.3F;
            input.moveForward *= multiplier;
            input.moveStrafe *= multiplier;
        }
        input.sneak = sneaking;
        this.visualSneaking = sneaking;
        this.cameraAnimation.setSneaking(sneaking, System.nanoTime());
        this.visualTick = mc.thePlayer.ticksExisted;
        this.visualPlayer = mc.thePlayer;
    }

    @Override
    public void onDisabled() {
        this.resetSneakState();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.resetSneakState();
    }

    private void resetSneakState() {
        this.sneakDelay = 0;
        this.visualPlayer = null;
        this.visualTick = Integer.MIN_VALUE;
        this.visualSneaking = false;
        this.cameraAnimation.reset();
    }

    @Override
    public void verifyValue(String name) {
        switch (name) {
            case "min-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "max-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(this.minDelay.getValue(), this.maxDelay.getValue())
                ? new String[]{this.minDelay.getValue().toString()}
                : new String[]{String.format("%d-%d", this.minDelay.getValue(), this.maxDelay.getValue())};
    }
}
