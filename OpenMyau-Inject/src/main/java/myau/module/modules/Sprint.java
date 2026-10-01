package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.StrafeEvent;
import myau.events.TickEvent;
import myau.access.AccessorEntityLivingBase;
import myau.module.Module;
import myau.util.KeyBindUtil;
import myau.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;

public class Sprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean wasSprinting = false;
    public final BooleanProperty foxFix = new BooleanProperty("fov-fix", true);

    public Sprint() {
        super("Sprint", true, true);
    }

    public boolean shouldApplyFovFix(IAttributeInstance attribute) {
        if (!this.foxFix.getValue()) {
            return false;
        } else {
            AttributeModifier attributeModifier = AccessorEntityLivingBase.getSprintingSpeedBoostModifier(mc.thePlayer);
            return attribute.getModifier(attributeModifier.getID()) == null && this.wasSprinting;
        }
    }

    public boolean shouldKeepFov(boolean boolean2) {
        return this.foxFix.getValue() && !boolean2 && this.wasSprinting;
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
    }
    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            this.wasSprinting = mc.thePlayer.isSprinting();
        }
    }
    @Override
    public void onDisabled() {
        this.wasSprinting = false;
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
    }
}
