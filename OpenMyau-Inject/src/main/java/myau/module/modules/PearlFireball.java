package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.management.blockage.OutboundNetworkBlockage;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.KeyProperty;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/** Uses the normal controller path to throw a pearl and then a Bed Wars fireball. */
public class PearlFireball extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int ROTATION_PRIORITY = 10;
    private static final int IDLE = 0;
    private static final int AIMING = 1;
    private static final int WAITING_FOR_FIREBALL = 2;
    private static final int FIREBALL_EQUIPPED = 3;
    private static final int RESTORING = 4;

    public final KeyProperty rescueKey = new KeyProperty("rescue-key", Keyboard.KEY_G);
    public final BooleanProperty silent = new BooleanProperty("silent", false);
    public final BooleanProperty moveFix = new BooleanProperty("move-fix", true, silent::getValue);
    public final IntProperty rotationSpeed = new IntProperty("rotation-speed", 60, 10, 90);
    public final IntProperty pearlDelay = new IntProperty("pearl-delay", 0, 0, 1000);
    public final IntProperty fireballDelay = new IntProperty("fireball-delay", 100, 0, 1000);

    private int stage;
    private boolean keyWasDown;
    private volatile boolean transportInterrupted;
    private volatile boolean positionCorrected;
    private EntityPlayerSP player;
    private World world;
    private float aimYaw;
    private float submittedPitch;
    private int originalSlot = -1;
    private int selectedSlot = -1;
    private int equippedTick;
    private int aimConfirmedTick;
    private int actionTick;
    private long startedAt;
    private long aimConfirmedAt;
    private long pearlSentAt;

    public PearlFireball() {
        super("Pearl Fireball", false);
    }

    @Override
    public void onEnabled() {
        reset();
        keyWasDown = rescueKey.isHeld();
    }

    @Override
    public void onDisabled() {
        restoreSlot();
        reset();
    }

    private void reset() {
        stage = IDLE;
        player = null;
        world = null;
        originalSlot = -1;
        selectedSlot = -1;
        aimConfirmedAt = 0L;
        pearlSentAt = 0L;
        transportInterrupted = false;
        positionCorrected = false;
    }

    private void restoreSlot() {
        if (player == mc.thePlayer && mc.playerController != null && originalSlot >= 0
                && player.inventory.currentItem == selectedSlot
                && selectedSlot != originalSlot) {
            player.inventory.currentItem = originalSlot;
            mc.playerController.updateController();
        }
    }

    private void abort(String reason) {
        restoreSlot();
        reset();
        ChatUtil.sendFormatted("&cPearl Fireball: " + reason);
    }

    private boolean transportBusy() {
        return Myau.blinkManager.isBlinking()
                || Myau.lagManager.isDelaying()
                || Myau.lagHandler.hasOutboundLag()
                || OutboundNetworkBlockage.get().isAnyBlockages();
    }

    @EventTarget(Priority.HIGH)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        boolean down = rescueKey.isHeld();
        boolean pressed = down && !keyWasDown;
        keyWasDown = down;

        if (stage != IDLE) {
            if (mc.thePlayer != player || mc.theWorld != world || mc.currentScreen != null
                    || player.isDead || positionCorrected || transportInterrupted || transportBusy()) {
                abort("interrupted; no further item will be used.");
                return;
            }
            if (player.inventory.currentItem != selectedSlot) {
                abort("selected hotbar slot changed.");
                return;
            }
            if (System.currentTimeMillis() - startedAt > 4000L) {
                abort("timed out.");
                return;
            }
        }
        if (!pressed || stage != IDLE || mc.thePlayer == null || mc.theWorld == null
                || mc.currentScreen != null || mc.playerController == null
                || mc.thePlayer.isDead || mc.thePlayer.isRiding()) {
            return;
        }
        if (transportBusy()) {
            ChatUtil.sendFormatted("&cPearl Fireball: outbound packets are delayed; try again later.");
            return;
        }
        if (mc.thePlayer.isUsingItem()) {
            ChatUtil.sendFormatted("&cPearl Fireball: finish using the held item first.");
            return;
        }
        int pearl = findSlot(Items.ender_pearl);
        if (pearl < 0 || findSlot(Items.fire_charge) < 0) {
            ChatUtil.sendFormatted("&cPearl Fireball: hotbar needs a pearl and a fireball.");
            return;
        }
        player = mc.thePlayer;
        world = mc.theWorld;
        originalSlot = player.inventory.currentItem;
        aimYaw = player.rotationYaw;
        startedAt = System.currentTimeMillis();
        stage = AIMING;
        equip(pearl);
    }

    private void equip(int slot) {
        selectedSlot = slot;
        player.inventory.currentItem = slot;
        mc.playerController.updateController(); // Sends the normal C09 when the slot changes.
        equippedTick = player.ticksExisted;
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || stage == IDLE || mc.thePlayer != player || mc.theWorld != world
                || mc.currentScreen != null) {
            return;
        }
        if (event.getType() == EventType.PRE) {
            float pitch = Math.max(-90.0F, event.getPitch() - rotationSpeed.getValue());
            submittedPitch = pitch;
            if (!silent.getValue()) {
                player.prevRotationYaw = player.rotationYaw = aimYaw;
                player.prevRotationPitch = player.rotationPitch = pitch;
            }
            event.setRotation(aimYaw, pitch, ROTATION_PRIORITY);
            event.setPervRotation(silent.getValue() && moveFix.getValue()
                    ? aimYaw : player.rotationYaw, ROTATION_PRIORITY);
            return;
        }
        if (event.getType() != EventType.POST) {
            return;
        }
        if (transportInterrupted || positionCorrected || transportBusy()) {
            abort("outbound packets were interrupted.");
            return;
        }
        if (player.inventory.currentItem != selectedSlot) {
            abort("selected hotbar slot changed.");
            return;
        }
        // The regular C03 must have reported the requested angle before either use.
        if (Math.abs(event.getPitch() - submittedPitch) > 0.1F
                || Math.abs(MathHelper.wrapAngleTo180_float(event.getYaw() - aimYaw)) > 0.1F
                || Math.abs(event.getPitch() + 90.0F) > 0.1F) {
            return;
        }
        long now = System.currentTimeMillis();
        if (aimConfirmedAt == 0L) {
            aimConfirmedAt = now;
            aimConfirmedTick = player.ticksExisted;
            return;
        }
        if (stage == AIMING) {
            if (player.ticksExisted <= equippedTick || player.ticksExisted <= aimConfirmedTick
                    || now - aimConfirmedAt < pearlDelay.getValue()) {
                return;
            }
            if (!useSelectedItem(Items.ender_pearl)) {
                abort("pearl use was interrupted.");
                return;
            }
            pearlSentAt = now;
            actionTick = player.ticksExisted;
            stage = WAITING_FOR_FIREBALL;
            return;
        }
        if (stage == WAITING_FOR_FIREBALL) {
            if (player.ticksExisted <= actionTick) {
                return;
            }
            int fireball = findSlot(Items.fire_charge);
            if (fireball < 0) {
                abort("fireball is no longer in the hotbar.");
                return;
            }
            equip(fireball);
            stage = FIREBALL_EQUIPPED;
            return;
        }
        if (stage == FIREBALL_EQUIPPED) {
            if (player.ticksExisted <= equippedTick
                    || now - pearlSentAt < fireballDelay.getValue()) {
                return;
            }
            if (!useSelectedItem(Items.fire_charge)) {
                abort("fireball use was interrupted.");
                return;
            }
            actionTick = player.ticksExisted;
            stage = RESTORING;
            return;
        }
        if (stage == RESTORING && player.ticksExisted > actionTick) {
            restoreSlot();
            reset();
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || stage == IDLE) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (event.getType() == EventType.RECEIVE && packet instanceof S08PacketPlayerPosLook) {
            positionCorrected = true;
        } else if (event.getType() == EventType.SEND && event.isCancelled()
                && (packet instanceof C03PacketPlayer || packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C09PacketHeldItemChange)) {
            transportInterrupted = true;
        }
    }

    private boolean useSelectedItem(Item item) {
        ItemStack stack = player.inventory.getStackInSlot(selectedSlot);
        if (stack == null || stack.stackSize <= 0 || stack.getItem() != item) {
            return false;
        }
        mc.playerController.sendUseItem(player, world, stack);
        return !transportInterrupted;
    }

    private int findSlot(Item item) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.stackSize > 0 && stack.getItem() == item) {
                return slot;
            }
        }
        return -1;
    }
}
