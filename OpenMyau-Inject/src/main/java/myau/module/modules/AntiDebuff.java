package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S1DPacketEntityEffect;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.StatCollector;

public class AntiDebuff extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Myau", "Vape"});
    public final BooleanProperty blindness = new BooleanProperty("blindness", true);
    public final BooleanProperty nausea = new BooleanProperty("nausea", true);
    public final BooleanProperty slowness = new BooleanProperty("slowness", true, () -> mode.getValue() == 1);
    public final BooleanProperty alertInChat = new BooleanProperty("alert-in-chat", true, () -> mode.getValue() == 1);

    public AntiDebuff() {
        super("Anti Debuff", false);
    }

    private boolean removes(int id) {
        return id == Potion.blindness.id && blindness.getValue()
                || id == Potion.confusion.id && nausea.getValue()
                || id == Potion.moveSlowdown.id && slowness.getValue();
    }

    private boolean vapeActive() { return isEnabled() && mode.getValue() == 1; }

    /** Camera/overlay HEAD, shared by injected and Mixin paths. */
    public static void purge() {
        if (Myau.moduleManager == null) return;
        AntiDebuff module = (AntiDebuff) Myau.moduleManager.modules.get(AntiDebuff.class);
        if (module != null) module.removeEffects();
    }

    private void removeEffects() {
        if (!vapeActive() || mc == null || mc.thePlayer == null || mc.theWorld == null) return;
        for (Potion potion : new Potion[]{Potion.blindness, Potion.confusion, Potion.moveSlowdown}) {
            PotionEffect effect = mc.thePlayer.getActivePotionEffect(potion);
            if (effect != null && removes(potion.id)) {
                mc.thePlayer.removePotionEffectClient(potion.id);
                alert(potion.id, effect.getAmplifier(), effect.getDuration());
            }
        }
    }

    private void alert(int id, int amplifier, int duration) {
        if (!alertInChat.getValue() || id < 0 || id >= Potion.potionTypes.length || Potion.potionTypes[id] == null) return;
        ChatUtil.sendFormatted(Myau.clientName + "&bAntiDebuff&7: Removing &d"
                + StatCollector.translateToLocal(Potion.potionTypes[id].getName()) + " " + (amplifier + 1)
                + " &7for &b" + String.format(java.util.Locale.ROOT, "%.1f", duration / 20.0) + "&7s.");
    }

    @EventTarget
    public void onTick(TickEvent event) { removeEffects(); }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!vapeActive() || mc.thePlayer == null || event.getType() != EventType.RECEIVE
                || event.isCancelled() || !(event.getPacket() instanceof S1DPacketEntityEffect)) return;
        S1DPacketEntityEffect packet = (S1DPacketEntityEffect) event.getPacket();
        if (packet.getEntityId() != mc.thePlayer.getEntityId() || !removes(packet.getEffectId() & 255)) return;
        event.setCancelled(true);
        EntityPlayerSP player = mc.thePlayer;
        Object world = mc.theWorld;
        Runnable notify = () -> {
            if (vapeActive() && mc.thePlayer == player && mc.theWorld == world) {
                removeEffects();
                alert(packet.getEffectId() & 255, packet.getAmplifier() & 255, packet.getDuration());
            }
        };
        if (mc.isCallingFromMinecraftThread()) notify.run(); else mc.addScheduledTask(notify);
    }

    @Override
    public void onEnabled() { removeEffects(); }

    @Override
    public void verifyValue(String name) { removeEffects(); }
}
