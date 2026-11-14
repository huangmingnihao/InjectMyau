package myau.module.modules;

import com.google.common.base.CaseFormat;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LivingUpdateEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.access.AccessorC0DPacketCloseWindow;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.ui.clickgui.ClickGuiScreen;
import myau.util.KeyBindUtil;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;

import java.util.concurrent.ConcurrentLinkedQueue;









public class InvWalk extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int MODE_VANILLA = 0;
    private static final int MODE_LEGIT = 1;
    private static final int MODE_HYPIXEL = 2;
    private static final int MODE_LEGIT_PLUS = 3;

    private final ConcurrentLinkedQueue<C0EPacketClickWindow> clickQueue = new ConcurrentLinkedQueue<>();
    private final boolean[] movementKeys = new boolean[7];
    private boolean keysPressed = false;
    private C16PacketClientStatus pendingStatus = null;
    private int delayTicks = 0;
    private int openDelayTicks = -1;
    private int closeDelayTicks = -1;

    public final ModeProperty mode =
            new ModeProperty("mode", MODE_LEGIT, new String[]{"VANILLA", "LEGIT", "HYPIXEL", "LEGIT+"});
    public final BooleanProperty guiEnabled = new BooleanProperty("click-gui", true);
    public final BooleanProperty disSprint = new BooleanProperty("dis-sprint", true);
    public final IntProperty openDelay =
            new IntProperty("open-delay", 0, 0, 20, () -> this.mode.getValue() == MODE_LEGIT_PLUS);
    public final IntProperty closeDelay =
            new IntProperty("close-delay", 4, 0, 20, () -> this.mode.getValue() == MODE_LEGIT_PLUS);
    public final BooleanProperty lockMoveKey = new BooleanProperty("lock-move-key", false);

    public InvWalk() {
        super("InvWalk", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{
                CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())
        };
    }


    private boolean sprintModuleHolding() {
        Sprint sprint = (Sprint) Myau.moduleManager.modules.get(Sprint.class);
        return sprint != null && sprint.isEnabled();
    }

    private KeyBinding[] trackedKeys() {
        return new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump,
                mc.gameSettings.keyBindSneak,
                mc.gameSettings.keyBindSprint
        };
    }

    public void pressMovementKeys(boolean skipSneak) {
        KeyBinding[] keys = this.trackedKeys();
        for (int i = 0; i < keys.length; i++) {
            if (skipSneak && keys[i] == mc.gameSettings.keyBindSneak) {
                continue;
            }
            KeyBindUtil.updateKeyState(keys[i].getKeyCode());
        }
        if (this.sprintModuleHolding()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
        this.keysPressed = true;
    }

    public void resetMovementKeys() {
        for (int i = 0; i < this.movementKeys.length; i++) {
            this.movementKeys[i] = false;
        }
    }

    public boolean isSetMovementKeys() {
        for (boolean pressed : this.movementKeys) {
            if (pressed) {
                return true;
            }
        }
        return false;
    }

    public void storeMovementKeys() {
        KeyBinding[] keys = this.trackedKeys();
        for (int i = 0; i < keys.length && i < this.movementKeys.length; i++) {
            this.movementKeys[i] = KeyBindUtil.isKeyDown(keys[i].getKeyCode());
        }
    }

    public void restoreMovementKeys() {
        KeyBinding[] keys = this.trackedKeys();
        for (int i = 0; i < keys.length; i++) {
            KeyBindUtil.setKeyBindState(keys[i].getKeyCode(), this.movementKeys[i]);
        }
        if (this.sprintModuleHolding()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
        this.keysPressed = true;
    }

    public boolean canInvWalk() {
        if (!(mc.currentScreen instanceof GuiContainer)) {
            return false;
        }
        if (mc.currentScreen instanceof GuiContainerCreative) {
            return false;
        }
        switch (this.mode.getValue()) {
            case MODE_VANILLA:
                return true;
            case MODE_LEGIT:
                if (!(mc.currentScreen instanceof GuiInventory)) {
                    return false;
                }
                return this.pendingStatus != null && this.clickQueue.isEmpty();
            case MODE_HYPIXEL:
                return this.delayTicks == 0 && this.clickQueue.isEmpty();
            case MODE_LEGIT_PLUS:
                if (!(mc.currentScreen instanceof GuiInventory)) {
                    return false;
                }
                return this.closeDelayTicks == -1 && this.clickQueue.isEmpty();
            default:
                return false;
        }
    }

    public boolean temporaryStackIsEmpty() {
        if (mc.thePlayer.inventory.getItemStack() != null) {
            return false;
        }
        if (mc.thePlayer.inventoryContainer instanceof ContainerPlayer) {
            ContainerPlayer containerPlayer = (ContainerPlayer) mc.thePlayer.inventoryContainer;
            for (int i = 0; i < containerPlayer.craftMatrix.getSizeInventory(); i++) {
                ItemStack stack = containerPlayer.craftMatrix.getStackInSlot(i);
                if (stack != null) {
                    return false;
                }
            }
        }
        return true;
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (this.openDelayTicks >= 0) {
            this.openDelayTicks--;
            return;
        }
        while (!this.clickQueue.isEmpty()) {
            PacketUtil.sendPacketNoEvent(this.clickQueue.poll());
        }
        if (this.closeDelayTicks > 0) {
            if (this.temporaryStackIsEmpty()) {
                this.closeDelayTicks--;
            }
        } else if (this.closeDelayTicks == 0) {
            if (mc.currentScreen instanceof GuiInventory) {
                PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(0));
            }
            this.closeDelayTicks = -1;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled() || !this.disSprint.getValue()) {
            return;
        }
        if (this.canInvWalk() && mc.currentScreen != null) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            mc.thePlayer.setSprinting(false);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.currentScreen instanceof ClickGuiScreen && this.guiEnabled.getValue()) {
            this.pressMovementKeys(true);
            return;
        }

        if (this.canInvWalk()) {
            if (this.isSetMovementKeys() && this.lockMoveKey.getValue()) {
                this.restoreMovementKeys();
            } else {
                this.pressMovementKeys(true);
            }
        } else {
            if (this.keysPressed) {
                if (mc.currentScreen != null) {
                    KeyBinding.unPressAllKeys();
                } else if (this.isSetMovementKeys()) {
                    this.resetMovementKeys();
                    this.pressMovementKeys(false);
                }
                this.keysPressed = false;
            }
            if (this.pendingStatus != null) {
                PacketUtil.sendPacketNoEvent(this.pendingStatus);
                this.pendingStatus = null;
            }
            if (this.delayTicks > 0) {
                this.delayTicks--;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND) {
            return;
        }

        if (event.getPacket() instanceof C16PacketClientStatus) {
            this.storeMovementKeys();
            if (this.mode.getValue() == MODE_LEGIT || this.mode.getValue() == MODE_LEGIT_PLUS) {
                C16PacketClientStatus packet = (C16PacketClientStatus) event.getPacket();
                if (packet.getStatus() == EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                    event.setCancelled(true);
                    if (this.mode.getValue() == MODE_LEGIT) {
                        this.pendingStatus = packet;
                    }
                }
            }
        } else if (!(event.getPacket() instanceof C0EPacketClickWindow)) {
            if (event.getPacket() instanceof C0DPacketCloseWindow) {
                C0DPacketCloseWindow packet = (C0DPacketCloseWindow) event.getPacket();
                if (AccessorC0DPacketCloseWindow.getWindowId(packet) == 0) {
                    if (this.mode.getValue() == MODE_LEGIT_PLUS) {
                        if (!this.clickQueue.isEmpty()) {
                            this.clickQueue.clear();
                        }
                        if (this.openDelayTicks >= 0) {
                            this.openDelayTicks = -1;
                        }
                        if (this.closeDelayTicks >= 0) {
                            this.closeDelayTicks = -1;
                        } else {
                            event.setCancelled(true);
                        }
                    } else if (this.pendingStatus != null) {
                        this.pendingStatus = null;
                        event.setCancelled(true);
                    }
                } else {
                    if (!this.clickQueue.isEmpty()) {
                        this.clickQueue.clear();
                    }
                    if (this.openDelayTicks >= 0) {
                        this.openDelayTicks = -1;
                    }
                    if (this.closeDelayTicks >= 0) {
                        this.closeDelayTicks = -1;
                    }
                }
            }
        } else {
            C0EPacketClickWindow packet = (C0EPacketClickWindow) event.getPacket();
            switch (this.mode.getValue()) {
                case MODE_LEGIT:
                    if (packet.getWindowId() == 0) {
                        if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                            event.setCancelled(true);
                            return;
                        }
                        if (this.pendingStatus != null) {
                            KeyBinding.unPressAllKeys();
                            event.setCancelled(true);
                            this.clickQueue.offer(packet);
                        }
                    }
                    break;
                case MODE_HYPIXEL:
                    if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                        event.setCancelled(true);
                    } else {
                        KeyBinding.unPressAllKeys();
                        event.setCancelled(true);
                        this.clickQueue.offer(packet);
                        this.delayTicks = 8;
                    }
                    break;
                case MODE_LEGIT_PLUS:
                    if (packet.getWindowId() == 0) {
                        if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                            event.setCancelled(true);
                            return;
                        }
                        KeyBinding.unPressAllKeys();
                        event.setCancelled(true);
                        this.clickQueue.offer(packet);
                        if (this.closeDelayTicks < 0 && this.openDelayTicks < 0) {
                            this.pendingStatus =
                                    new C16PacketClientStatus(EnumState.OPEN_INVENTORY_ACHIEVEMENT);
                            this.openDelayTicks = this.openDelay.getValue();
                        }
                        this.closeDelayTicks = this.closeDelay.getValue();
                    }
                    break;
                default:
                    break;
            }
            if (this.pendingStatus != null) {
                PacketUtil.sendPacketNoEvent(this.pendingStatus);
                this.pendingStatus = null;
            }
        }
    }

    @Override
    public void onDisabled() {
        if (this.keysPressed) {
            if (mc.currentScreen != null) {
                KeyBinding.unPressAllKeys();
            }
            this.keysPressed = false;
        }
        if (this.pendingStatus != null) {
            PacketUtil.sendPacketNoEvent(this.pendingStatus);
            this.pendingStatus = null;
        }
        this.clickQueue.clear();
        this.delayTicks = 0;
        this.openDelayTicks = -1;
        this.closeDelayTicks = -1;
        this.resetMovementKeys();
    }
}
