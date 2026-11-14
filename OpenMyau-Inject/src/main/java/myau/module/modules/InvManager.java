package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.TickEvent;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.KeyProperty;
import myau.property.properties.ModeProperty;
import myau.slinky.RecoveredMath;
import myau.slinky.RecoveredRandom;
import myau.slinky.RotationMath;
import myau.slinky.SlinkyModule;
import myau.slinky.inventory.InventoryActions;
import myau.slinky.inventory.InventorySelection;
import myau.util.ItemUtil;
import myau.util.KeyBindUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockOre;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;

public class InvManager extends SlinkyModule {
   private static final Minecraft mc = Minecraft.getMinecraft();

   private static final String[] HOTBAR_CATEGORIES = new String[]{
      "None",
      "Axe",
      "Blocks",
      "Bow",
      "Chest",
      "Ender pearl",
      "Fireball",
      "Fishing rod",
      "Flint and steel",
      "Golden apple",
      "Healing potion",
      "Jump potion",
      "Ladders",
      "Lava bucket",
      "Pickaxe",
      "Shears",
      "Shovel",
      "Snowballs/eggs",
      "Soup",
      "Speed potion",
      "Stick",
      "Strength potion",
      "Sword",
      "TNT",
      "Water bucket",
      "Weapon",
      "Wool"
   };

   public final IntProperty delay_after_open = new IntProperty("delay-after-open", 150, 0, 500, 1);
   public final FloatProperty speed = new FloatProperty("speed", 2.0F, 1.0F, 4.0F, 0.1F);
   public final BooleanProperty smart_speed = new BooleanProperty("smart-speed", true);
   public final BooleanProperty randomize = new BooleanProperty("randomize", true);
   public final BooleanProperty equip_armor = new BooleanProperty("equip-armor", false);
   public final BooleanProperty sort_hotbar = new BooleanProperty("sort-hotbar", false);
   public final BooleanProperty sort_hotbar_fallback = new BooleanProperty("sort-hotbar-fallback", true);

   public final BooleanProperty bind_sort = new BooleanProperty("bind-sort", false);
   public final ModeProperty slot1 = new ModeProperty("1", 0, HOTBAR_CATEGORIES);
   public final KeyProperty bind1 = new KeyProperty("1-key", KeyProperty.NONE, () -> this.bind_sort.getValue());
   public final ModeProperty slot2 = new ModeProperty("2", 0, HOTBAR_CATEGORIES);
   public final KeyProperty bind2 = new KeyProperty("2-key", KeyProperty.NONE, () -> this.bind_sort.getValue());
   public final ModeProperty slot3 = new ModeProperty("3", 0, HOTBAR_CATEGORIES);
   public final KeyProperty bind3 = new KeyProperty("3-key", KeyProperty.NONE, () -> this.bind_sort.getValue());
   public final ModeProperty slot4 = new ModeProperty("4", 0, HOTBAR_CATEGORIES);
   public final KeyProperty bind4 = new KeyProperty("4-key", KeyProperty.NONE, () -> this.bind_sort.getValue());
   public final ModeProperty slot5 = new ModeProperty("5", 0, HOTBAR_CATEGORIES);
   public final KeyProperty bind5 = new KeyProperty("5-key", KeyProperty.NONE, () -> this.bind_sort.getValue());
   public final ModeProperty slot6 = new ModeProperty("6", 0, HOTBAR_CATEGORIES);
   public final KeyProperty bind6 = new KeyProperty("6-key", KeyProperty.NONE, () -> this.bind_sort.getValue());
   public final ModeProperty slot7 = new ModeProperty("7", 0, HOTBAR_CATEGORIES);
   public final KeyProperty bind7 = new KeyProperty("7-key", KeyProperty.NONE, () -> this.bind_sort.getValue());
   public final ModeProperty slot8 = new ModeProperty("8", 0, HOTBAR_CATEGORIES);
   public final KeyProperty bind8 = new KeyProperty("8-key", KeyProperty.NONE, () -> this.bind_sort.getValue());
   public final ModeProperty slot9 = new ModeProperty("9", 0, HOTBAR_CATEGORIES);
   public final KeyProperty bind9 = new KeyProperty("9-key", KeyProperty.NONE, () -> this.bind_sort.getValue());

   public final BooleanProperty drop_trash = new BooleanProperty("drop-trash", false);

