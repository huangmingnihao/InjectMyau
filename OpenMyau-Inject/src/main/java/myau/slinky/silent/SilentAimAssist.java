package myau.slinky.silent;

import java.lang.reflect.Field;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import myau.module.modules.AimAssist;
import myau.slinky.RangeProperty;
import myau.util.KeyBindUtil;
import myau.util.TeamUtil;
import myau.inject.MappingBridge;
import net.minecraft.item.ItemSword;

public final strictfp class SilentAimAssist {
   private final AimAssist settings;
   private final RecoveredRandom random;
   private final RecoveredRandom.Smooth pitchDamping = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth pitchNoise = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth horizontalFactor = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth verticalFactor = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth speedFactor = new RecoveredRandom.Smooth();
   private EntityPlayer target;
   private boolean targetActive;
   private boolean silent;
   private Vec3 previousAim = new Vec3(0.0, 0.0, 0.0);
   private Vec3 currentAim = this.previousAim;
   private int graceTicks;
   private long tieCounter;

   public SilentAimAssist(AimAssist settings) {
      this(settings, RecoveredRandom.SHARED);
   }

   public SilentAimAssist(AimAssist settings, RecoveredRandom random) {
      this.settings = settings;
      this.random = random;
   }

   private boolean flag(String key) {
      switch (key) {
         case "silent_move_correct": return settings.silent_move_correct.getValue();
         case "silent_ignore_manual_aim": return settings.silent_ignore_manual_aim.getValue();
         case "ignore_invis": return settings.ignore_invis.getValue();
         case "require_line_of_sight": return settings.require_line_of_sight.getValue();
         case "allow_exceed_fov": return settings.allow_exceed_fov.getValue();
         case "require_sprint": return settings.require_sprint.getValue();
         case "require_mouse_pressed": return settings.require_mouse_pressed.getValue();
         case "require_mouse_moved": return settings.require_mouse_moved.getValue();
         case "disable_on_block_break": return settings.disable_on_block_break.getValue();
         case "require_weapon": return settings.require_weapon.getValue();
         case "lock_on_vertical": return settings.lock_on_vertical.getValue();
         default: throw new IllegalArgumentException(key);
      }
   }

   private float number(String key) {
      switch (key) {
         case "hor_speed": return settings.hor_speed.getValue();
         case "ver_speed": return settings.ver_speed.getValue();
         case "predict": return settings.predict.getValue() / 100.0f;
         case "aim_randomization": return settings.aim_randomization.getValue() / 100.0f;
         case "hor_hitbox_correct": return settings.hor_hitbox_correct.getValue() / 100.0f;
         case "ver_hitbox_correct": return settings.ver_hitbox_correct.getValue() / 100.0f;
         default: throw new IllegalArgumentException(key);
      }
   }

   private int choice(String key) {
      if (key.equals("mode")) return 3; // The source's Silent index; Myau saves it as index 1.
      if (key.equals("mode_target")) return settings.mode_target.getValue();
      if (key.equals("sort_by")) return settings.sort_by.getValue();
      throw new IllegalArgumentException(key);
   }

   private RangeProperty.Value range(String key) {
      if (key.equals("range")) return settings.range.getValue();
      if (key.equals("fov")) return settings.fov.getValue();
      throw new IllegalArgumentException(key);
   }

   public void afterEntities(SilentAimContext context) {
      updateTarget(context);
      SilentAimFrame event = new SilentAimFrame();
      event.yaw = SilentRotationService.storedRotationValid() ? SilentRotationService.cameraYaw() : context.player().rotationYaw;
      event.pitch = SilentRotationService.storedRotationValid() ? SilentRotationService.cameraPitch() : context.player().rotationPitch;
      event.active = silent;
      event.remapMovement = silent && flag("silent_move_correct");
      SilentRotationService.finishFrame(context, event);
      SilentRotationService.applyPending(context, true);
   }

   public void display(SilentAimContext context, float partialTicks, double elapsedSeconds) {
      SilentAimFrame event = new SilentAimFrame();
      event.timerRenderPartialTicks = partialTicks;
      event.elapsedSeconds = elapsedSeconds;
      rotate(context, event);
   }

   public void reset() { clearTarget(); silent = false; graceTicks = 0; }
   public boolean isSilent() { return silent; }

   private strictfp boolean focused(SilentAimContext context) {
      return context.ready() && context.minecraft().inGameHasFocus && context.minecraft().currentScreen == null;
   }

   private strictfp void clearTarget() {
      this.target = null;
      this.targetActive = false;
   }

   public strictfp EntityPlayer currentTarget() {
      return this.target;
   }

   public strictfp boolean targetActive() {
      return this.targetActive;
   }

   private strictfp boolean consider(
      SilentAimContext context, EntityPlayer candidate, Vec3 eye, float viewYaw, boolean locked, SilentAimAssist.Score best, SilentAimAssist.Candidate result
   ) {
      if (candidate.isDead || candidate.isPlayerSleeping()) {
         return false;
      } else if (this.flag("ignore_invis") && candidate.isInvisibleToPlayer(context.player())) {
         return false;
      } else if (TeamUtil.isFriend(candidate) || TeamUtil.isSameTeam(candidate)
            || (settings.bot_check.getValue() && TeamUtil.isBot(candidate))) {
         return false;
      } else {
         Vec3 position = new Vec3(candidate.posX, candidate.posY, candidate.posZ);
         float prediction = this.number("predict");
         if (prediction > 0.0F && candidate instanceof EntityOtherPlayerMP) {
            position = RotationMath.interpolate(position, SilentAimAssist.ServerPosition.read((EntityOtherPlayerMP)candidate), (double)prediction);
         }

         AxisAlignedBB box = candidate.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
         Vec3 nearest = new Vec3(
            RotationMath.clamp(box.minX, box.maxX, eye.xCoord),
            RotationMath.clamp(box.minY, box.maxY, eye.yCoord),
            RotationMath.clamp(box.minZ, box.maxZ, eye.zCoord)
         );
         double distance = RotationMath.distance(eye, nearest);
         boolean enabled = !((double)((float)this.range("range").lower) > distance);
         if (distance > best.distance) {
            return false;
         } else {
            Vec3 point = aimPoint(
               eye, position, nearest, distance, this.number("hor_hitbox_correct"), this.number("ver_hitbox_correct"), this.number("aim_randomization")
            );
            float angle = RotationMath.abs(RotationMath.wrap(RotationMath.angles(eye, point)[0] - viewYaw));
            if (angle > best.angle) {
               if (!locked) {
                  return false;
               }

               enabled = this.flag("allow_exceed_fov");
            }

            float health = candidate.getHealth();
            if (Float.isNaN(health)) {
               health = 20.0F;
            }

            if (!(health > best.health) && candidate.hurtTime <= best.hurtTime) {
               if (this.flag("require_line_of_sight") && context.world().rayTraceBlocks(eye, point, false, true, true) != null) {
                  return false;
               } else {
                  if (this.choice("mode_target") != 0) {
                     switch (this.choice("sort_by")) {
                        case 0:
                           best.distance = distance;
                           break;
                        case 1:
                           best.health = health;
                           break;
                        case 2:
                           int hash = RotationMath.tieHash(candidate.getEntityId(), this.tieCounter++);
                           if (candidate.hurtTime == best.hurtTime && Integer.compareUnsigned(hash, best.tieBreak) < 0) {
                              return false;
                           }

                           best.hurtTime = candidate.hurtTime;
                           best.tieBreak = hash;
                           break;
                        case 3:
                           best.angle = angle;
                           break;
                        default:
                           throw new IllegalStateException("target sort");
                     }
                  } else {
                     best.angle = angle;
                  }

                  result.point = point;
                  result.enabled = enabled;
                  if (locked) {
                     this.targetActive = enabled;
                  }

                  return true;
               }
            } else {
               return false;
            }
         }
      }
   }

   private strictfp void updateTarget(SilentAimContext context) {
      SilentRotationService.clearManual();
      if (!this.focused(context)) {
         this.clearTarget();
      } else if (this.flag("require_sprint") && this.choice("mode") != 3 && !context.player().isSprinting()) {
         this.targetActive = false;
      } else {
         int keyCode = context.minecraft().gameSettings.keyBindAttack.getKeyCode();
         boolean attack = KeyBindUtil.isKeyDown(keyCode);
         if (this.flag("require_mouse_pressed")) {
            if (!attack) {
               this.graceTicks = (this.random.chance(0.5F) ? 1 : 0) + 1;
               this.clearTarget();
               return;
            }

            if (this.graceTicks != 0 && this.choice("mode") < 2) {
               this.graceTicks--;
               this.clearTarget();
               return;
            }
         }

         MovingObjectPosition hit = context.minecraft().objectMouseOver;
         if (this.flag("disable_on_block_break") && attack && hit != null && hit.typeOfHit == MovingObjectType.BLOCK) {
            this.targetActive = false;
         } else if (this.flag("require_weapon") && !(context.player().getHeldItem() != null && context.player().getHeldItem().getItem() instanceof ItemSword)) {
            this.clearTarget();
         } else {
            Vec3 eye = new Vec3(context.player().posX, context.player().posY + 1.62, context.player().posZ);
            float viewYaw = SilentRotationService.storedRotationValid() ? SilentRotationService.storedYaw() : context.player().rotationYaw;
            SilentAimAssist.Score best = new SilentAimAssist.Score((double)((float)this.range("range").upper), (float)this.range("fov").upper);
            SilentAimAssist.Candidate selected = new SilentAimAssist.Candidate();
            if (this.choice("mode_target") == 0 && this.target != null) {
               if (this.consider(context, this.target, eye, viewYaw, true, best, selected)) {
                  this.previousAim = this.currentAim;
                  this.currentAim = selected.point;
                  return;
               }

               this.clearTarget();
            }

            EntityPlayer chosen = null;

            for (int i = 0; i < context.world().playerEntities.size(); i++) {
               EntityPlayer candidate = (EntityPlayer)context.world().playerEntities.get(i);
               if (candidate != context.player()) {
                  SilentAimAssist.Candidate result = new SilentAimAssist.Candidate();
                  if (this.consider(context, candidate, eye, viewYaw, false, best, result)) {
                     chosen = candidate;
                     selected = result;
                  }
               }
            }

            if (chosen == null) {
               this.clearTarget();
            } else {
               this.previousAim = this.target == chosen ? this.currentAim : selected.point;
               this.currentAim = selected.point;
               this.targetActive = selected.enabled;
               this.target = chosen;
            }
         }
      }
   }

   public static strictfp Vec3 aimPoint(Vec3 localEye, Vec3 predicted, Vec3 nearest, double distance, float horizontal, float vertical, float randomization) {
      Vec3 targetEye = new Vec3(predicted.xCoord, predicted.yCoord + 1.62, predicted.zCoord);
      double x = targetEye.xCoord;
      double z = targetEye.zCoord;
      if (horizontal > 0.0F) {
         float fraction = distance < 3.5 ? (float)((double)horizontal * (distance / 3.5)) : horizontal;
         Vec3 point = RotationMath.interpolate(targetEye, nearest, (double)fraction);
         x = point.xCoord;
         z = point.zCoord;
      }

      double y = targetEye.yCoord + (nearest.yCoord - targetEye.yCoord) * (double)vertical;
      double sum = localEye.xCoord + localEye.yCoord;
      sum += localEye.zCoord;
      sum += x;
      sum += y;
      sum += z;
      float input = (float)(sum * 0.1);
      float noiseX = AimNoise.sample(input);
      float noiseY = AimNoise.sample(10000.0F + input);
      double halfX = (double)noiseX * 0.5;
      double halfY = (double)noiseY * 0.5;
      float noiseZ = AimNoise.sample(20000.0F + input);
      double halfZ = (double)noiseZ * 0.5;
      float randomScale = randomization;
      if (distance < 3.5) {
         randomScale = (float)((double)randomization * (distance / 3.5 * 0.9 + 0.1));
      }

      double scale = (double)randomScale;
      return new Vec3(x + scale * halfX, y + scale * halfY, z + scale * halfZ);
   }

   private strictfp void rotate(SilentAimContext context, SilentAimFrame event) {
      if (this.focused(context)) {
         float currentYaw = context.player().rotationYaw + SilentRotationService.pendingYaw();
         float currentPitch = context.player().rotationPitch + SilentRotationService.pendingPitch();
         if (!this.targetActive) {
            if (this.silent) {
               if (SilentRotationService.storedRotationValid() && SilentRotationService.claim(100)) {
                  float yaw = RotationMath.wrap(SilentRotationService.storedYaw() - currentYaw);
                  float pitch = SilentRotationService.storedPitch() - currentPitch;
                  if (RotationMath.magnitude(yaw, pitch) < 10.0F) {
                     this.silent = false;
                  }

                  this.applyStep(yaw, pitch, (float)event.elapsedSeconds);
               } else {
                  this.silent = false;
               }
            }
         } else if (SilentRotationService.claim(100)) {
            if (!this.silent && this.choice("mode") == 3) {
               this.silent = true;
            }

            if (!this.silent || SilentRotationService.storedRotationValid()) {
               double fraction = (double)event.timerRenderPartialTicks;
               Vec3 previous = new Vec3(context.player().prevPosX, context.player().prevPosY, context.player().prevPosZ);
               Vec3 current = new Vec3(context.player().posX, context.player().posY, context.player().posZ);
               Vec3 eye = RotationMath.interpolate(previous, current, fraction).addVector(0.0, 1.62, 0.0);
               float[] desired = RotationMath.angles(eye, RotationMath.interpolate(this.previousAim, this.currentAim, fraction));
               float yaw = RotationMath.wrap(desired[0] - currentYaw);
               float pitch = desired[1] - currentPitch;
               if (this.choice("mode") == 3 && !this.flag("silent_ignore_manual_aim")) {
                  float restoreYaw = RotationMath.wrap(SilentRotationService.storedYaw() - currentYaw);
                  float restorePitch = SilentRotationService.storedPitch() - currentPitch;
                  float targetYaw = RotationMath.wrap(desired[0] - SilentRotationService.storedYaw());
                  float targetPitch = desired[1] - SilentRotationService.storedPitch();
                  if (RotationMath.magnitude(restoreYaw, restorePitch) < 10.0F
                     && RotationMath.magnitude(targetYaw, targetPitch) < RotationMath.magnitude(yaw, pitch)) {
                     SilentRotationService.add(restoreYaw, restorePitch);
                     return;
                  }
               }

               this.applyStep(yaw, pitch, (float)event.elapsedSeconds);
            }
         }
      }
   }

   private strictfp void applyStep(float yaw, float pitch, float time) {
      float[] step = this.aimStep(yaw, pitch, time, RecoveredRandom.now(), SilentRotationService.manualYaw(), SilentRotationService.manualPitch());
      if (this.flag("require_mouse_moved")) {
         SilentRotationService.clearManual();
      }

      SilentRotationService.add(step[0], step[1]);
   }

   public strictfp float[] aimStep(float yaw, float pitch, float time, double now, float manualYaw, float manualPitch) {
      float deadZone = (float)this.range("fov").lower;
      yaw = yaw > 0.0F ? RotationMath.max(yaw - deadZone, 0.0F) : RotationMath.min(yaw + deadZone, 0.0F);
      pitch = pitch > 0.0F ? RotationMath.max(pitch - deadZone, 0.0F) : RotationMath.min(deadZone + pitch, 0.0F);
      int mode = this.choice("mode");
      if (mode != 2) {
         float draw = this.pitchDamping.sample(40.0F, 120.0F, 5.0, now, this.random);
         double slope = -1.0 / (double)draw;
         float magnitudeScale = RotationMath.max((float)(slope * (double)RotationMath.abs(yaw) + 1.0), 0.0F);
         pitch *= magnitudeScale;
      }

      if (mode == 2) {
         if (!this.flag("lock_on_vertical")) {
            pitch = 0.0F;
         }
      } else {
         float hSpeed = this.number("hor_speed");
         float vSpeed = this.number("ver_speed");
         if (mode == 3) {
            hSpeed = RotationMath.max(5.0F, hSpeed);
            if (5.0F > vSpeed) {
               vSpeed = 5.0F;
            }
         }

         float hHalf = hSpeed * 0.5F;
         float hSquared = hHalf * hHalf;
         float yawFactor = this.horizontalFactor.sample(0.9F, 1.1F, 1.0, now, this.random) * hSquared;
         float vHalf = vSpeed * 0.5F;
         float vSquared = vHalf * vHalf;
         float pitchFactor = this.verticalFactor.sample(0.9F, 1.1F, 1.0, now, this.random) * vSquared;
         float global = this.speedFactor.sample(0.95F, 1.05F, 60.0, now, this.random);
         if (mode == 1) {
            yawFactor *= 5.0F;
            yawFactor *= global;
            float yawStep = yawFactor * time;
            yaw = RotationMath.clamp(-yawStep, yawStep, yaw);
            pitchFactor *= 5.0F;
            pitchFactor *= global;
            float pitchStep = pitchFactor * time;
            pitch = RotationMath.clamp(-pitchStep, pitchStep, pitch);
         } else {
            float rawYaw = yawFactor * yaw;
            float rawPitch = pitchFactor * pitch;
            rawYaw *= global;
            rawPitch *= global;
            rawYaw *= time;
            rawPitch *= time;
            if (!(RotationMath.abs(rawYaw) > RotationMath.abs(yaw))) {
               yaw = rawYaw;
            }

            if (!(RotationMath.abs(rawPitch) > RotationMath.abs(pitch))) {
               pitch = rawPitch;
            }
         }

         float bound = (float)((double)RotationMath.abs(yaw) * 0.1);
         pitch += this.pitchNoise.sample(-bound, bound, 1.0, now, this.random);
      }

      if (this.flag("require_mouse_moved")) {
         float yawMagnitude = RotationMath.abs(yaw);
         float pitchMagnitude = RotationMath.abs(pitch);
         float manualYawMagnitude = RotationMath.abs(manualYaw);
         float manualPitchMagnitude = RotationMath.abs(manualPitch);
         boolean adjustYaw = manualYawMagnitude < yawMagnitude && yaw != 0.0F;
         float ratio = manualYawMagnitude / yawMagnitude;
         float scaledYaw = ratio * yaw;
         if (manualPitchMagnitude < pitchMagnitude && pitch != 0.0F) {
            float pitchRatio = manualPitchMagnitude / pitchMagnitude;
            pitch *= pitchRatio;
         }

         if (adjustYaw) {
            yaw = scaledYaw;
         }
      }

      return new float[]{yaw, pitch};
   }

   private static final strictfp class Candidate {
      Vec3 point;
      boolean enabled;

      private Candidate() {
      }
   }

   public static final strictfp class Score {
      public double distance;
      public float health = Float.MAX_VALUE;
      public float angle;
      public int hurtTime = Integer.MAX_VALUE;
      public int tieBreak;

      public Score(double range, float fov) {
         this.distance = range;
         this.angle = fov;
      }
   }

   private static final class ServerPosition {
      static final Field X = MappingBridge.field("net.minecraft.client.entity.EntityOtherPlayerMP", "otherPlayerMPX", null);
      static final Field Y = MappingBridge.field("net.minecraft.client.entity.EntityOtherPlayerMP", "otherPlayerMPY", null);
      static final Field Z = MappingBridge.field("net.minecraft.client.entity.EntityOtherPlayerMP", "otherPlayerMPZ", null);

      static strictfp Vec3 read(EntityOtherPlayerMP player) {
         try {
            return new Vec3(X.getDouble(player), Y.getDouble(player), Z.getDouble(player));
         } catch (IllegalAccessException var2) {
            throw new IllegalStateException("Cannot read target interpolation position", var2);
         }
      }
   }
}
