package myau.module.modules;

import myau.access.AccessorPlayerControllerMP;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.BadPacketsUtil;
import myau.util.PacketUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.List;

public class AutoPot extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final FloatProperty health = new FloatProperty("health", 15.0F, 1.0F, 20.0F);
    public final IntProperty delay = new IntProperty("delay", 1000, 50, 5000);
    private static final int ROTATION_PRIORITY = 7;
    private final TimerUtil throwTimer = new TimerUtil();
    public AutoPot() {
        super("Auto Pot", false);
    }
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.thePlayer.inventory == null) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        if (mc.thePlayer.getHealth() > this.health.getValue()) {
            return;
        }
        if (!this.throwTimer.hasTimeElapsed(this.delay.getValue().longValue())) {
            return;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null) {
                continue;
            }
            Item item = stack.getItem();
            if (!(item instanceof ItemPotion)) {
                continue;
            }
            ItemPotion potion = (ItemPotion) item;
            if (!ItemPotion.isSplash(stack.getMetadata())) {
                continue;
            }
            List<PotionEffect> effects = potion.getEffects(stack);
            if (effects.isEmpty()) {
                continue;
            }
            PotionEffect effect = effects.get(0);
            if (!isGoodEffect(effect.getPotionID())) {
                continue;
            }
            if (mc.thePlayer.isPotionActive(effect.getPotionID())) {
                continue;
            }
            this.throwPotion(event, i, stack);
            break;
        }
    }
    private void throwPotion(UpdateEvent event, int slot, ItemStack stack) {
        event.setRotation(mc.thePlayer.rotationYaw, 90.0F, ROTATION_PRIORITY);
        if (!(event.getNewPitch() > 85.0F) || BadPacketsUtil.bad(false, true, false, true, false)) {
            return;
        }
        int originalSlot = mc.thePlayer.inventory.currentItem;
        if (originalSlot != slot) {
            mc.thePlayer.inventory.currentItem = slot;
            PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
        }
        AccessorPlayerControllerMP.callSyncCurrentPlayItem(mc.playerController);
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(stack));
        if (originalSlot != slot) {
            mc.thePlayer.inventory.currentItem = originalSlot;
            PacketUtil.sendPacket(new C09PacketHeldItemChange(originalSlot));
            AccessorPlayerControllerMP.callSyncCurrentPlayItem(mc.playerController);
        }
        this.throwTimer.reset();
    }
    private boolean isGoodEffect(int potionId) {
        return potionId == Potion.heal.id
                || potionId == Potion.regeneration.id
                || potionId == Potion.resistance.id
                || potionId == Potion.fireResistance.id
                || potionId == Potion.moveSpeed.id
                || potionId == Potion.damageBoost.id
                || potionId == Potion.jump.id;
    }
}
