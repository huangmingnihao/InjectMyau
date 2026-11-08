package myau.inject;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import myau.events.MoveInputEvent;
import myau.events.LoadWorldEvent;
import myau.property.properties.BooleanProperty;
import myau.util.SneakCameraAnimation;

/** Checks the camera hook against the real Minecraft renderer without starting OpenGL. */
public final class LegitScaffoldCameraTest {
    private static final String RENDERER = "net.minecraft.client.renderer.EntityRenderer";
    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String MODULE = "myau/module/modules/LegitScaffold";
    private static final String GL = "net/minecraft/client/renderer/GlStateManager";

    public static void main(String[] args) throws Exception {
        Hooks.register();
        List<HookRegistry.Hook> hooks = HookRegistry.forOwner(RENDERER);
        List<HookRegistry.Hook> original = new ArrayList<>(hooks);
        try {
            hooks.removeIf(hook -> !hook.callbackName.equals("cameraTranslate"));
            check(hooks.size() == 1, "install exactly one camera translation hook");
            rendererRegression(bytes(RENDERER.replace('.', '/')), false);
            if (args.length > 0) {
                rendererRegression(Files.readAllBytes(Paths.get(args[0])), true);
                System.out.println("Lunar renderer dump: final translation hooked after onTranslateEyeHeight");
            }
            check(calls(read("myau/mixin/MixinEntityRenderer"), "cameraTranslate",
                            MODULE, "getCameraTranslationY") == 1,
                    "Mixin camera must use the same implementation");
            check(calls(read(CameraCallbacks.OWNER), "cameraTranslate",
                            MODULE, "getCameraTranslationY") == 1,
                    "injected camera must use the same implementation");
            stateRegression();
            System.out.println("LegitScaffold camera checks passed (real renderer, late launcher overrides, held sneak releases, state cleanup)");
        } finally {
            hooks.clear();
            hooks.addAll(original);
        }
    }