   public final BooleanProperty debug_drop = new BooleanProperty("debug-drop", false);



   public final BooleanProperty keep_ore = new BooleanProperty("keep-ore", true, () -> this.drop_trash.getValue());

   public final BooleanProperty keep_water_bucket = new BooleanProperty("keep-water-bucket", true, () -> this.drop_trash.getValue());

   public final BooleanProperty keep_bow_arrows = new BooleanProperty("keep-bow-arrows", true, () -> this.drop_trash.getValue());

   public final BooleanProperty rod_throwable = new BooleanProperty("rod-throwable", false, () -> this.drop_trash.getValue());

   public final IntProperty throws_amount = new IntProperty("throws-amount", 64, 16, 320, () -> this.drop_trash.getValue());

   public final IntProperty blocks = new IntProperty("blocks", 128, 64, 2304, () -> this.drop_trash.getValue());
   public final IntProperty projectiles = new IntProperty("projectiles", 64, 16, 2304, () -> this.drop_trash.getValue());
   public final IntProperty arrow = new IntProperty("arrow", 256, 0, 2304, () -> this.drop_trash.getValue());

   private int phase;
   private int resume;
   private int scan;
   private long processed;
   private InventorySelection.Action action;
   private float timer;
   private float previousX;
   private float previousY;

   private int pendingSort = -1;
   private final boolean[] bindWasDown = new boolean[9];
   private KeyProperty[] slotKeys;

   public InvManager() {
      super("Inv Manager");
      this.delay_after_open.unit("ms");
      this.speed.unit("x");
   }


   private static float ticks(float milliseconds) {
      return milliseconds / 50.0F;
   }

   private void delay(float x, float y) {
      float amount = (float) ((double) this.speed.getMaximum() / (double) this.number("speed"));
      float distance = RecoveredMath.floatDistance(x - this.previousX, y - this.previousY);
      if (this.flag("smart-speed")) {
         amount *= RotationMath.max(distance * 0.5F, 1.0F);
      }

      if (this.flag("randomize")) {
         amount *= RecoveredRandom.SHARED.interval(1.0F, 1.25F);
      }

      this.timer += amount;
      this.previousX = x;
      this.previousY = y;
   }

   private void delay(int slot) {
      float[] grid = InventoryActions.grid(slot);
      this.delay(grid[0], grid[1]);
   }

   private void advance(int next) {
      this.phase = next;
      this.scan = 0;
      this.processed = 0L;
      this.action = null;
      this.resume = 0;
   }


   private int convertSlotIndex(int slot) {
      if (slot >= 36) {
         return 8 - (slot - 36);
      } else {
         return slot <= 8 ? slot + 36 : slot;
      }
   }

   private int getStackSize(int slot) {
      if (slot == -1) {
         return 0;
      }
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
      return stack != null ? stack.stackSize : 0;
   }

   // ==================================================================

   // ==================================================================

   private boolean isThrowable(ItemStack stack) {
      if (stack == null) return false;
      return stack.getItem() instanceof ItemSnowball || stack.getItem() instanceof ItemEgg
              || (this.rod_throwable.getValue() && stack.getItem() instanceof ItemFishingRod);
   }

   private boolean isGapple(ItemStack stack) {
      if (stack == null) return false;
      return stack.getItem() instanceof ItemAppleGold;
   }


   private boolean isOre(ItemStack stack) {
      if (stack == null) return false;
      Item item = stack.getItem();
      if (item instanceof ItemBlock) {
         Block block = ((ItemBlock) item).getBlock();
         if (block instanceof BlockOre) {
            return true;
         }
      }
      return item == Items.diamond
              || item == Items.emerald
              || item == Items.iron_ingot
              || item == Items.gold_ingot
              || item == Items.coal
              || item == Items.quartz
              || item == Items.redstone;
   }

   private boolean isWaterBucket(ItemStack stack) {
      return stack != null && (stack.getItem() == Items.water_bucket || stack.getItem() == Items.bucket);
   }

   private boolean isBow(ItemStack stack) {
      return stack != null && stack.getItem() instanceof ItemBow;
   }


