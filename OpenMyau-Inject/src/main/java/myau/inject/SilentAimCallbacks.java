package myau.inject;

import myau.Myau;
import myau.access.AccessorMinecraft;
import myau.module.modules.AimAssist;
import myau.slinky.silent.SilentAimContext;
import myau.slinky.silent.SilentAimFrame;
import myau.slinky.silent.SilentRotationService;
import net.minecraft.client.Minecraft;

/** Shared injected/Mixin adapters for the reference's ray, entity and display phases. */
public final class SilentAimCallbacks {
    public static final String OWNER = "myau/inject/SilentAimCallbacks";
    private static double previousDisplaySeconds;
    private SilentAimCallbacks() { }

    private static AimAssist module() {
        return Myau.moduleManager == null ? null : (AimAssist)Myau.moduleManager.modules.get(AimAssist.class);
    }

    private static SilentAimContext context() { return new SilentAimContext(Minecraft.getMinecraft()); }

    public static void beforeRay() {
        AimAssist aim = module();
        if (aim != null && aim.usesSourceSilent()) {
            SilentAimContext context = context();
            SilentRotationService.beginFrame(context, 0, context.ready() ? AccessorMinecraft.getTimer(context.minecraft()).renderPartialTicks : 0);
        }
    }

    public static void afterEntities(Object world) {
        AimAssist aim = module();
        SilentAimContext context = context();
        if (aim != null && aim.usesSourceSilent() && context.ready() && world == context.world()) {
            aim.sourceSilent().afterEntities(context);
        }
    }

    public static void beforeDisplay() {
        AimAssist aim = module();
        SilentAimContext context = context();
        double now = System.nanoTime() / 1.0E9;
        if (aim == null || !aim.usesSourceSilent() || !context.ready()) { previousDisplaySeconds = 0; return; }
        if (previousDisplaySeconds == 0) previousDisplaySeconds = now;
        SilentRotationService.beforeDisplay(context);
        double elapsed = now - previousDisplaySeconds;
        if (!(0.002 > elapsed)) {
            aim.sourceSilent().display(context, AccessorMinecraft.getTimer(context.minecraft()).renderPartialTicks, elapsed);
            SilentRotationService.applyPending(context, false);
            previousDisplaySeconds = now;
            SilentRotationService.afterDisplay(context);
        }
    }

    public static void prepareMouse() {
        AimAssist aim = module();
        if (aim != null && aim.usesSourceSilent()) SilentRotationService.prepareMouse(context());
    }

    public static void beginCamera() { SilentAimContext c = context(); if (c.ready()) SilentRotationService.beginCamera(c); }
    public static void endCamera() { SilentRotationService.endCamera(); }
    public static void beginRenderInfo() { SilentAimContext c = context(); if (c.ready()) SilentRotationService.beginRenderInfo(c); }
    public static void endRenderInfo() { SilentRotationService.endRenderInfo(); }
    public static void endRender() { SilentRotationService.endRender(); }
    public static void beginMovement() { SilentAimContext c = context(); if (c.inputReady()) SilentRotationService.beginMovement(c); }
    public static void endMovement() { SilentRotationService.endMovement(null); }

    public static void stop(boolean restore) {
        SilentAimContext c = context();
        SilentRotationService.endRender();
        if (restore && c.ready()) SilentRotationService.finishFrame(c, new SilentAimFrame());
        SilentRotationService.reset(c);
        previousDisplaySeconds = 0;
    }
}
