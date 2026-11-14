package myau.access;

import java.lang.reflect.Field;

import myau.inject.MappingBridge;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
















public final class AccessorC0DPacketCloseWindow {

    private static final String OWNER = "net.minecraft.network.play.client.C0DPacketCloseWindow";
    private static final Field F_WINDOW_ID =
            MappingBridge.field(OWNER, "windowId", int.class);

    private AccessorC0DPacketCloseWindow() {
    }





    public static int getWindowId(C0DPacketCloseWindow packet) {
        if (packet == null) {
            return 0;
        }
        if (F_WINDOW_ID == null) {
            return 0;
        }
        try {
            return F_WINDOW_ID.getInt(packet);
        } catch (Throwable swallowed) {
            return 0;
        }
    }
}
