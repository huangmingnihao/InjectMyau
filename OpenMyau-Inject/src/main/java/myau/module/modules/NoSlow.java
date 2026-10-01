package myau.module.modules;

import myau.Myau;
import myau.enums.FloatModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LivingUpdateEvent;
import myau.events.PacketEvent;
import myau.events.PlayerUpdateEvent;
import myau.events.RightClickMouseEvent;
import myau.module.Module;
import myau.access.AccessorEntity;
import myau.util.BlockUtil;
import myau.util.MoveUtil;
import myau.util.MovementTicks;
import myau.util.ItemUtil;
import myau.util.PlayerUtil;
import myau.util.TeamUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.ItemBow;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.util.BlockPos;

public class NoSlow extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int lastSlot = -1;
    private static final int SWORD_GRIM_30 = 2;
    private static final int ITEM_GRIM_30 = 3;
    public final ModeProperty swordMode = new ModeProperty("sword-mode", 1, new String[]{"NONE", "VANILLA", "GRIM_30"});
    public final PercentProperty swordMotion = new PercentProperty("sword-motion", 100, () -> this.swordMode.getValue() != 0);
    public final BooleanProperty swordSprint = new BooleanProperty("sword-sprint", true, () -> this.swordMode.getValue() != 0);
    public final ModeProperty foodMode = new ModeProperty("food-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT", "GRIM_30"});
    public final PercentProperty foodMotion = new PercentProperty("food-motion", 100, () -> this.foodMode.getValue() != 0);
    public final BooleanProperty foodSprint = new BooleanProperty("food-sprint", true, () -> this.foodMode.getValue() != 0);
    public final ModeProperty bowMode = new ModeProperty("bow-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT", "GRIM_30"});
    public final PercentProperty bowMotion = new PercentProperty("bow-motion", 100, () -> this.bowMode.getValue() != 0);
    public final BooleanProperty bowSprint = new BooleanProperty("bow-sprint", true, () -> this.bowMode.getValue() != 0);
    public final BooleanProperty grimHeypixel = new BooleanProperty("grim-heypixel", false,
            () -> this.isGrim30Selected());
    private static final int GRIM_ROTATION_PRIORITY = 2;
    public NoSlow() {
        super("No Slowdown", false);
    }
    public boolean isSwordActive() {
        return this.swordMode.getValue() != 0 && ItemUtil.isHoldingSword();
    }
    public boolean isFoodActive() {
        return this.foodMode.getValue() != 0 && ItemUtil.isEating();
    }
    public boolean isBowActive() {
        return this.bowMode.getValue() != 0 && ItemUtil.isUsingBow();
    }

    public boolean isFloatMode() {
        return this.foodMode.getValue() == 2 && ItemUtil.isEating()
                || this.bowMode.getValue() == 2 && ItemUtil.isUsingBow();
    }
    public boolean isGrim30Selected() {
        return this.swordMode.getValue() == SWORD_GRIM_30
                || this.foodMode.getValue() == ITEM_GRIM_30
                || this.bowMode.getValue() == ITEM_GRIM_30;
    }

    public boolean isGrim30Active() {
        if (!mc.thePlayer.isUsingItem()) {
            return false;
        }
        if (ItemUtil.isHoldingSword()) {
            return this.swordMode.getValue() == SWORD_GRIM_30;
        }
        if (ItemUtil.isEating()) {
            return this.foodMode.getValue() == ITEM_GRIM_30;
        }
        return ItemUtil.isUsingBow() && this.bowMode.getValue() == ITEM_GRIM_30;
    }
    private boolean isGrim30CancelTick() {
        return MovementTicks.ground() == 1
                || MovementTicks.air() % 2 == 0 && !mc.thePlayer.onGround
                || MovementTicks.ground() % 2 == 1 && mc.thePlayer.onGround;
    }
    public boolean isAnyActive() {
        if (!mc.thePlayer.isUsingItem()) {
            return false;
        }
        if (this.isGrim30Active()) {
            return this.isGrim30CancelTick();
        }
        return this.isSwordActive() || this.isFoodActive() || this.isBowActive();
    }
    public boolean canSprint() {
        return this.isGrim30Active()
                || this.isSwordActive() && this.swordSprint.getValue()
                || this.isFoodActive() && this.foodSprint.getValue()
                || this.isBowActive() && this.bowSprint.getValue();
    }
    public int getMotionMultiplier() {
        if (ItemUtil.isHoldingSword()) {
            return this.swordMotion.getValue();
        } else if (ItemUtil.isEating()) {
            return this.foodMotion.getValue();
        } else {
            return ItemUtil.isUsingBow() ? this.bowMotion.getValue() : 100;
        }
    }
    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && this.isGrim30Active() && mc.thePlayer.moveForward > 0.0F) {
            mc.thePlayer.setSprinting(true);
        }
        if (this.isEnabled() && this.isAnyActive()) {
            float multiplier = (float) this.getMotionMultiplier() / 100.0F;
            mc.thePlayer.movementInput.moveForward *= multiplier;
            mc.thePlayer.movementInput.moveStrafe *= multiplier;
            if (!this.canSprint()) {
                mc.thePlayer.setSprinting(false);
            }
        }
    }
    @EventTarget(Priority.LOW)
    public void onGrimPlayerUpdate(PlayerUpdateEvent event) {
        if (!this.isEnabled() || !this.isGrim30Active()) {
            return;
        }
        boolean speedActive = Myau.moduleManager.modules.get(Speed.class).isEnabled();
        boolean strafingSideways = mc.gameSettings.keyBindRight.isKeyDown() || mc.gameSettings.keyBindLeft.isKeyDown();
        if (!mc.thePlayer.onGround && !strafingSideways) {
            this.aimSideways();
        }
        if (AccessorEntity.getIsInWeb(mc.thePlayer)) {
            MoveUtil.setSpeed(0.64, MoveUtil.getMoveYaw());
        }
        if (MovementTicks.ground() > 1 && !mc.gameSettings.keyBindJump.isKeyDown()) {
            MoveUtil.addSpeed(speedActive ? 1.0E-4 : 2.0E-4, MoveUtil.getMoveYaw());
            if (!strafingSideways && !(mc.thePlayer.getHeldItem() != null
                    && mc.thePlayer.getHeldItem().getItem() instanceof ItemBow)) {
                this.aimSideways();
            }
        }
    }
    private void aimSideways() {
        Myau.rotationManager.setRotation(
                mc.thePlayer.rotationYaw + 45.0F, mc.thePlayer.rotationPitch, GRIM_ROTATION_PRIORITY, false);
    }
    @EventTarget
    public void onGrimPacket(PacketEvent event) {
        if (!this.isEnabled() || !this.grimHeypixel.getValue() || event.getType() != EventType.SEND) {
            return;
        }
        if (!(event.getPacket() instanceof C0FPacketConfirmTransaction) || !mc.thePlayer.isUsingItem()) {
            return;
        }
        if (ItemUtil.isEating() || ItemUtil.isUsingBow()) {
            event.setCancelled(true);
        }
    }
    @EventTarget(Priority.LOW)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (this.isEnabled() && this.isFloatMode()) {
            int item = mc.thePlayer.inventory.currentItem;
            if (this.lastSlot != item && PlayerUtil.isUsingItem()) {
                this.lastSlot = item;
                Myau.floatManager.setFloatState(true, FloatModules.NO_SLOW);
            }
        } else {
            this.lastSlot = -1;
            Myau.floatManager.setFloatState(false, FloatModules.NO_SLOW);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            if (mc.objectMouseOver != null) {
                switch (mc.objectMouseOver.typeOfHit) {
                    case BLOCK:
                        BlockPos blockPos = mc.objectMouseOver.getBlockPos();
                        if (BlockUtil.isInteractable(blockPos) && !PlayerUtil.isSneaking()) {
                            return;
                        }
                        break;
                    case ENTITY:
                        Entity entityHit = mc.objectMouseOver.entityHit;
                        if (entityHit instanceof EntityVillager) {
                            return;
                        }
                        if (entityHit instanceof EntityLivingBase && TeamUtil.isShop((EntityLivingBase) entityHit)) {
                            return;
                        }
                }
            }
            if (this.isGrim30Selected() && !mc.thePlayer.onGround && MovementTicks.air() % 2 == 1) {
                event.setCancelled(true);
                return;
            }
            if (this.isFloatMode() && !Myau.floatManager.isPredicted() && mc.thePlayer.onGround) {
                event.setCancelled(true);
                mc.thePlayer.motionY = 0.42F;
            }
        }
    }
}