   private int findWaterBucketSlot(int preferredSlot, boolean hotbarOnly) {
      if (preferredSlot >= 0 && preferredSlot <= 8) {
         if (this.isWaterBucket(mc.thePlayer.inventory.getStackInSlot(preferredSlot))) {
            return preferredSlot;
         }
      }
      int start = hotbarOnly ? 0 : 9;
      int end = hotbarOnly ? 9 : 36;
      for (int i = start; i < end; i++) {
         if (this.isWaterBucket(mc.thePlayer.inventory.getStackInSlot(i))) {
            return i;
         }
      }
      return -1;
   }

   private int findThrowableSlot(int preferredSlot, boolean hotbarOnly) {
      if (preferredSlot >= 0 && preferredSlot <= 8) {
         if (this.isThrowable(mc.thePlayer.inventory.getStackInSlot(preferredSlot))) {
            return preferredSlot;
         }
      }
      int start = hotbarOnly ? 0 : 9;
      int end = hotbarOnly ? 9 : 36;
      for (int i = start; i < end; i++) {
         if (this.isThrowable(mc.thePlayer.inventory.getStackInSlot(i))) {
            return i;
         }
      }
      return -1;
   }

   private int findGappleSlot(int preferredSlot, boolean hotbarOnly) {
      if (preferredSlot >= 0 && preferredSlot <= 8) {
         if (this.isGapple(mc.thePlayer.inventory.getStackInSlot(preferredSlot))) {
            return preferredSlot;
         }
      }
      int start = hotbarOnly ? 0 : 9;
      int end = hotbarOnly ? 9 : 36;
      for (int i = start; i < end; i++) {
         if (this.isGapple(mc.thePlayer.inventory.getStackInSlot(i))) {
            return i;
         }
      }
      return -1;
   }

   private int findBowSlot(int preferredSlot, boolean hotbarOnly) {
      if (!this.keep_bow_arrows.getValue()) return -1;
      if (preferredSlot >= 0 && preferredSlot <= 8) {
         if (this.isBow(mc.thePlayer.inventory.getStackInSlot(preferredSlot))) {
            return preferredSlot;
         }
      }
      int start = hotbarOnly ? 0 : 9;
      int end = hotbarOnly ? 9 : 36;
      for (int i = start; i < end; i++) {
         if (this.isBow(mc.thePlayer.inventory.getStackInSlot(i))) {
            return i;
         }
      }
      return -1;
   }

   private int getTotalThrowsCount() {
      int count = 0;
      for (int i = 0; i < 36; i++) {
         ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
         if (stack != null && this.isThrowable(stack)) {
            count += stack.stackSize;
         }
      }
      return count;
   }

   private KeyProperty[] slotKeys() {
      if (this.slotKeys == null) {
         this.slotKeys = new KeyProperty[]{
            this.bind1, this.bind2, this.bind3, this.bind4, this.bind5,
            this.bind6, this.bind7, this.bind8, this.bind9
         };
      }
      return this.slotKeys;
   }


   private void scanBindKeys() {
      if (!this.flag("bind-sort")) {
         for (int i = 0; i < 9; i++) {
            this.bindWasDown[i] = false;
         }
         return;
      }
      KeyProperty[] keys = this.slotKeys();
      for (int i = 0; i < 9; i++) {
         KeyProperty key = keys[i];
         boolean down = key.isBound() && KeyBindUtil.isKeyDown(key.getValue());
         if (down && !this.bindWasDown[i]) {
            this.pendingSort = i;
         }
         this.bindWasDown[i] = down;
      }
   }


   private void processPendingSort() {
      if (this.pendingSort < 0) {
         return;
      }
      int destination = this.pendingSort;
      this.pendingSort = -1;
      if (mc.thePlayer == null) {
         return;
      }
      int category = this.choice(Integer.toString(destination + 1));
      if (category == 0) {
         return;
      }
      InventoryPlayer inventory = mc.thePlayer.inventory;
      InventorySelection.Stack[] main = new InventorySelection.Stack[36];
      for (int i = 0; i < 36; i++) {
         main[i] = InventorySelection.Stack.of(inventory.getStackInSlot(i));
      }
      InventorySelection.Action planned = InventorySelection.planHotbar(
            main, destination, category, this.flag("sort-hotbar-fallback"), 0L);
      if (planned == null) {
         return;
      }
      this.action = planned;
      this.scan = destination;
      this.resume = 2;
      this.delay(planned.source);
      this.phase = 3;
   }

















