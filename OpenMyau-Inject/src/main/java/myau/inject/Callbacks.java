package myau.inject;

import myau.Myau;
import myau.access.AccessorMinecraft;
import myau.event.EventManager;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import org.lwjgl.input.Keyboard;
import myau.events.LeftClickMouseEvent;
import myau.events.HitBlockEvent;
import myau.events.MouseButtonEvent;
import myau.events.PreAttackEvent;
import myau.events.RightClickMouseEvent;
import myau.events.PrePlayerInteractEvent;
import myau.events.UseItemEvent;
import myau.module.modules.InventoryMove;
import myau.events.Render2DEvent;
import myau.events.Render2DPostEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.management.blockage.InboundNetworkBlockage;
import myau.management.blockage.OutboundNetworkBlockage;
import myau.module.modules.NoHitDelay;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.client.Minecraft;
import myau.util.KeyBindUtil;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Callbacks {
    public static final String OWNER = "myau/inject/Callbacks";
    private static final ThreadLocal<Boolean> SENDING_REPLACEMENT =
            new ThreadLocal<Boolean>();
    private static final Set<String> REPORTED_UNREGISTERED =
            Collections.synchronizedSet(new HashSet<String>());
    private static float overlayPartialTicks;
    private static float worldPartialTicks;
    private static int lastKey;
    private static boolean lastPressed;
    private static boolean lastSynthetic;
    private Callbacks() {
    }
    public static void tickPre() {
        try {

            Bootstrap.tick();
            NativeBridge.flushTransformLog();
            if (!Bootstrap.isStarted() || !GameState.inGame()) {
                return;
            }
            tickSequence++;
            EventManager.call(new TickEvent(EventType.PRE));
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
    }

    private static long tickSequence;
    private static long lastInteractTick = -1L;

    public static void render2DPre(float partialTicks) {
        overlayPartialTicks = partialTicks;
    }
    public static void render2DPost() {
        try {
            if (!Bootstrap.isStarted() || !GameState.inGame()) {
                return;
            }
            EventManager.call(new Render2DEvent(overlayPartialTicks));
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
    }
    public static void render2DFrameEnd() {
        try {
            if (!Bootstrap.isStarted() || !GameState.inGame()) {
                return;
            }
            EventManager.call(new Render2DPostEvent(overlayPartialTicks));
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
    }
    public static void prePlayerInteract() {
        try {
            if (!Bootstrap.isStarted() || !GameState.inGame()) {
                return;
            }
            if (lastInteractTick == tickSequence) {
                return;
            }
            lastInteractTick = tickSequence;
            EventManager.call(new PrePlayerInteractEvent());
        } catch (Throwable ignored) {
        }
    }

    public static Boolean sendUseItem(Object stack) {
        try {
            if (!Bootstrap.isStarted()) {
                return null;
            }
            UseItemEvent event = new UseItemEvent((net.minecraft.item.ItemStack) stack);
            EventManager.call(event);
            return event.isCancelled() ? Boolean.FALSE : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean rightClickMouse() {
        try {
            if (!Bootstrap.isStarted() || !GameState.inGame()) {
                return false;
            }
            RightClickMouseEvent event = new RightClickMouseEvent();
            EventManager.call(event);
            return event.isCancelled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean clickMouse() {
        try {
            if (!Bootstrap.isStarted()) {
                return false;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (Myau.moduleManager != null
                    && Myau.moduleManager.modules.get(NoHitDelay.class).isEnabled()) {
                AccessorMinecraft.setLeftClickCounter(mc, 0);
            }
            PreAttackEvent preAttack =
                    new PreAttackEvent(Minecraft.getMinecraft().objectMouseOver);
            EventManager.call(preAttack);
            if (preAttack.isCancelled()) {
                return true;
            }
            LeftClickMouseEvent event = new LeftClickMouseEvent();
            EventManager.call(event);
            return event.isCancelled();
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return false;
        }
    }

    public static boolean sendClickBlockToController() {
        try {
            if (!Bootstrap.isStarted() || !GameState.inGame()) {
                return false;
            }
            HitBlockEvent event = new HitBlockEvent();
            EventManager.call(event);
            if (event.isCancelled()) {
                Minecraft.getMinecraft().playerController.resetBlockRemoving();
                return true;
            }
            return false;
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return false;
        }
    }

    public static boolean keyBindStatePre(int key, boolean pressed) {
        lastKey = key;
        lastPressed = pressed;
        lastSynthetic = KeyBindUtil.isSynthetic();
        try {
            if (!Bootstrap.isStarted() || lastSynthetic || key >= 0) {
                return false;
            }
            MouseButtonEvent event = new MouseButtonEvent(key + 100, pressed);
            EventManager.call(event);
            return event.isCancelled() && pressed;
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return false;
        }
    }
    public static void guiKeyboardInput() {
        try {
            if (!Bootstrap.isStarted() || !Keyboard.getEventKeyState()) {
                return;
            }
            int key = Keyboard.getEventKey();
            if (key == 0) {
                return;
            }
            EventManager.call(new KeyEvent(key, true));
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
    }

    public static void keyBindStatePost() {
        int key = lastKey;
        boolean pressed = lastPressed;
        boolean synthetic = lastSynthetic;
        try {
            if (!Bootstrap.isStarted() || !pressed || synthetic) {
                return;
            }
            EventManager.call(new KeyEvent(key,
                    Minecraft.getMinecraft().currentScreen != null));
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
    }
    public static void render3DPre(float partialTicks) {
        worldPartialTicks = partialTicks;
    }
    public static void render3DPost() {
        try {
            if (!Bootstrap.isStarted() || !GameState.inGame()) {
                return;
            }
            EventManager.call(new Render3DEvent(worldPartialTicks));
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
    }
    public static void loadWorld() {
        try {
            if (!Bootstrap.isStarted()) {
                return;
            }
            OutboundNetworkBlockage.get().reset();
            InboundNetworkBlockage.get().reset();
            EventManager.call(new LoadWorldEvent());
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
    }
    public static void ingameNotInFocus() {
        try {
            if (!Bootstrap.isStarted() || !GameState.inGame()) {
                return;
            }
            InventoryMove module =
                    (InventoryMove) Myau.moduleManager.modules.get(InventoryMove.class);
            if (module != null && module.isEnabled()) {
                module.updateMovementKeyStates();
            }
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
    }
    public static boolean packetReceive(Object raw) {
        try {
            if (!Bootstrap.isStarted()) {
                return false;
            }
            Packet<?> packet = (Packet<?>) raw;
            if (packet.getClass().getName().startsWith("net.minecraft.network.play.client")) {
                return false;
            }
            if (Myau.delayManager != null
                    && Myau.delayManager.shouldDelay((Packet<INetHandlerPlayClient>) packet)) {
                return true;
            }
            PacketEvent event = new PacketEvent(EventType.RECEIVE, packet);
            EventManager.call(event);
            if (event.isCancelled()) {
                return true;
            }
            return packet.getClass().getName().startsWith("net.minecraft.network.play.server")
                    && InboundNetworkBlockage.get().isBlocked(packet);
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return false;
        }
    }
    public static boolean packetSend(Object raw) {
        try {
            if (Boolean.TRUE.equals(SENDING_REPLACEMENT.get())) {
                return false;
            }
            if (!Bootstrap.isStarted()) {
                return false;
            }
            Packet<?> packet = (Packet<?>) raw;
            if (packet.getClass().getName().startsWith("net.minecraft.network.play.server")) {
                return false;
            }
            if (rejectUnregistered(packet)) {
                return true;
            }
            PacketEvent event = new PacketEvent(EventType.SEND, packet);
            EventManager.call(event);
            if (event.isCancelled()) {
                return true;
            }
            if (packet.getClass().getName().startsWith("net.minecraft.network.play.client")
                    && OutboundNetworkBlockage.get().isBlocked(packet)) {
                return true;
            }
            return handOffToManagers(packet);
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return false;
        }
    }
    public static boolean packetSendWithListeners(Object raw) {
        try {
            if (Boolean.TRUE.equals(SENDING_REPLACEMENT.get())) {
                return false;
            }
            if (!Bootstrap.isStarted()) {
                return false;
            }
            Packet<?> packet = (Packet<?>) raw;
            if (packet.getClass().getName().startsWith("net.minecraft.network.play.server")) {
                return false;
            }
            if (rejectUnregistered(packet)) {
                return true;
            }
            return handOffToManagers(packet);
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return false;
        }
    }

    /**
     * MessageSerializer throws "Can't serialize unregistered packet" when the
     * connection state is non-null and this class has no serverbound id.
     * A same-named class from another loader is copied onto the game's class
     * and sent; anything else is dropped so the encoder does not disconnect.
     */
    private static boolean rejectUnregistered(Packet<?> packet) {
        if (serverboundId(packet) != null) {
            return false;
        }
        Packet<?> replacement = copyOntoGameClass(packet);
        if (replacement != null && serverboundId(replacement) != null) {
            SENDING_REPLACEMENT.set(Boolean.TRUE);
            try {
                Minecraft.getMinecraft().getNetHandler().getNetworkManager()
                        .sendPacket(replacement);
                report(packet, "rewrote onto the game class and sent that");
            } catch (Throwable swallowed) {
                Log.swallowed(swallowed);
            } finally {
                SENDING_REPLACEMENT.remove();
            }
            return true;
        }
        report(packet, "blocked before the encoder");
        return true;
    }

    private static io.netty.channel.Channel channelOf(NetworkManager manager) {
        if (manager == null) {
            return null;
        }
        try {
            Field field = declaredField(NetworkManager.class, io.netty.channel.Channel.class,
                    "channel", "field_150746_k", "k");
            return field == null ? null : (io.netty.channel.Channel) field.get(manager);
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return null;
        }
    }

    private static Integer serverboundId(Packet<?> packet) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getNetHandler() == null) {
                return Integer.valueOf(0);
            }
            NetworkManager manager = mc.getNetHandler().getNetworkManager();
            io.netty.channel.Channel channel = channelOf(manager);
            if (channel == null) {
                return Integer.valueOf(0);
            }
            Field keyField = declaredField(NetworkManager.class, io.netty.util.AttributeKey.class,
                    "attrKeyConnectionState", "field_150739_c", "c");
            if (keyField == null || !Modifier.isStatic(keyField.getModifiers())) {
                return Integer.valueOf(0);
            }
            @SuppressWarnings("unchecked")
            io.netty.util.AttributeKey<EnumConnectionState> key =
                    (io.netty.util.AttributeKey<EnumConnectionState>) keyField.get(null);
            EnumConnectionState state = channel.attr(key).get();
            if (state == null) {
                return Integer.valueOf(0);
            }
            EnumPacketDirection bound = serverbound();
            if (bound == null) {
                return Integer.valueOf(0);
            }
            for (Method method : state.getClass().getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 2 || params[0] != EnumPacketDirection.class
                        || !Packet.class.isAssignableFrom(params[1])) {
                    continue;
                }
                if (method.getReturnType() != Integer.class && method.getReturnType() != int.class) {
                    continue;
                }
                method.setAccessible(true);
                return (Integer) method.invoke(state, bound, packet);
            }
            return Integer.valueOf(0);
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return Integer.valueOf(0);
        }
    }

    private static EnumPacketDirection serverbound() {
        try {
            return EnumPacketDirection.valueOf("SERVERBOUND");
        } catch (IllegalArgumentException missing) {
            EnumPacketDirection[] values = EnumPacketDirection.values();
            return values.length == 0 ? null : values[0];
        }
    }

    private static Field declaredField(Class<?> owner, Class<?> type, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                if (type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (NoSuchFieldException missing) {
                // Lunar is MCP, Forge is SRG, vanilla is notch. Try the next name.
            }
        }
        for (Field field : owner.getDeclaredFields()) {
            if (type.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static Packet<?> copyOntoGameClass(Packet<?> packet) {
        Class<?> source = packet.getClass();
        ClassLoader game = NetworkManager.class.getClassLoader();
        if (game == null || source.getClassLoader() == game) {
            return null;
        }
        Class<?> target;
        try {
            target = Class.forName(source.getName(), false, game);
        } catch (ClassNotFoundException missing) {
            return null;
        }
        if (target == source || !Packet.class.isAssignableFrom(target)) {
            return null;
        }
        try {
            Constructor<?> constructor = target.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object fresh = constructor.newInstance();
            for (Class<?> type = source; type != null && type != Object.class;
                 type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Field dest;
                    try {
                        dest = target.getDeclaredField(field.getName());
                    } catch (NoSuchFieldException missing) {
                        continue;
                    }
                    dest.setAccessible(true);
                    Object value = field.get(packet);
                    if (value != null && !dest.getType().isInstance(value)) {
                        return null;
                    }
                    dest.set(fresh, value);
                }
            }
            return (Packet<?>) fresh;
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return null;
        }
    }

    private static void report(Packet<?> packet, String action) {
        Class<?> type = packet.getClass();
        String key = type.getName() + " " + System.identityHashCode(type.getClassLoader());
        if (!REPORTED_UNREGISTERED.add(key)) {
            return;
        }
        ClassLoader loader = type.getClassLoader();
        Log.line(action + ": " + type.getName()
                + " loader=" + (loader == null ? "bootstrap" : loader.getClass().getName()));
    }

    private static boolean handOffToManagers(Packet<?> packet) {
        if (Myau.playerStateManager == null || Myau.blinkManager == null
                || Myau.lagManager == null || Myau.lagManager.isFlushing()) {
            return false;
        }
        Myau.playerStateManager.handlePacket(packet);
        if (Myau.blinkManager.isBlinking() && Myau.blinkManager.offerPacket(packet)) {
            return true;
        }
        return Myau.lagManager.handlePacket(packet);
    }
    public static void tickPost() {
        try {
            if (!Bootstrap.isStarted() || !GameState.inGame()) {
                return;
            }
            EventManager.call(new TickEvent(EventType.POST));
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
    }
}
