package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.PreAttackEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.slinky.RecoveredRandom;
import myau.slinky.SlinkyModule;
import myau.slinky.StatusText;
import myau.slinky.TargetCombatStatus;
import myau.slinky.WeaponsHelper;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class HitSelect extends SlinkyModule {
   private static final Minecraft mc = Minecraft.getMinecraft();

   private static final float MS_PER_TICK = 50.0F;

   public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Burst", "Criticals"});
   public final PercentProperty cancel_rate = new PercentProperty("in-combat", 80);
   public final PercentProperty cancel_rate_air = new PercentProperty("missed-swings", 40);
   public final IntProperty pause_dur = new IntProperty("pause-duration", 500, 0, 500, 1);
   public final IntProperty wait_for_damage_dur = new IntProperty("wait-for-first-hit", 0, 0, 400, 1);
   public final IntProperty trades_delay = new IntProperty("hit-later-in-trades", 0, 0, 400, 1);
   public final BooleanProperty use_server_time = new BooleanProperty("use-server-attack-time", false);
   public final BooleanProperty fake_swing = new BooleanProperty("fake-swing", false);
   public final BooleanProperty crits_allow_reduce = new BooleanProperty("disable-during-knockback", true);
   public final BooleanProperty require_damage = new BooleanProperty("only-while-damaged", true);
   public final BooleanProperty require_sprint = new BooleanProperty("sprinting", false);
   public final BooleanProperty require_weapon = new BooleanProperty("holding-weapon", false);

   private final State state = new State();

   public HitSelect() {
      super("Hit Select");
      this.cancel_rate.unit("%");
      this.cancel_rate_air.unit("%");
      this.pause_dur.unit("ms");
      this.wait_for_damage_dur.unit("ms");
      this.trades_delay.unit("ms");
   }

   @Override
   public String[] getSuffix() {
      return new String[]{StatusText.label("Hit Select", this.choice("mode"))};
   }

   @Override
   public void onDisabled() {
      this.state.clear();
   }

   @EventTarget
   public void onLoadWorld(LoadWorldEvent event) {
      this.state.clear();
   }

   @EventTarget
   public void onTick(TickEvent event) {
      if (!this.isEnabled() || event.getType() != EventType.PRE) {
         return;
      }
      TargetCombatStatus.select();
      if (mc.thePlayer == null || mc.theWorld == null) {
         this.state.clear();
         return;
      }
      this.state.history();
   }

   @EventTarget
   public void onRender3D(Render3DEvent event) {
      if (!this.isEnabled()) {
         return;
      }
      if (mc.thePlayer == null || mc.theWorld == null) {
         return;
      }
      this.state.tick();
   }

   @EventTarget
   public void onPreAttack(PreAttackEvent event) {
      if (!this.isEnabled()) {
         return;
      }
      if (mc.thePlayer == null || mc.theWorld == null) {
         return;
      }
      TargetCombatStatus.prepare();
      if (this.state.filter(
         TargetCombatStatus.reducing(),
         this.choice("mode"),
         this.flag("sprinting"),
         this.flag("holding-weapon"),
         this.fraction("missed-swings"),
         this.number("pause-duration") / MS_PER_TICK,
         this.number("wait-for-first-hit") / MS_PER_TICK,
         this.number("hit-later-in-trades") / MS_PER_TICK,
         this.flag("use-server-attack-time"),
         this.flag("disable-during-knockback"),
         this.flag("only-while-damaged"),
         this.fraction("in-combat"),
         this.flag("fake-swing"),
         RecoveredRandom.SHARED)) {
         event.setCancelled(true);
      }
   }

   // ---------------------------------------------------------------- Input

   private boolean inputReady() {
      return mc.thePlayer != null && mc.theWorld != null && mc.inGameHasFocus && mc.currentScreen == null;
   }

   private int hurt() {
      return mc.thePlayer.hurtTime;
   }

   private boolean sprint() {
      return mc.thePlayer.isSprinting();
   }

   private boolean weapon() {
      return WeaponsHelper.isWeapon(mc.thePlayer);
   }

   private boolean hasHit() {
      return mc.objectMouseOver != null;
   }

   private int hitKind() {
      MovingObjectPosition hit = mc.objectMouseOver;
      return hit != null && hit.typeOfHit != MovingObjectType.MISS ? (hit.typeOfHit == MovingObjectType.BLOCK ? 1 : 2) : 0;
   }

   private boolean hitEntity() {
      return mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null;
   }

   private boolean ground() {
      return mc.thePlayer.onGround;
   }

   private double vertical() {
      return mc.thePlayer.motionY;
   }

   private float fallDistance() {
      return mc.thePlayer.fallDistance;
   }

   private boolean feetAir() {
      BlockPos pos = new BlockPos((long) Math.floor(mc.thePlayer.posX), (long) Math.floor(mc.thePlayer.posY), (long) Math.floor(mc.thePlayer.posZ));
      return Block.getIdFromBlock(mc.theWorld.getBlockState(pos).getBlock()) == 0;
   }

   private boolean using() {
      return mc.thePlayer.isUsingItem();
   }

   private void swing() {
      mc.thePlayer.swingItem();
   }

   private boolean target() {
      return TargetCombatStatus.target() != null;
   }

   private int targetId() {
      return TargetCombatStatus.target().getEntityId();
   }

   private int targetHurt() {
      return TargetCombatStatus.target().hurtTime;
   }

   private int ticks() {
      return mc.thePlayer.ticksExisted;
   }

   private int attackerTime() {
      return mc.thePlayer.getLastAttackerTime();
   }

   // ---------------------------------------------------------------- State

   private final class State {
      private int unhurt;
      private int targetHistory;
      private int consecutive;
      private int previousTarget;
      private boolean ready;
      private boolean cancel;
      private boolean newHurtWindow;

      private State() {
      }

      private void tick() {
         this.ready = false;
         this.unhurt = inputReady() && hurt() == 0 ? this.unhurt + 1 : 0;
      }

      private void history() {
         boolean server = flag("use-server-attack-time");
         if (!server) {
            int id = target() ? targetId() : 0;
            if (id == this.previousTarget) {
               if (Integer.compareUnsigned(++this.targetHistory, 10) < 0) {
                  return;
               }
            } else {
               this.previousTarget = id;
               this.targetHistory = 1001;
            }

            if (ticks() == attackerTime()) {
               this.targetHistory = 0;
            }
         } else {
            if (target() && targetHurt() == 10) {
               this.targetHistory = 0;
               this.newHurtWindow = true;
            } else {
               this.targetHistory++;
            }
         }
      }

      private int rounded(float value, RecoveredRandom r) {
         long whole = !Float.isNaN(value) && !(value >= 9.223372E18F) && !(value < -9.223372E18F) ? (long) value : Long.MIN_VALUE;
         float fraction = value - (float) Integer.toUnsignedLong((int) whole);
         return (int) whole + (r.chance(fraction) ? 1 : 0);
      }

      private boolean filter(
         boolean reducing,
         int mode,
         boolean requireSprint,
         boolean requireWeapon,
         float airRate,
         float pause,
         float waitDamage,
         float trades,
         boolean server,
         boolean allowReduce,
         boolean requireDamage,
         float combatRate,
         boolean fakeSwing,
         RecoveredRandom r
      ) {
         if (!this.ready) {
            boolean decision;
            if ((!requireSprint || sprint()) && (!requireWeapon || weapon()) && hitKind() != 1) {
               if (hasHit() && hitKind() == 0 && r.chance(airRate)) {
                  decision = true;
               } else {
                  int threshold = rounded(pause, r);
                  boolean ground = ground();
                  boolean rising = (!ground || vertical() > 0.0) && reducing;
                  boolean missing = hitKind() != 2 || !hitEntity();
                  boolean early = false;
                  decision = false;
                  if (mode == 0) {
                     if (rising) {
                        early = true;
                     } else {
                        if (Integer.compareUnsigned(this.targetHistory, 15) < 0) {
                           this.consecutive = 0;
                        } else if (!missing && Integer.compareUnsigned(++this.consecutive, rounded(waitDamage, r)) <= 0) {
                           decision = true;
                           early = true;
                        }

                        if (!early) {
                           if (Integer.compareUnsigned(this.unhurt, 9) <= 0) {
                              threshold += rounded(trades, r);
                           }

                           if (this.newHurtWindow) {
                              this.newHurtWindow = false;
                              early = true;
                           }
                        }
                     }
                  } else if (mode == 1) {
                     boolean window = Integer.compareUnsigned(this.targetHistory - 9, -7) < 0 || server;
                     boolean descending = !ground && fallDistance() > 0.0F;
                     if ((!descending || !window) && (!rising || window && !allowReduce) && feetAir()) {
                        if (!ground && !descending && (!requireDamage || hurt() != 0)) {
                           decision = true;
                           early = true;
                        }
                     } else {
                        early = true;
                     }
                  } else {
                     early = true;
                  }

                  if (!early) {
                     decision = Integer.compareUnsigned(threshold, this.targetHistory + 1) > 0 && r.chance(combatRate);
                  }
               }
            } else {
               decision = false;
            }

            this.cancel = decision;
            this.ready = true;
         }

         if (this.cancel && fakeSwing && !using()) {
            swing();
         }

         return this.cancel;
      }

      private void clear() {
         this.unhurt = this.targetHistory = this.consecutive = this.previousTarget = 0;
         this.ready = this.cancel = this.newHurtWindow = false;
      }
   }
}
