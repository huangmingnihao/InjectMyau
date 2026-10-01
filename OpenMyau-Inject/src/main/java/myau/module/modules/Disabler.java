package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.TextProperty;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

public class Disabler extends Module {

    private static final int MODE_WATCHDOG = 0;

    private static final String BRAND_CHANNEL = "MC|Brand";

    private static final String MOD_CONTROL_CHANNEL = "badlion:mods";

    private static final String[] HYPIXEL_PREFIXES = {"hypixel:", "hyevent:"};

    public final ModeProperty mode = new ModeProperty("mode", MODE_WATCHDOG, new String[]{"Watchdog"});
    public final TextProperty brand = new TextProperty("brand", "vanilla",
            () -> this.mode.getValue() == MODE_WATCHDOG);

    public final BooleanProperty hypixelBrand = new BooleanProperty("Hypixel Brand", true,
            () -> this.mode.getValue() == MODE_WATCHDOG);

    public Disabler() {
        super("Disabler", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled() || this.mode.getValue() != MODE_WATCHDOG) {
            return;
        }
        if (event.getType() != EventType.RECEIVE
                || !(event.getPacket() instanceof S3FPacketCustomPayload)) {
            return;
        }

        S3FPacketCustomPayload payload = (S3FPacketCustomPayload) event.getPacket();
        String channel = payload.getChannelName();
        if (channel == null) {
            return;
        }

        if (this.hypixelBrand.getValue()) {
            if (MOD_CONTROL_CHANNEL.equalsIgnoreCase(channel)) {
                event.setCancelled(true);
                return;
            }
            String lower = channel.toLowerCase();
            for (String prefix : HYPIXEL_PREFIXES) {
                if (lower.startsWith(prefix)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (!BRAND_CHANNEL.equals(channel)) {
            return;
        }

        String replacement = this.brand.getValue();
        if (replacement == null || replacement.isEmpty()) {
            return;
        }
        try {

            PacketBuffer data = payload.getBufferData();
            data.clear();
            data.writeString(replacement);
        } catch (Throwable readOnlyOrTooSmall) {

            event.setCancelled(true);
        }
    }
}