   private static final class KeptSlots {












      final boolean[] kept = new boolean[40];

      final int[] armorBest = new int[]{-1, -1, -1, -1};

      int sword = -1;
      int pickaxe = -1;
      int shovel = -1;
      int axe = -1;
      int blocks = -1;
      int projectile = -1;
      int goldApple = -1;
      int bow = -1;
      int arrow = -1;
   }









   private KeptSlots computeKeptSlots() {
      KeptSlots kept = new KeptSlots();


      kept.sword = ItemUtil.findSwordInInventorySlot(0, true);
      if (kept.sword == -1) {
         kept.sword = ItemUtil.findSwordInInventorySlot(0, false);
      }
      kept.pickaxe = ItemUtil.findInventorySlot("pickaxe", 0, true);
      if (kept.pickaxe == -1) {
         kept.pickaxe = ItemUtil.findInventorySlot("pickaxe", 0, false);
      }
      kept.shovel = ItemUtil.findInventorySlot("shovel", 0, true);
      if (kept.shovel == -1) {
         kept.shovel = ItemUtil.findInventorySlot("shovel", 0, false);
      }
      kept.axe = ItemUtil.findInventorySlot("axe", 0, true);
      if (kept.axe == -1) {
         kept.axe = ItemUtil.findInventorySlot("axe", 0, false);
      }
      kept.blocks = ItemUtil.findInventorySlot(0, ItemUtil.ItemType.Block);
      kept.projectile = ItemUtil.findInventorySlot(0, ItemUtil.ItemType.Projectile);
      if (kept.projectile == -1) {
         kept.projectile = ItemUtil.findInventorySlot(0, ItemUtil.ItemType.FishRod);
      }
      kept.goldApple = ItemUtil.findInventorySlot(0, ItemUtil.ItemType.GoldApple);
      kept.bow = ItemUtil.findBowInventorySlot(0, true);
      if (kept.bow == -1) {
         kept.bow = ItemUtil.findBowInventorySlot(0, false);
      }
      kept.arrow = ItemUtil.findInventorySlot(0, ItemUtil.ItemType.Arrow);


      //






      for (int i = 0; i < 4; i++) {
         int best = ItemUtil.findArmorInventorySlot(i, true);
         if (best == -1) {
            best = ItemUtil.findArmorInventorySlot(i, false);
         }
         kept.armorBest[i] = best;
         if (best >= 0 && best < 36) {
            kept.kept[best] = true;
         }
      }

      if (kept.sword != -1) kept.kept[kept.sword] = true;
      if (kept.pickaxe != -1) kept.kept[kept.pickaxe] = true;
      if (kept.shovel != -1) kept.kept[kept.shovel] = true;
      if (kept.axe != -1) kept.kept[kept.axe] = true;
      if (kept.blocks != -1) kept.kept[kept.blocks] = true;
      if (kept.projectile != -1) kept.kept[kept.projectile] = true;
      if (kept.goldApple != -1) kept.kept[kept.goldApple] = true;
      if (kept.bow != -1) kept.kept[kept.bow] = true;
      if (kept.arrow != -1) kept.kept[kept.arrow] = true;


      //

      //



      //




      boolean fallback = this.flag("sort-hotbar-fallback");
      InventorySelection.Stack[] main = new InventorySelection.Stack[36];
      for (int i = 0; i < 36; i++) {
         main[i] = InventorySelection.Stack.of(mc.thePlayer.inventory.getStackInSlot(i));
      }
      for (int scan = 0; scan < 9; scan++) {
         int category = this.choice(Integer.toString(scan + 1));
         if (category == 0 || main[scan] == null) {
            continue;
         }
         int slotScore = InventorySelection.policy(category, main[scan], fallback) * main[scan].count;
         if (slotScore <= 0) {
            continue;
         }

         boolean best = true;
         for (int i = 0; i < 36; i++) {
            InventorySelection.Stack s = main[i];
            if (s == null) {
               continue;
            }
            int score = InventorySelection.policy(category, s, fallback) * s.count;
            if (Integer.compareUnsigned(slotScore, score) < 0) {
               best = false;
               break;
            }
         }
         if (best) {
            kept.kept[scan] = true;
         }
      }

      return kept;
   }








