package myau.slinky.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class InventoryActions {
   private static final Minecraft mc = Minecraft.getMinecraft();

   private InventoryActions() {
   }

   public static int type(ItemStack stack) {
      return stack != null && stack.getItem() != null ? Item.getIdFromItem(stack.getItem()) : 0;
   }

   public static void click(GuiContainer screen, int slot, int button, int mode) {
      mc.playerController.windowClick(screen.inventorySlots.windowId, slot, button, mode, mc.thePlayer);
   }

   public static long truncateDoubleLong(double value) {
      return !Double.isNaN(value) && !(value >= 9.223372E18F) && !(value < -9.223372E18F) ? (long) value : Long.MIN_VALUE;
   }

   public static float[] grid(int slot) {
      long index = Integer.toUnsignedLong(slot);
      if (index <= 8L) {
         return new float[]{(float) Integer.toUnsignedLong(slot - 5), 1.0F};
      } else {
         int quotient = (int) (index * 954437177L >>> 33);
         return new float[]{(float) Integer.toUnsignedLong(quotient + 3), (float) (slot - quotient * 9)};
      }
   }
}
