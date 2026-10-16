package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.TickEvent;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.slinky.RecoveredMath;
import myau.slinky.RecoveredRandom;
import myau.slinky.RotationMath;
import myau.slinky.SlinkyModule;
import myau.slinky.inventory.InventoryActions;
import myau.slinky.inventory.InventorySelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.InventoryPlayer;

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
   public final ModeProperty slot1 = new ModeProperty("1", 0, HOTBAR_CATEGORIES);
   public final ModeProperty slot2 = new ModeProperty("2", 0, HOTBAR_CATEGORIES);
   public final ModeProperty slot3 = new ModeProperty("3", 0, HOTBAR_CATEGORIES);
   public final ModeProperty slot4 = new ModeProperty("4", 0, HOTBAR_CATEGORIES);
   public final ModeProperty slot5 = new ModeProperty("5", 0, HOTBAR_CATEGORIES);
   public final ModeProperty slot6 = new ModeProperty("6", 0, HOTBAR_CATEGORIES);
   public final ModeProperty slot7 = new ModeProperty("7", 0, HOTBAR_CATEGORIES);
   public final ModeProperty slot8 = new ModeProperty("8", 0, HOTBAR_CATEGORIES);
   public final ModeProperty slot9 = new ModeProperty("9", 0, HOTBAR_CATEGORIES);

   public final IntProperty blocks = new IntProperty("blocks", 128, 64, 2304, () -> false);
   public final IntProperty projectiles = new IntProperty("projectiles", 64, 16, 2304, () -> false);
   public final IntProperty arrow = new IntProperty("arrow", 256, 0, 2304, () -> false);

   private int phase;
   private int resume;
   private int scan;
   private long processed;
   private InventorySelection.Action action;
   private float timer;
   private float previousX;
   private float previousY;

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

   @EventTarget
   public void onTick(TickEvent event) {
      if (!this.isEnabled() || event.getType() != EventType.PRE) {
         return;
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
      } else if (this.phase != 2 && mc.thePlayer != null && mc.theWorld != null) {
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
               if (this.flag(this.phase == 0 ? "equip-armor" : "sort-hotbar")) {
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

               this.advance(currentPhase + 1);
            }

            if (this.phase == 2) {
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
