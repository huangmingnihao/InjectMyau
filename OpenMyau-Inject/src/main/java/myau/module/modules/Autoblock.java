package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LoadWorldEvent;
import myau.events.MouseButtonEvent;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.RightClickMouseEvent;
import myau.events.TickEvent;
import myau.lag.api.EnumLagDirection;
import myau.lag.api.LagRequest;
import myau.lag.timeout.ModuleBackedTimeout;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.PercentProperty;
import myau.slinky.RecoveredRandom;
import myau.slinky.SlinkyModule;
import myau.slinky.TargetCombatStatus;
import myau.access.AccessorEntityPlayer;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import org.lwjgl.input.Mouse;

public class Autoblock extends SlinkyModule {
   private static final Minecraft mc = Minecraft.getMinecraft();

   public final FloatProperty range = new FloatProperty("range", 3.1F, 2.0F, 6.0F, 0.1F);
   public final IntProperty max_hurt_time = new IntProperty("maximum-hurt-time", 300, 50, 500, 1);
   public final IntProperty max_hold_time = new IntProperty("maximum-hold-duration", 250, 50, 500, 1);
   public final BooleanProperty fake_block_animation = new BooleanProperty("fake-block-animation", false);
   public final BooleanProperty fake_block_animation_require_target =
         new BooleanProperty("only-within-range", false, () -> this.fake_block_animation.getValue());
   public final PercentProperty lag_chance = new PercentProperty("lag-chance", 0);
   public final IntProperty lag_duration = new IntProperty("lag-duration", 100, 50, 500, 1);
   public final BooleanProperty lag_flush_attack = new BooleanProperty("prevent-delaying-attacks", true);
   public final BooleanProperty lag_reblock = new BooleanProperty("block-again-immediately", false);
   public final BooleanProperty require_left_mouse_pressed = new BooleanProperty("left-mouse-pressed", true);
   public final BooleanProperty require_right_mouse_pressed = new BooleanProperty("right-mouse-pressed", false);
   public final BooleanProperty require_damage = new BooleanProperty("damaged", false);

   private boolean blocking;
   private boolean manualBlock;
   private boolean animationOwned;
   private int blockStartTick = -1;
   private int lastSelfHurtTime;
   private boolean lagging;
   private int lagStartTick = -1;
   private int tickCounter;
   private LagRequest outboundLag;

   public Autoblock() {
      super("Auto Block");
      this.range.unit(" blocks");
      this.max_hurt_time.unit("ms");
      this.max_hold_time.unit("ms");
      this.lag_chance.unit("%");
      this.lag_duration.unit("ms");
   }

   @Override
   public void onEnabled() {
      this.tickCounter = 0;
      this.resetState(false);
   }

   @Override
   public void onDisabled() {
      this.resetState(true);
   }

   @EventTarget
   public void onLoadWorld(LoadWorldEvent event) {
      this.resetState(false);
   }

   private static int ticks(float milliseconds) {
      return milliseconds <= 0.0F ? 0 : (int) Math.ceil((double) milliseconds / 50.0);
   }

   public static boolean canBlock(ItemStack stack) {
      if (stack == null || stack.getItem() == null) {
         return false;
      }
      int id = Item.getIdFromItem(stack.getItem()) - 267;
      return Integer.compareUnsigned(id, 16) <= 0 && (66083 >>> (id & 31) & 1) != 0;
   }

   private boolean holdingSword() {
      return mc.thePlayer != null && canBlock(mc.thePlayer.getHeldItem());
   }

   private EntityPlayer target() {
      EntityPlayer target = TargetCombatStatus.target();
      float range = this.number("range");
      return target != null && TargetCombatStatus.distanceSquared() <= (double) (range * range) ? target : null;
   }

   private boolean shouldPredictiveBlock() {
      int trigger = Math.max(1, Math.min(10, Math.round(this.number("maximum-hurt-time") / 50.0F)));
      return mc.thePlayer.hurtTime == trigger;
   }

   private boolean shouldBlockVanillaUse() {
      return this.isEnabled() && this.lagging && mc.thePlayer != null && mc.theWorld != null
            && this.holdingSword() && mc.currentScreen == null;
   }

   @EventTarget
   public void onTick(TickEvent event) {
      if (!this.isEnabled() || event.getType() != EventType.PRE) {
         return;
      }
      TargetCombatStatus.select();
      if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead || mc.currentScreen != null) {
         this.resetState(true);
         return;
      }

      int selfHurtTime = mc.thePlayer.hurtTime;
      boolean hurtAgain = selfHurtTime > this.lastSelfHurtTime;
      this.lastSelfHurtTime = selfHurtTime;
      if (!this.holdingSword()) {
         this.resetState(false);
         return;
      }

