package myau.slinky.silent;

import myau.inject.HookRegistry;
import myau.inject.HookTransformer;
import myau.inject.Hooks;
import myau.inject.SilentAimCallbacks;
import myau.module.modules.AimAssist;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import sun.misc.Unsafe;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public final class SilentAimRegressionTest {
    private static int cleanupCount;

    public static void main(String[] args) throws Exception {
        lifecycle();
        hookIntegration();
        finallyVerifier();
        AimAssist aim = new AimAssist();
        check(aim.mode.parseString("Silent") && aim.mode.getValue() == 1, "old saved Silent name/index");
        check(aim.silent_move_correct.getValue() && aim.silent_move_correct.isVisible(), "source movement correction default");
        aim.mode.parseString("Normal");
        check(!aim.silent_move_correct.isVisible(), "Silent controls isolated from Normal");
        System.out.println("Silent Aim checks passed (retained/camera separation, vanilla mouse, quantization, key restore, lifecycle, real hook fixtures, JVM-verified exception cleanup)");
    }

    private static void lifecycle() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        Unsafe unsafe = (Unsafe)field.get(null);
        Minecraft mc = (Minecraft)unsafe.allocateInstance(Minecraft.class);
        EntityPlayerSP player = (EntityPlayerSP)unsafe.allocateInstance(EntityPlayerSP.class);
        mc.thePlayer = player;
        mc.theWorld = (WorldClient)unsafe.allocateInstance(WorldClient.class);
        mc.gameSettings = (GameSettings)unsafe.allocateInstance(GameSettings.class);
        mc.inGameHasFocus = true;
        GameSettings g = mc.gameSettings;
        g.mouseSensitivity = 0.5f;
        g.keyBindForward = new KeyBinding("silent-test-forward", 200, "silent-test");
        g.keyBindBack = new KeyBinding("silent-test-back", 208, "silent-test");
        g.keyBindLeft = new KeyBinding("silent-test-left", 203, "silent-test");
        g.keyBindRight = new KeyBinding("silent-test-right", 205, "silent-test");
        SilentAimContext context = new SilentAimContext(mc);
        SilentRotationService.reset(context);
        player.rotationYaw = 10; player.rotationPitch = 20;
        SilentRotationService.beginFrame(context, 0, 0);
        SilentAimFrame request = new SilentAimFrame();
        request.active = true; request.yaw = 10; request.pitch = 20; request.remapMovement = true;
        SilentRotationService.finishFrame(context, request);
        SilentRotationService.add(30.123f, 2.123f);
        SilentRotationService.applyPending(context, true);
        float aimYaw = player.rotationYaw, aimPitch = player.rotationPitch;
        near(aimYaw, 40, "quantized physical aim");
        near(SilentRotationService.cameraYaw(), 10, "camera stays at manual view");
        SilentRotationService.prepareMouse(context);
        near(player.rotationYaw, 10, "vanilla mouse sees camera yaw");
        player.setAngles(20, -10);
        SilentRotationService.beginCamera(context);
        near(player.rotationYaw, 13, "camera captures vanilla mouse delta");
        near(player.rotationPitch, 21.5f, "vanilla pitch sign");
        SilentRotationService.endCamera();
        near(player.rotationYaw, aimYaw, "render restores physical aim");
        near(player.rotationPitch, aimPitch, "render restores physical pitch");
        near(SilentRotationService.storedYaw(), 10, "retained view changes only on entity phase");
        near(SilentRotationService.cameraYaw(), 13, "camera may differ from retained view between ticks");
        request.yaw = 13; request.pitch = 21.5f;
        SilentRotationService.finishFrame(context, request);
        near(SilentRotationService.storedYaw(), 13, "entity phase updates retained view");

        player.rotationYaw = 103;
        KeyBindUtil.setPressed(g.keyBindForward, true);
        SilentRotationService.beginMovement(context);
        check(!g.keyBindForward.isKeyDown() && g.keyBindLeft.isKeyDown(), "90 degree silent aim remaps forward to left");
        mc.inGameHasFocus = false;
        SilentRotationService.endMovement(context);
        check(g.keyBindForward.isKeyDown() && !g.keyBindLeft.isKeyDown(), "restore original key state even after focus loss");
        check(SilentRotationService.remapMask(1, -90) == 8, "negative quarter turn");
        check(SilentRotationService.remapMask(1, 180) == 2, "half turn");
        check(SilentRotationService.remapMask(3, 90) == 0, "opposite directions cancel");
        mc.inGameHasFocus = true;
        SilentRotationService.beginRenderInfo(context);
        near(player.rotationYaw, 13, "render info uses camera");
        SilentRotationService.endRender();
        near(player.rotationYaw, 103, "frame end restores render info after early return");
        SilentRotationService.finishFrame(context, new SilentAimFrame());
        near(player.rotationYaw, 13, "release restores camera angle");
        check(!SilentRotationService.storedRotationValid(), "release ends silent state");
        SilentRotationService.reset(context);
        check(SilentRotationService.pendingYaw() == 0 && SilentRotationService.manualYaw() == 0
                && SilentRotationService.allows(0), "world/disable clears deltas and ownership");
        // Fractions smaller than a mouse quantum must survive until enough accumulate.
        SilentRotationService.beginFrame(context, 0, 0);
        player.rotationYaw = 0;
        SilentRotationService.add(0.10f, 0); SilentRotationService.applyPending(context);
        near(player.rotationYaw, 0, "small fractional step is retained");
        SilentRotationService.add(0.10f, 0); SilentRotationService.applyPending(context);
        check(player.rotationYaw > 0 && SilentRotationService.pendingYaw() > 0, "fractional steps accumulate without truncation loss");
        SilentRotationService.reset(context);
    }

    private static void hookIntegration() throws Exception {
        Hooks.register();
        ClassNode renderer = transformed("net.minecraft.client.renderer.EntityRenderer");
        check(call(method(renderer, "getMouseOver"), "beforeRay") >= 0, "ray phase installed on actual renderer");
        check(call(method(renderer, "updateCameraAndRender"), "prepareMouse") >= 0, "mouse preparation installed");
        check(call(method(renderer, "orientCamera"), "beginCamera") >= 0
                && call(method(renderer, "orientCamera"), "endCamera") >= 0, "camera scope installed");
        check(!method(renderer, "orientCamera").tryCatchBlocks.isEmpty(), "camera scope includes exceptional exits");
        ClassNode minecraft = transformed("net.minecraft.client.Minecraft");
        MethodNode display = method(minecraft, "updateDisplay");
        check(call(display, "beforeDisplay") >= 0, "display phase installed");
        ClassNode world = transformed("net.minecraft.world.World");
        MethodNode entities = method(world, "updateEntities");
        check(call(entities, "afterEntities") >= 0, "target selection installed after actual world entities");
        for (AbstractInsnNode n : entities.instructions.toArray()) {
            if (n instanceof MethodInsnNode && ((MethodInsnNode)n).owner.equals(SilentAimCallbacks.OWNER)
                    && ((MethodInsnNode)n).name.equals("afterEntities")) {
                check(n.getPrevious() instanceof VarInsnNode && ((VarInsnNode)n.getPrevious()).var == 0,
                        "entity phase receives the actual owning world");
            }
        }
    }

    public static void cleanupProbe() { cleanupCount++; }

    private static void finallyVerifier() throws Exception {
        String owner = "net.minecraft.client.renderer.EntityRenderer";
        List<HookRegistry.Hook> hooks = HookRegistry.forOwner(owner);
        List<HookRegistry.Hook> saved = new ArrayList<>(hooks);
        hooks.clear();
        try {
            HookRegistry.hook(owner, "probe", "(Z)V").in("myau/slinky/silent/SilentAimRegressionTest")
                    .at(HookRegistry.Position.FINALLY).calls("cleanupProbe").add();
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner.replace('.', '/'), null, "java/lang/Object", null);
            MethodVisitor m = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "probe", "(Z)V", null, null);
            m.visitCode(); m.visitVarInsn(Opcodes.ILOAD, 0); Label end = new Label(); m.visitJumpInsn(Opcodes.IFEQ, end);
            m.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException"); m.visitInsn(Opcodes.DUP);
            m.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false);
            m.visitInsn(Opcodes.ATHROW); m.visitLabel(end); m.visitInsn(Opcodes.RETURN); m.visitMaxs(0,0); m.visitEnd(); writer.visitEnd();
            byte[] transformed = new HookTransformer(new Class<?>[0]).transform(null, owner.replace('.', '/'), null, null, writer.toByteArray());
            check(transformed != null, "finally transform produced class bytes");
            Class<?> fixture = new ClassLoader(SilentAimRegressionTest.class.getClassLoader()) {
                Class<?> define(byte[] bytes) { return defineClass(owner, bytes, 0, bytes.length); }
            }.define(transformed);
            fixture.getMethod("probe", boolean.class).invoke(null, false);
            check(cleanupCount == 1, "normal return cleanup executes exactly once");
            try { fixture.getMethod("probe", boolean.class).invoke(null, true); throw new AssertionError("expected failure"); }
            catch (InvocationTargetException e) { check(e.getCause() instanceof IllegalStateException, "original render failure preserved"); }
            check(cleanupCount == 2, "exceptional cleanup executes exactly once and verifies on JVM");
        } finally { hooks.clear(); hooks.addAll(saved); }
    }

    private static ClassNode transformed(String owner) throws Exception {
        byte[] result = new HookTransformer(new Class<?>[0]).transform(null, owner.replace('.', '/'), null, null, bytes(owner.replace('.', '/')));
        check(result != null, "actual fixture transformed: " + owner);
        ClassNode node = new ClassNode(); new ClassReader(result).accept(node, 0); return node;
    }
    private static int call(MethodNode method, String name) {
        int i=0; for (AbstractInsnNode n : method.instructions.toArray()) {
            if (n instanceof MethodInsnNode && ((MethodInsnNode)n).owner.equals(SilentAimCallbacks.OWNER) && ((MethodInsnNode)n).name.equals(name)) return i;
            i++;
        } return -1;
    }
    private static MethodNode method(ClassNode node, String name) {
        for (MethodNode m : node.methods) if (m.name.equals(name)) return m;
        throw new AssertionError(name);
    }
    private static byte[] bytes(String name) throws Exception {
        try (InputStream in = SilentAimRegressionTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(in != null, name); ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int n; while ((n=in.read(buffer)) >= 0) out.write(buffer,0,n); return out.toByteArray();
        }
    }
    private static void near(float actual, float expected, String message) { check(Math.abs(actual-expected)<0.16f, message + ": " + actual); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
