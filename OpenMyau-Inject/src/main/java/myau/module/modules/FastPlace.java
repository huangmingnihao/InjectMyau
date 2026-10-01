package myau.module.modules;

import myau.access.AccessorMinecraft;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.TextProperty;
import myau.util.KeyBindUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;

/**
 * RightClickDelayTickEvent → TickEvent(PRE)
 * SendPacketEvent → PacketEvent(SEND)
 */
public class FastPlace extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // BS SliderSetting("Tick delay", 1.0, 0.0, 3.0, 1.0)
    public final IntProperty tickDelay = new IntProperty("tick-delay", 1, 0, 3, 1);
    // BS SliderSetting("Activation time", "ms", 0.0, 0.0, 100.0, 5.0)
    public final IntProperty activationTime = new IntProperty("activation-time", 0, 0, 100, 5);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final BooleanProperty pitchCheck = new BooleanProperty("pitch-check", false);
    // BS ButtonSetting("Held item blacklist", false, ...) + ItemListSetting("Held items", "Items")
    public final BooleanProperty heldItemBlacklistToggle = new BooleanProperty("held-item-blacklist", false);
    public final TextProperty heldItems = new TextProperty("held-items", "");
    // BS ButtonSetting("Block blacklist", false, ...) + BlockListSetting("Blacklisted blocks", ...)
    public final BooleanProperty blockBlacklistToggle = new BooleanProperty("block-blacklist", false);
    public final TextProperty blacklistedBlocks = new TextProperty("blacklisted-blocks", "");

    private long rightClickStartTime;

    public FastPlace() {
        super("Fast Place", false);
    }

    @Override
    public void onDisabled() {
        this.rightClickStartTime = 0L;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || !AccessorMinecraft.isInGameHasFocus(mc)) {
            this.rightClickStartTime = 0L;
            return;
        }
        if (!this.isRightClickActive()) {
            this.rightClickStartTime = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        if (this.rightClickStartTime == 0L) {
            this.rightClickStartTime = now;
        }
        if (!this.canFastPlace(now, true)) {
            return;
        }
        int delay = this.tickDelay.getValue();
        int current = AccessorMinecraft.getRightClickDelayTimer(mc);
        if (delay == 0) {
            AccessorMinecraft.setRightClickDelayTimer(mc, 0);
        } else {
            if (delay == 4) {
                return;
            }
            if (current > delay) {
                AccessorMinecraft.setRightClickDelayTimer(mc, delay);
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != myau.event.types.EventType.SEND
                || !this.isEnabled()
                || mc.thePlayer == null || mc.theWorld == null
                || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            return;
        }
        C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
        if (packet.getPlacedBlockDirection() != 255) {
            return;
        }
        ItemStack packetStack = packet.getStack();
        if (packetStack == null || !(packetStack.getItem() instanceof ItemBlock)) {
            return;
        }
        if (!this.canFastPlace(System.currentTimeMillis(), true)) {
            return;
        }
        if (Math.random() < 0.7) {
            event.setCancelled(true);
        }
    }

    private boolean isBlockedHoverBlock() {
        if (!this.blockBlacklistToggle.getValue()
                || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }
        BlockPos hoveredPos = mc.objectMouseOver.getBlockPos();
        if (hoveredPos == null) {
            return false;
        }
        IBlockState state = mc.theWorld.getBlockState(hoveredPos);
        Block hoveredBlock = state.getBlock();
        if (hoveredBlock == null) {
            return false;
        }
        String registryId = Block.blockRegistry.getNameForObject(hoveredBlock).getResourcePath();
        if (registryId == null) {
            return false;
        }
        int meta = hoveredBlock.getMetaFromState(state);
        String storageId = meta != 0 ? registryId + ":" + meta : registryId;
        return containsId(this.blacklistedBlocks.getValue(), storageId)
                || containsId(this.blacklistedBlocks.getValue(), registryId);
    }

    private boolean isRightClickActive() {
        return KeyBindUtil.isKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode()) || mc.thePlayer.isUsingItem();
    }

    private boolean canFastPlace(long now, boolean requireActivationDelay) {
        if (this.blocksOnly.getValue()) {
            ItemStack item = mc.thePlayer.getHeldItem();
            if (item == null || !(item.getItem() instanceof ItemBlock)) {
                return false;
            }
        }
        if (this.pitchCheck.getValue() && mc.thePlayer.rotationPitch < 70.0F) {
            return false;
        }
        if (this.heldItemBlacklistToggle.getValue() && this.matchesHeldBlacklist(mc.thePlayer.getHeldItem())) {
            return false;
        }
        if (this.isBlockedHoverBlock()) {
            return false;
        }
        return !requireActivationDelay
                || now - this.rightClickStartTime >= (long) this.activationTime.getValue().intValue();
    }

    private static boolean containsId(String list, String id) {
        if (list == null || list.isEmpty() || id == null) {
            return false;
        }
        String lower = id.toLowerCase();
        for (String entry : list.split(",")) {
            String e = entry.trim().toLowerCase();
            if (!e.isEmpty() && e.equals(lower)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesHeldBlacklist(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        Item item = stack.getItem();
        net.minecraft.util.ResourceLocation rl = Item.itemRegistry.getNameForObject(item);
        if (rl == null) {
            return false;
        }
        return containsId(this.heldItems.getValue(), rl.getResourcePath());
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.valueOf(this.tickDelay.getValue())};
    }
}
