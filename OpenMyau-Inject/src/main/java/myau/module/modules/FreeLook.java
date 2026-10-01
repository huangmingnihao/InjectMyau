package myau.module.modules;

import myau.access.AccessorMinecraft;
import myau.event.EventTarget;
import myau.events.LoadWorldEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.KeyProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MouseHelper;

/**
 * Freelook ported from raven-bS.  Holds the player's body rotation in place while
 * the camera is driven independently by the mouse.  Works through the runtime hook
 * pipeline (no Mixin) so it is usable under Forge, Badlion and Lunar.
 */
public class FreeLook extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean perspectiveToggled;
    public static float cameraYaw;
    public static float cameraPitch;

    public final KeyProperty freelookKey = new KeyProperty("freelook-key", 56);
    public final BooleanProperty hold = new BooleanProperty("hold", true);
    public final BooleanProperty invertPitch = new BooleanProperty("invert-pitch", false);
    public final BooleanProperty lockPitch = new BooleanProperty("lock-pitch", true);
    public final BooleanProperty customFov = new BooleanProperty("custom-fov", false);
    public final IntProperty fov =
            new IntProperty("fov", 90, 10, 150, 1, () -> this.customFov.getValue());

    private boolean prevKeyState;
    private int previousPerspective;
    private float lastFov;

    public FreeLook() {
        super("Free Look", false);
    }

    /** Called once per rendered frame from EntityRenderer.updateCameraAndRender. */
    public void onFrame() {
        if (!this.isEnabled()) {
            if (perspectiveToggled) {
                resetPerspective();
            }
            return;
        }
        if (mc.currentScreen != null || mc.theWorld == null || mc.thePlayer == null) {
            if (perspectiveToggled && mc.currentScreen != null && this.hold.getValue()) {
                resetPerspective();
            }
            return;
        }
        boolean down = this.freelookKey.isHeld();
        if (down != this.prevKeyState) {
            this.prevKeyState = down;
            this.onPressed(down);
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (perspectiveToggled) {
            resetPerspective();
        }
    }

    private void onPressed(boolean state) {
        if (!this.isEnabled()) {
            if (perspectiveToggled) {
                resetPerspective();
            }
            return;
        }
        if (state) {
            cameraYaw = mc.thePlayer.rotationYaw;
            cameraPitch = mc.thePlayer.rotationPitch;
            if (perspectiveToggled) {
                resetPerspective();
            } else {
                enterPerspective();
            }
        } else if (this.hold.getValue()) {
            resetPerspective();
        }
    }

    private void enterPerspective() {
        perspectiveToggled = true;
        this.previousPerspective = mc.gameSettings.thirdPersonView;
        applyThirdPersonView(1);
        this.lastFov = mc.gameSettings.fovSetting;
    }

    public void resetPerspective() {
        perspectiveToggled = false;
        applyThirdPersonView(this.previousPerspective);
        if (mc.currentScreen == null && AccessorMinecraft.isInGameHasFocus(mc)) {
            grabMouse();
        }
        if (this.hold.getValue() || mc.gameSettings.fovSetting == this.lastFov
                || this.customFov.getValue()) {
            mc.gameSettings.fovSetting = this.lastFov;
        }
    }

    @Override
    public void onDisabled() {
        if (perspectiveToggled) {
            perspectiveToggled = false;
            applyThirdPersonView(0);
            if (mc.currentScreen == null && AccessorMinecraft.isInGameHasFocus(mc)) {
                grabMouse();
            }
            mc.gameSettings.fovSetting = this.lastFov;
        }
    }

    private static void grabMouse() {
        MouseHelper helper = AccessorMinecraft.getMouseHelper(mc);
        if (helper != null) {
            helper.grabMouseCursor();
        }
    }

    private void applyThirdPersonView(int view) {
        if (view < 0) {
            view = 0;
        } else if (view > 2) {
            view = 2;
        }
        mc.gameSettings.thirdPersonView = view;
        if (mc.entityRenderer != null) {
            if (view == 0) {
                mc.entityRenderer.loadEntityShader(mc.getRenderViewEntity());
            } else if (view == 1) {
                mc.entityRenderer.loadEntityShader((Entity) null);
            }
        }
        if (mc.renderGlobal != null) {
            mc.renderGlobal.setDisplayListEntitiesDirty();
        }
    }
}