    private static void rendererRegression(byte[] source, boolean lunar) {
        ClassNode before = new ClassNode();
        new ClassReader(source).accept(before, 0);
        byte[] transformed = new HookTransformer(new Class<?>[0]).transform(
                null, RENDERER.replace('.', '/'), null, null, source);
        check(transformed != null, "hook must match the actual renderer");
        ClassNode after = new ClassNode();
        new ClassReader(transformed).accept(after, 0);
        int translations = calls(before, "orientCamera", GL, "translate");
        check(translations > 0, "fixture must include camera translations");
        check(calls(after, "orientCamera", GL, "translate") == 0,
                "intercept the actual camera translations");
        check(calls(after, "orientCamera", CameraCallbacks.OWNER, "cameraTranslate") == translations,
                "each translation must be forwarded exactly once");
        for (String method : new String[]{"orientCamera", "getMouseOver"}) {
            check(calls(after, method, ENTITY, "getEyeHeight")
                            == calls(before, method, ENTITY, "getEyeHeight"),
                    "preserve original eye-height reads in " + method);
        }
        if (lunar) {
            boolean lateOverride = false;
            boolean finalTranslation = false;
            for (MethodNode method : after.methods) {
                if (!method.name.equals("orientCamera")) continue;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.name.endsWith("$lunar$onTranslateEyeHeight")) lateOverride = true;
                    if (lateOverride && call.owner.equals(CameraCallbacks.OWNER)
                            && call.name.equals("cameraTranslate")) finalTranslation = true;
                }
            }
            check(finalTranslation, "apply after Lunar discards the earlier eye height");
        }
    }

    private static ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(bytes(name)).accept(node, 0);
        return node;
    }

    private static byte[] bytes(String name) throws Exception {
        try (InputStream input = LegitScaffoldCameraTest.class.getClassLoader()
                .getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing compiled class: " + name);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int size;
            while ((size = input.read(buffer)) != -1) output.write(buffer, 0, size);
            return output.toByteArray();
        }
    }

    private static int calls(ClassNode node, String methodName, String owner, String name) {
        int count = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals(methodName)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(owner) && call.name.equals(name)
                        && (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        || call.getOpcode() == Opcodes.INVOKESTATIC)) count++;
            }
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void stateRegression() throws Exception {
        final String fixtureName = "myau/inject/LegitScaffoldStateFixture";
        Map<String, String> names = new HashMap<>();
        names.put(MODULE, fixtureName);
        names.put("myau/module/Module", name(Harness.class));
        names.put("myau/Myau", name(Globals.class));
        names.put("myau/module/ModuleManager", name(Manager.class));
        names.put("net/minecraft/client/Minecraft", name(Client.class));
        names.put("net/minecraft/client/settings/GameSettings", name(Settings.class));
        names.put("net/minecraft/client/entity/EntityPlayerSP", name(Player.class));
        names.put("net/minecraft/entity/Entity", name(Player.class));
        names.put("net/minecraft/client/multiplayer/WorldClient", "java/lang/Object");
        names.put("net/minecraft/client/gui/GuiScreen", "java/lang/Object");
        names.put("net/minecraft/util/MovementInput", name(Input.class));
        names.put(GL, name(Translations.class));
        names.put("java/lang/System", name(Clock.class));
        SimpleRemapper remapper = new SimpleRemapper(names);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, fixtureName, null, name(Harness.class), null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, name(Harness.class), "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        int copied = 0;
        for (MethodNode method : read(MODULE).methods) {
            if (!java.util.Arrays.asList("getCameraTranslationY", "onMoveInput", "resetSneakState",
                    "onDisabled", "onLoadWorld").contains(method.name)) continue;
            method.visibleAnnotations = null;
            method.invisibleAnnotations = null;
            MethodVisitor visitor = writer.visitMethod(method.access, method.name,
                    remapper.mapMethodDesc(method.desc), null, null);
            method.accept(new MethodRemapper(Opcodes.ASM5, visitor, remapper) {});
            copied++;
        }
        check(copied == 5, "execute the production input, camera and cleanup methods");
        for (MethodNode method : read(CameraCallbacks.OWNER).methods) {
            if (!method.name.equals("cameraTranslate")) continue;
            MethodVisitor visitor = writer.visitMethod(method.access, method.name, method.desc, null, null);
            method.accept(new MethodRemapper(Opcodes.ASM5, visitor, remapper) {});
        }
        // A launcher overwrites the initial eye height just before the final GL translation.
        // Copy the transformed method into the same executable fixture as the production code.
        ClassNode cameraPath = launcherCameraPath();
        ClassWriter source = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cameraPath.accept(source);
        byte[] transformed = new HookTransformer(new Class<?>[0]).transform(
                null, RENDERER.replace('.', '/'), null, null, source.toByteArray());
        check(transformed != null, "hook must install on the launcher execution fixture");
        ClassNode hooked = new ClassNode();
        new ClassReader(transformed).accept(hooked, 0);
        names.put(CameraCallbacks.OWNER, fixtureName);
        names.put(cameraPath.name, fixtureName);
        for (MethodNode method : hooked.methods) {
            MethodVisitor visitor = writer.visitMethod(method.access, method.name, method.desc, null, null);
            method.accept(new MethodRemapper(Opcodes.ASM5, visitor, new SimpleRemapper(names)) {});
        }
        writer.visitEnd();
        Class<?> fixture = new ClassLoader(LegitScaffoldCameraTest.class.getClassLoader()) {
            Class<?> define(byte[] data) { return defineClass(fixtureName.replace('/', '.'), data, 0, data.length); }
        }.define(writer.toByteArray());
        Harness module = (Harness) fixture.newInstance();
        Clock.nowNanos = 0L;
        Harness.mc = new Client();
        Globals.moduleManager = new Manager();
        Globals.moduleManager.modules.put(fixture, module);
        java.lang.reflect.Method camera = fixture.getMethod("getCameraTranslationY",
                Player.class, float.class, float.class, float.class);
        java.lang.reflect.Method render = fixture.getMethod("orientCamera", float.class);
        Player player = Harness.mc.thePlayer;

        // Emulate the physical sneak key being held on every tick, then overwritten by the launcher.
        for (int tick = 0; tick < 120; tick++) {
            player.ticksExisted = tick;
            player.movementInput.sneak = true;
            player.movementInput.moveForward = 0.3F;
            module.edge = tick % 4 < 2;
            module.onMoveInput(new MoveInputEvent());
            check(player.movementInput.sneak == module.edge, "safe blocks must release a held sneak key");
            near(player.movementInput.moveForward, module.edge ? 0.3F : 1.0F, "slow down exactly once");
            player.movementInput.sneak = true;
            near(settledCameraHeight(camera, player), module.edge ? 1.54F : 1.62F,
                    "camera must retain the automatic release even if input is overwritten");
            Translations.count = 0;
            render.invoke(module, tick % 3 == 0 ? 1.62F : 1.54F);
            check(Translations.count == 2, "forward each GL translation exactly once");
            near(Translations.y, module.edge ? -1.54F : -1.62F,
                    "final rendered height must survive late launcher overrides");
        }
        module.edge = false;
        module.sneakDelay = 2;
        module.onMoveInput(new MoveInputEvent());
        near(settledCameraHeight(camera, player), 1.54F, "retain configured unsneak delay");
        module.sneakDelay = 0;
        player.movementInput.sneak = false;
        player.movementInput.moveForward = 1.0F;
        module.onMoveInput(new MoveInputEvent());
        near(player.movementInput.moveForward, 1.0F, "already released input must not be accelerated");
        near(settledCameraHeight(camera, player), 1.62F, "stand after delay expires");
        near((Float) camera.invoke(null, player, 0.0F, 0.0F, -0.1F), 0.0F,
                "preserve first-person forward translation");
        near((Float) camera.invoke(null, player, 0.0F, 0.3F, 0.0F), 0.3F,
                "preserve positive sleeping translation");
        near((Float) camera.invoke(null, player, 1.0F, -1.0F, 0.0F), -1.0F,
                "preserve translations that are not purely vertical");
        near((Float) camera.invoke(null, new Player(), 0.0F, -1.0F, 0.0F), -1.0F,
                "preserve other camera entities");
        player.ticksExisted += 3;
        near(cameraHeight(camera, player), player.originalHeight, "expire stale snapshots");
        module.onMoveInput(new MoveInputEvent());
        Harness.mc.gameSettings.thirdPersonView = 1;
        near(cameraHeight(camera, player), player.originalHeight, "preserve third person");
        Harness.mc.gameSettings.thirdPersonView = 0;
        player.sleeping = true;
        near(cameraHeight(camera, player), player.originalHeight, "preserve sleeping camera");
        player.sleeping = false;
        module.visualSneak.setValue(false);
        near(cameraHeight(camera, player), player.originalHeight, "disabled setting returns original height");
        render.invoke(module, 1.23F);
        near(Translations.y, -1.23F, "disabled setting preserves the launcher's final height");
        module.visualSneak.setValue(true);
        module.onDisabled();
        check(module.visualPlayer == null && module.sneakDelay == 0, "disable clears pose and delay");
        module.onMoveInput(new MoveInputEvent());
        module.onLoadWorld(new LoadWorldEvent());
        check(module.visualPlayer == null, "world changes clear the previous player's pose");
        Harness.mc.currentScreen = new Object();
        module.onMoveInput(new MoveInputEvent());
        check(module.visualPlayer == null, "GUI input must not leave a pose snapshot");
    }

    private static float cameraHeight(java.lang.reflect.Method camera, Player player) throws Exception {
        return -(Float) camera.invoke(null, player, 0.0F, -player.originalHeight, 0.0F);
    }

    private static float settledCameraHeight(java.lang.reflect.Method camera, Player player) throws Exception {
        cameraHeight(camera, player);
        Clock.nowNanos += 1_000_000_000L;
        return cameraHeight(camera, player);
    }

    private static ClassNode launcherCameraPath() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "myau/inject/LauncherCameraFixture",
                null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "orientCamera", "(F)V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.FCONST_0);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitLdcInsn(-0.1F);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, GL, "translate", "(FFF)V", false);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitLdcInsn(-1.62F);
        method.visitVarInsn(Opcodes.FLOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, name(Translations.class),
                "launcherOverride", "(FF)F", false);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, GL, "translate", "(FFF)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        ClassNode node = new ClassNode();
        new ClassReader(writer.toByteArray()).accept(node, 0);
        return node;
    }

    private static String name(Class<?> type) { return type.getName().replace('.', '/'); }
    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.00001F, message + ": " + actual);
    }

    public static class Harness {
        public static Client mc;
        public final BooleanProperty visualSneak = new BooleanProperty("visual-sneak", true);
        public final BooleanProperty sneakOnly = new BooleanProperty("sneaking-only", true);
        public Player visualPlayer;
        public int visualTick = Integer.MIN_VALUE;
        public boolean visualSneaking;
        public final SneakCameraAnimation cameraAnimation = new SneakCameraAnimation();
        public int sneakDelay;
        public boolean edge;
        public boolean isEnabled() { return true; }
        public boolean shouldSneak() { return true; }
        public boolean canMoveSafely() { return edge; }
        public void onMoveInput(MoveInputEvent event) {}
        public void onDisabled() {}
        public void onLoadWorld(LoadWorldEvent event) {}
    }
    public static final class Globals { public static Manager moduleManager; }
    public static final class Clock {
        static long nowNanos;
        public static long nanoTime() { return nowNanos; }
    }
    public static final class Manager { public final LinkedHashMap<Class<?>, Object> modules = new LinkedHashMap<>(); }
    public static final class Client {
        public static Client getMinecraft() { return Harness.mc; }
        public Player getRenderViewEntity() { return thePlayer; }
        public Player thePlayer = new Player();
        public Object theWorld = new Object();
        public Object currentScreen;
        public Settings gameSettings = new Settings();
    }
    public static final class Translations {
        static int count;
        static float y;
        public static float launcherOverride(float ignoredOriginalY, float height) { return -height; }
        public static void translate(float x, float translatedY, float z) {
            if (count == 0) {
                near(x, 0.0F, "preserve forward translation X");
                near(translatedY, 0.0F, "preserve forward translation Y");
                near(z, -0.1F, "preserve forward translation Z");
            }
            count++;
            y = translatedY;
        }
    }
    public static final class Settings { public int thirdPersonView; }
    public static final class Player {
        public Input movementInput = new Input();
        public int ticksExisted;
        public boolean sleeping;
        public float originalHeight = 1.31F;
        public boolean isPlayerSleeping() { return sleeping; }
        public boolean isRiding() { return false; }
        public float getEyeHeight() { return originalHeight; }
    }
    public static final class Input {
        public boolean sneak;
        public float moveForward;
        public float moveStrafe;
    }
}
