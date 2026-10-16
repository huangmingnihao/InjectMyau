package myau.slinky;

import myau.util.ItemUtil;
import net.minecraft.entity.player.EntityPlayer;

public final class WeaponsHelper {
   private WeaponsHelper() {
   }

   public static boolean isWeapon(EntityPlayer player) {
      return player != null && ItemUtil.hasRawUnbreakingEnchant();
   }
}
