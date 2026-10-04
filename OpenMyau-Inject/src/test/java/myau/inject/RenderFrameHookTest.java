package myau.inject;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Verifies frame callbacks even when a renderer returns without drawing its cached HUD. */
public final class RenderFrameHookTest {
    private static final String RENDERER = "net.minecraft.client.renderer.EntityRenderer";
    private static final String FIXTURE = "myau/inject/RenderFrameFixture";

    public static void main(String[] args) throws Exception {
        Hooks.register();
        List<HookRegistry.Hook> hooks = HookRegistry.forOwner(RENDERER);
        List<HookRegistry.Hook> original = new ArrayList<>(hooks);
        try {
            hooks.removeIf(hook -> !hook.callbackName.equals("renderFramePre")
                    && !hook.callbackName.equals("renderFramePost"));
            check(hooks.size() == 2, "frame capture and dispatch must use the camera renderer");
            HookTransformer transformer = new HookTransformer(new Class<?>[0]);
            byte[] transformed = transformer.transform(null, RENDERER.replace('.', '/'), null, null, fixture());
            check(transformed != null, "frame hooks must install without any HUD calls");
            ClassNode node = new ClassNode();
            new ClassReader(transformed).accept(node, 0);
            int ends = 0;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if (call.owner.equals(Callbacks.OWNER)) {
                            if (call.name.equals("renderFramePost")) ends++;
                            // Execute the transformed bytecode without initializing Minecraft/OpenGL.
                            call.owner = "myau/inject/RenderFrameHookTest$Recorder";
                        }
                    }
                }
            }
            check(ends == 2, "both normal and early returns must dispatch the frame overlay");
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            Class<?> type = new FixtureLoader().define(writer.toByteArray());
            Object instance = type.newInstance();
            Method render = type.getMethod("updateCameraAndRender", float.class, long.class);
            for (int frame = 0; frame < 120; frame++) {
                float partialTicks = frame % 10 / 10.0F;
                render.invoke(instance, partialTicks, 0L);
                check(Recorder.frames == frame + 1, "exactly one overlay per camera frame");
                check(Recorder.partialTicks == partialTicks, "use this frame's interpolation fraction");
            }
            System.out.println("Frame render hook regression checks passed (120 frames, both return paths)");
        } finally {
            hooks.clear();
            hooks.addAll(original);
        }
    }

    private static byte[] fixture() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, FIXTURE, null, "java/lang/Object", null);
        org.objectweb.asm.MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        org.objectweb.asm.MethodVisitor render = writer.visitMethod(Opcodes.ACC_PUBLIC, "updateCameraAndRender", "(FJ)V", null, null);
        render.visitCode();
        org.objectweb.asm.Label cachedHud = new org.objectweb.asm.Label();
        render.visitVarInsn(Opcodes.FLOAD, 1);
        render.visitLdcInsn(0.5F);
        render.visitInsn(Opcodes.FCMPG);
        render.visitJumpInsn(Opcodes.IFGE, cachedHud);
        render.visitInsn(Opcodes.RETURN);
        render.visitLabel(cachedHud);
        render.visitInsn(Opcodes.RETURN);
        render.visitMaxs(0, 0);
        render.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static final class Recorder {
        static int frames;
        static float partialTicks;

        public static void renderFramePre(float partial) {
            partialTicks = partial;
        }

        public static void renderFramePost() {
            frames++;
        }
    }

    private static final class FixtureLoader extends ClassLoader {
        FixtureLoader() {
            super(RenderFrameHookTest.class.getClassLoader());
        }

        Class<?> define(byte[] bytes) {
            return defineClass(FIXTURE.replace('/', '.'), bytes, 0, bytes.length);
        }
    }
}
