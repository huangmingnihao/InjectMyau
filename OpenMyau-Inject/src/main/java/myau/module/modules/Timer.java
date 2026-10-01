package myau.module.modules;

import myau.access.AccessorMinecraft;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;

public class Timer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float NORMAL = 1.0F;
    public final FloatProperty speed = new FloatProperty("speed", 1.0F, 0.1F, 2.0F);
    public Timer() {
        super("Timer", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }
        AccessorMinecraft.getTimer(mc).timerSpeed = this.speed.getValue();
    }
    @Override
    public void onDisabled() {
        AccessorMinecraft.getTimer(mc).timerSpeed = NORMAL;
    }
    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%.1f", this.speed.getValue())};
    }
}