      int currentTick = ++this.tickCounter;
      boolean rightDown = Mouse.isButtonDown(1);
      boolean leftDown = Mouse.isButtonDown(0);
      if (!leftDown) {
         if (!rightDown) {
            this.resetState(true);
         } else {
            if (this.lagging) {
               this.releaseLag();
            }

            if (!this.blocking) {
               this.startBlocking(currentTick);
               this.manualBlock = true;
            }
         }
      } else {
         if (this.manualBlock) {
            this.stopBlocking(true);
            this.manualBlock = false;
         }

         boolean conditionsMet = this.target() != null && (!this.flag("right-mouse-pressed") || rightDown);
         if (this.lagging) {
            int lagTicks = ticks(this.number("lag-duration"));
            boolean lagExpired = lagTicks > 0 && this.lagStartTick >= 0 && currentTick - this.lagStartTick >= lagTicks;
            if (lagExpired || !conditionsMet) {
               this.releaseLag();
               if (lagExpired && this.flag("block-again-immediately") && conditionsMet) {
                  this.startBlocking(currentTick);
               }
            }
         }

         if (!conditionsMet) {
            this.stopBlocking(true);
         } else {
            if (!this.blocking && !this.lagging && (!this.flag("damaged") || this.shouldPredictiveBlock())) {
               this.startBlocking(currentTick);
            }

            if (this.blocking) {
               int holdTicks = ticks(this.number("maximum-hold-duration"));
               boolean shouldStop = holdTicks > 0 && this.blockStartTick >= 0 && currentTick - this.blockStartTick >= holdTicks;
               if (this.flag("damaged") && hurtAgain) {
                  shouldStop = true;
               }

               if (shouldStop) {
                  if (this.shouldStartLag()) {
                     this.startLag(currentTick);
                  }

                  this.stopBlocking(true);
               }
            }
         }
      }
   }

   @EventTarget(Priority.HIGHEST)
   public void onMouseButton(MouseButtonEvent event) {
      if (this.isEnabled() && event.getButton() == 1 && mc.thePlayer != null && mc.theWorld != null
            && mc.currentScreen == null && this.holdingSword()) {
         event.setCancelled(true);
      }
   }

   @EventTarget(Priority.HIGHEST)
   public void onRightClickMouse(RightClickMouseEvent event) {
      if (this.shouldBlockVanillaUse()) {
         event.setCancelled(true);
      }
   }

   @EventTarget
   public void onPacket(PacketEvent event) {
      if (event.getType() != EventType.SEND) {
         return;
      }
      if (this.isEnabled() && this.lagging && this.flag("prevent-delaying-attacks")
            && event.getPacket() instanceof C02PacketUseEntity
            && ((C02PacketUseEntity) event.getPacket()).getAction() == C02PacketUseEntity.Action.ATTACK) {
         this.releaseLag();
         if (this.flag("block-again-immediately") && this.holdingSword()) {
            this.startBlocking(this.tickCounter);
         }
      }
   }

   @EventTarget
   public void onRender3D(Render3DEvent event) {
      if (!this.isEnabled()) {
         return;
      }
      if (mc.thePlayer == null || mc.theWorld == null) {
         return;
      }
      if (mc.currentScreen == null || !this.blocking && !this.lagging) {
         if (this.flag("fake-block-animation") && this.holdingSword()) {
            boolean show = this.blocking || this.lagging;
            if (show && this.flag("only-within-range")) {
               show = this.target() != null;
            }

            this.setItemInUse(show);
         }
      } else {
         this.resetState(true);
      }
   }

   private void startBlocking(int currentTick) {
      if (this.holdingSword()) {
         int key = mc.gameSettings.keyBindUseItem.getKeyCode();
         KeyBindUtil.setKeyBindState(key, true);
         KeyBindUtil.pressKeyOnce(key);
         this.blocking = true;
         this.blockStartTick = currentTick;
      }
   }

   private void stopBlocking(boolean force) {
      if (this.blocking || force) {
         if (mc.gameSettings != null) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
         }

         this.blocking = false;
         this.blockStartTick = -1;
      }
   }

   private boolean shouldStartLag() {
      float chance = this.fraction("lag-chance");
      return chance <= 0.0F ? false : chance >= 1.0F || RecoveredRandom.SHARED.chance(chance);
   }

   private void startLag(int currentTick) {
      if (!this.lagging) {
         this.lagging = true;
         this.lagStartTick = currentTick;
         if (Myau.lagHandler != null) {
            this.outboundLag = new LagRequest(EnumLagDirection.ONLY_OUTBOUND, new ModuleBackedTimeout(this));
            Myau.lagHandler.requestLag(this.outboundLag);
         }
      }
   }

   private void releaseLag() {
      this.lagging = false;
      this.lagStartTick = -1;
      if (this.outboundLag != null) {
         this.outboundLag.getTimeout().forceTimeOut();
         this.outboundLag = null;
      }
   }

   public boolean isActive() {
      return this.isEnabled() && (this.blocking || this.lagging);
   }

   private void setItemInUse(boolean show) {
      if (mc.thePlayer != null) {
         if (show) {
            AccessorEntityPlayer.setItemInUseCount(mc.thePlayer, 72000);
            this.animationOwned = true;
         } else if (this.animationOwned) {
            if (!mc.thePlayer.isUsingItem()) {
               AccessorEntityPlayer.setItemInUseCount(mc.thePlayer, 0);
            }

            this.animationOwned = false;
         }
      }
   }

   private void resetState(boolean releaseUseKey) {
      this.releaseLag();
      if (mc.gameSettings != null) {
         this.stopBlocking(releaseUseKey);
         this.manualBlock = false;
         if (this.animationOwned) {
            this.setItemInUse(false);
         }

         if (releaseUseKey && Mouse.isCreated() && Mouse.isButtonDown(1) && mc.currentScreen == null) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
         }

         this.lastSelfHurtTime = 0;
      } else {
         this.blocking = this.manualBlock = false;
      }
   }
}