   private String describeKept() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 36; i++) {
         if (i < this.keptFlags.length && this.keptFlags[i]) {
            sb.append(i).append(',');
         }
      }
      return sb.length() == 0 ? "none" : sb.toString();
   }


   private final boolean[] keptFlags = new boolean[36];













   private ArrayList<Integer> findTrashSlots() {
      KeptSlots keep = this.computeKeptSlots();
      System.arraycopy(keep.kept, 0, this.keptFlags, 0, 36);
      InventoryPlayer inventory = mc.thePlayer.inventory;
      ArrayList<Integer> itemsToDrop = new ArrayList<Integer>();

      int blockCount = 0;
      int blockLimit = this.blocks.getValue();
      int totalThrows = this.getTotalThrowsCount();
      int throwsLimit = this.throws_amount.getValue();


      //






      for (int i = 0; i < 9; i++) {
         if (this.findWaterBucketSlot(i, true) == i) keep.kept[i] = true;
         if (this.findThrowableSlot(i, true) == i) keep.kept[i] = true;
         if (this.findGappleSlot(i, true) == i) keep.kept[i] = true;
         if (this.findBowSlot(i, true) == i) keep.kept[i] = true;
      }
      System.arraycopy(keep.kept, 0, this.keptFlags, 0, 36);


      if (totalThrows > throwsLimit) {
         for (int i = 35; i >= 0; i--) {
            if (keep.kept[i]) continue;
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack != null && this.isThrowable(stack)) {
               itemsToDrop.add(i);
            }
         }
      }


      for (int i = 0; i < 36; i++) {
         if (keep.kept[i] || itemsToDrop.contains(i)) {
            continue;
         }
         ItemStack stack = inventory.getStackInSlot(i);
         if (stack == null) {
            continue;
         }


         if (this.keep_bow_arrows.getValue() && this.isBow(stack)) {
            int bestBow = ItemUtil.findBowInventorySlot(0, true);
            if (bestBow == -1) {
               bestBow = ItemUtil.findBowInventorySlot(0, false);
            }
            if (bestBow != -1 && i != bestBow) {
               itemsToDrop.add(i);
            }
            continue;
         }

         boolean isBlock = ItemUtil.isBlock(stack);
         boolean isThrowable = this.isThrowable(stack);
         boolean isGapple = this.isGapple(stack);
         boolean isOre = this.isOre(stack);
         boolean isProtectedWater = this.keep_water_bucket.getValue() && this.isWaterBucket(stack);
         boolean isProtectedBowArrow = this.keep_bow_arrows.getValue()
                 && ItemUtil.ItemType.Arrow.contains(stack);

         if (!this.keep_ore.getValue() && isOre) {

            itemsToDrop.add(i);
         } else if (!isThrowable && !isOre && !isGapple && !isProtectedWater && !isProtectedBowArrow
                 && (ItemUtil.isNotSpecialItem(stack)
                     || (isBlock && blockCount >= blockLimit))) {
            itemsToDrop.add(i);
         }

         if (isBlock) {
            blockCount += stack.stackSize;
         }
      }

      return itemsToDrop;
   }

   @EventTarget
   public void onTick(TickEvent event) {
      if (!this.isEnabled() || event.getType() != EventType.PRE) {
         return;
      }
      boolean inInv = mc.currentScreen instanceof GuiInventory;
      if (this.debug_drop.getValue() && inInv) {
         myau.inject.Log.line("[InvMgr] tick phase=" + this.phase
                 + " scan=" + this.scan
                 + " timer=" + this.timer
                 + " sortHotbar=" + this.flag("sort-hotbar")
                 + " equipArmor=" + this.flag("equip-armor")
                 + " dropTrash=" + this.flag("drop-trash")
                 + " bindSort=" + this.flag("bind-sort"));
      }
      if (!(mc.currentScreen instanceof GuiInventory)) {
         this.advance(0);
         this.timer = ticks(this.number("delay-after-open"));
         if (this.flag("randomize")) {
            this.timer = RecoveredRandom.SHARED.interval(1.0F, 1.5F) * this.timer;
         }

         float[] grid = InventoryActions.grid(13);
         this.previousX = grid[0];
         this.previousY = grid[1];
      } else if (mc.thePlayer != null && mc.theWorld != null) {
         this.scanBindKeys();





         if (this.phase == 2) {
            if (this.pendingSort >= 0) {
               this.processPendingSort();
               return;
            }



            this.phase = 7;
         }
         if (this.timer > 0.0F) {
            this.timer += -1.0F;
            if (this.timer > 0.0F) {
               return;
            }
         }

         GuiInventory screen = (GuiInventory) mc.currentScreen;
         InventoryPlayer inventory = mc.thePlayer.inventory;

         while (true) {
            while (this.phase == 0 || this.phase == 1) {
               int currentPhase = this.phase;
               int limit = this.phase == 0 ? 4 : 9;
               boolean active = this.phase == 0
                  ? this.flag("equip-armor")
                  : this.flag("sort-hotbar") && !this.flag("bind-sort");
               if (active) {
                  while (this.scan < limit) {
                     InventorySelection.Stack[] main = new InventorySelection.Stack[36];

                     for (int i = 0; i < 36; i++) {
                        main[i] = InventorySelection.Stack.of(inventory.getStackInSlot(i));
                     }

                     if (this.phase == 0) {
                        this.action = InventorySelection.planArmor(main, InventorySelection.Stack.of(inventory.getStackInSlot(39 - this.scan)), this.scan);
                     } else {
                        int category = this.choice(Integer.toString(this.scan + 1));
                        this.action = InventorySelection.planHotbar(main, this.scan, category, this.flag("sort-hotbar-fallback"), this.processed);
                        if (this.action != null
                           || category != 0
                              && main[this.scan] != null
                              && Math.max(0, main[this.scan].count)
                                    * InventorySelection.policy(category, main[this.scan], this.flag("sort-hotbar-fallback"))
                                 != 0) {
                           this.processed = this.processed | InventorySelection.processedBit(this.scan);
                        }
                     }

                     if (this.action != null) {
                        this.delay(this.action.source);
                        this.resume = currentPhase;
                        this.phase = 3;
                        return;
                     }

                     this.scan = this.scan + 1 & 0xFF;
                  }
               }

               int nextPhase = currentPhase + 1;
               if (nextPhase == 2 && this.flag("drop-trash")) {
                  nextPhase = 6;
               }
               this.advance(nextPhase);
            }

            if (this.phase == 2) {

               this.phase = 7;
               return;
            }

            if (this.phase == 7) {

               return;
            }

            if (this.phase == 6) {
               ArrayList<Integer> junk = this.findTrashSlots();
               if (this.debug_drop.getValue()) {
                  int keptCount = 0;
                  for (boolean b : this.keptFlags) {
                     if (b) keptCount++;
                  }
                  myau.util.ChatUtil.sendFormatted(String.format(
                          "%sInvMgr&7: drop=%s keptSlots=%d/36 [%s]",
                          myau.Myau.clientName, junk.toString(), keptCount, this.describeKept()));
               }
               if (junk.isEmpty()) {
                  this.advance(2);
                  continue;
               }



               //



               //



               int slot = junk.get(0);
               int junkSlot = this.convertSlotIndex(slot);
               InventoryActions.click(screen, junkSlot, 1, 4);
               this.delay(junkSlot);
               return;
            }

            if (this.phase == 3) {
               int button = this.action.kind == 1 ? this.action.destination : 0;
               int mode = this.action.kind == 0 ? 1 : (this.action.kind == 1 ? 2 : 0);
               InventoryActions.click(screen, this.action.source, button, mode);
               this.processed = this.processed | InventorySelection.processedBit(this.scan);
               if (this.resume != 1) {
                  this.scan = this.scan + 1 & 0xFF;
               }

               if (this.action.kind == 2) {
                  this.delay(this.action.destination);
                  this.phase = 4;
                  this.action.source = this.action.destination;
                  return;
               }

               this.phase = this.resume;
            } else {
               if (this.phase == 4) {
                  InventoryActions.click(screen, this.action.source, 0, 0);
                  this.delay(this.previousX + -2.0F, this.previousY);
                  this.phase = 5;
                  return;
               }

               if (this.phase != 5) {
                  return;
               }

               InventoryActions.click(screen, -999, 0, 0);
               this.phase = this.resume;
            }
         }
      }
   }

   @EventTarget
   public void onLoadWorld(LoadWorldEvent event) {
      this.advance(0);
      this.timer = 0.0F;
      this.previousX = 0.0F;
      this.previousY = 0.0F;
   }
}
