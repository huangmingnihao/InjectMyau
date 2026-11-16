package myau.inject;

import myau.util.StuckFreezeState;
import myau.util.StuckPacketPolicy;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class StuckRegressionTest {
    public static void main(String[] args) throws Exception {
        lifecycle();
        packets();
        hooks();
        System.out.println("Stuck checks passed (rapid toggles, pulse/recovery, silent look packets, actual injected input ordering, Mixin parity)");
    }

    private static void lifecycle() {
        StuckFreezeState state = new StuckFreezeState();
        for (int i = 0; i < 100; i++) {
            state.enable();
            state.tick(true, 20);
            check(!state.isFrozen(), "rapid re-enable must allow a normal physics update before capture");
            state.reset();
            check(!state.isFrozen() && !state.blocksInput(), "disable leaves no freeze or input latch");
        }
        state.enable(); state.tick(true, 20); state.tick(true, 20);
        check(state.isFrozen(), "freeze starts on second update");
        for (int i = 1; i < 20; i++) {
            state.tick(true, 20);
            check(state.isFrozen(), "retain freeze for configured interval");
        }
        state.tick(true, 20);
        check(!state.isFrozen() && state.blocksInput(), "pulse releases physics for one update with zero input");
        state.tick(true, 20);
        check(state.isFrozen(), "pulse recaptures after one physics update");
        for (int delay : new int[]{3, 4}) {
            state.resumeAfter(delay);
            for (int i = 1; i < delay; i++) {
                state.tick(false, 20);
                check(!state.isFrozen(), "server correction/velocity gets recovery updates");
            }
            state.tick(false, 20);
            check(state.isFrozen(), "refreeze after server recovery");
        }
        for (int i = 0; i < 100; i++) {
            state.tick(false, 20);
            check(state.isFrozen(), "NORMAL does not pulse");
        }
        state.tick(true, 0);
        check(!state.isFrozen(), "zero pulse delay preserves legacy pass-through");
    }

    private static void packets() {
        C03PacketPlayer[] sources = {
            new C03PacketPlayer(false),
            new C03PacketPlayer.C04PacketPlayerPosition(12, 70, -8, true),
            new C03PacketPlayer.C05PacketPlayerLook(-135F, 83F, false),
            new C03PacketPlayer.C06PacketPlayerPosLook(12, 70, -8, 225F, 80F, true)
        };
        for (C03PacketPlayer source : sources) {
            C03PacketPlayer look = StuckPacketPolicy.lookOnly(source, 17F, 23F);
            check(look instanceof C03PacketPlayer.C05PacketPlayerLook, "freeze sends only look");
            check(!look.isMoving() && look.getRotating(), "no frozen position update");
            check(look.isOnGround() == source.isOnGround(), "retain grounded state");
            check(look.getYaw() == (source.getRotating() ? source.getYaw() : 17F), "retain silent yaw");
            check(look.getPitch() == (source.getRotating() ? source.getPitch() : 23F), "retain silent pitch");
            check(StuckPacketPolicy.lookOnly(look, 0, 0) == look, "look replacement cannot recurse");
        }
    }

    private static void hooks() throws Exception {
        Hooks.register();
        String player = "net.minecraft.client.entity.EntityPlayerSP";
        List<HookRegistry.Hook> hooks = HookRegistry.forOwner(player);
        List<HookRegistry.Hook> saved = new ArrayList<>(hooks);
        try {
            hooks.removeIf(h -> !Arrays.asList("onUpdatePre", "onUpdatePost", "onMoveInput",
                    "updatePlayerMoveState", "preLivingUpdate", "onMotionUpdate").contains(h.callbackName));
            byte[] transformed = new HookTransformer(new Class<?>[0]).transform(null,
                    player.replace('.', '/'), null, null, bytes(player.replace('.', '/')));
            check(transformed != null, "transform actual player fixture");
            ClassNode node = read(transformed);
            MethodNode living = method(node, "onLivingUpdate");
            int input = callIndex(living, StuckCallbacks.OWNER, "updatePlayerMoveState");
            int postInput = callIndex(living, LocalPlayerCallbacks.OWNER, "onMoveInput");
            check(input >= 0 && postInput > input, "final input event runs after replaced native input");
            check(callIndex(method(node, "onUpdate"), LocalPlayerCallbacks.OWNER, "onUpdatePre") >= 0,
                    "early freeze lifecycle hook installed");
            // Both paths must protect silent rotation bookkeeping during SmartStuck.
            for (String name : new String[]{"myau/inject/LocalPlayerCallbacks", "myau/mixin/MixinEntityPlayerSP"}) {
                ClassNode path = read(bytes(name));
                MethodNode post = method(path, name.contains("mixin") ? "postUpdate" : "onUpdatePost");
                check(callIndex(post, "myau/module/modules/LeaderStuck", "isFrozen") >= 0, "freeze guard in " + name);
            }
        } finally { hooks.clear(); hooks.addAll(saved); }
    }
    private static int callIndex(MethodNode method, String owner, String name) {
        int index = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (owner.equals(call.owner) && name.equals(call.name)) return index;
            }
            index++;
        }
        return -1;
    }
    private static MethodNode method(ClassNode node, String name) {
        for (MethodNode method : node.methods) if (method.name.equals(name)) return method;
        throw new AssertionError("Missing method " + name);
    }
    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node;
    }
    private static byte[] bytes(String name) throws Exception {
        try (InputStream input = StuckRegressionTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError(name);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = input.read(buf)) >= 0) output.write(buf, 0, n);
            return output.toByteArray();
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
