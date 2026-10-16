package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.LoadWorldEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.slinky.AimNoise;
import myau.slinky.RangeProperty;
import myau.slinky.RecoveredRandom;
import myau.slinky.RotationMath;
import myau.slinky.SlinkyModule;
import myau.slinky.SlinkyRotationService;
import myau.slinky.StatusText;
import myau.util.ItemUtil;
import myau.util.PlayerUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssist extends SlinkyModule {
   private static final Minecraft mc = Minecraft.getMinecraft();

   private static final int MODE_LOCK_ON = 0;
   private static final int MODE_SILENT = 1;
   private static final int MODE_NORMAL = 2;

   public final ModeProperty mode = new ModeProperty("mode", MODE_LOCK_ON, new String[]{"Lock-on", "Silent", "Normal"});
   public final BooleanProperty lock_on_vertical = new BooleanProperty("lock-on-vertical", false, () -> this.mode.getValue() == MODE_LOCK_ON);
   public final BooleanProperty silent_ignore_manual_aim = new BooleanProperty("silent-ignore-manual-aim", false, () -> this.mode.getValue() == MODE_SILENT);
   public final FloatProperty hor_speed = new FloatProperty("horizontal-speed", 15.0F, 0.0F, 30.0F, 0.1F, () -> this.mode.getValue() == MODE_SILENT);
   public final FloatProperty ver_speed = new FloatProperty("vertical-speed", 0.0F, 0.0F, 30.0F, 0.1F, () -> this.mode.getValue() == MODE_SILENT);
   public final PercentProperty predict = new PercentProperty("predict", 0, () -> this.mode.getValue() != MODE_NORMAL);
   public final PercentProperty aim_randomization = new PercentProperty("randomization", 50, () -> this.mode.getValue() != MODE_NORMAL);
   public final PercentProperty hor_hitbox_correct = new PercentProperty("horizontal", 0, () -> this.mode.getValue() != MODE_NORMAL);
   public final PercentProperty ver_hitbox_correct = new PercentProperty("vertical", 100, () -> this.mode.getValue() != MODE_NORMAL);
   public final ModeProperty mode_target = new ModeProperty("mode-target", 0, new String[]{"Single", "Switch"}, () -> this.mode.getValue() != MODE_NORMAL);
   public final ModeProperty sort_by = new ModeProperty("sort-by", 0, new String[]{"Distance", "Health", "Hurt time", "Aim angle"}, () -> this.mode.getValue() != MODE_NORMAL && this.mode_target.getValue() == 1);
   public final BooleanProperty allow_exceed_fov = new BooleanProperty("allow-exceed-fov", true, () -> this.mode.getValue() != MODE_NORMAL && this.mode_target.getValue() == 0);
   public final RangeProperty range = new RangeProperty("range", 1.0, 4.0, 0.0, 10.0, 0.1, () -> this.mode.getValue() != MODE_NORMAL);
   public final RangeProperty fov = new RangeProperty("fov", 4.0, 60.0, 0.0, 180.0, 1.0, () -> this.mode.getValue() != MODE_NORMAL);
   public final BooleanProperty require_sprint = new BooleanProperty("require-sprint", false, () -> this.mode.getValue() == MODE_LOCK_ON);
   public final BooleanProperty require_mouse_pressed = new BooleanProperty("require-mouse-pressed", false, () -> this.mode.getValue() != MODE_NORMAL);
   public final BooleanProperty require_mouse_moved = new BooleanProperty("require-mouse-moved", false, () -> this.mode.getValue() != MODE_NORMAL);
   public final BooleanProperty disable_on_block_break = new BooleanProperty("disable-on-block-break", false, () -> this.mode.getValue() != MODE_NORMAL);
   public final BooleanProperty require_weapon = new BooleanProperty("require-weapon", false, () -> this.mode.getValue() != MODE_NORMAL);
   public final BooleanProperty ignore_invis = new BooleanProperty("ignore-invis", true, () -> this.mode.getValue() != MODE_NORMAL);
   public final BooleanProperty bot_check = new BooleanProperty("bot-check", true);
   public final BooleanProperty require_line_of_sight = new BooleanProperty("require-line-of-sight", true, () -> this.mode.getValue() != MODE_NORMAL);
   public final FloatProperty normal_horizontal_speed = new FloatProperty("normal-horizontal-speed", 3.0F, 0.0F, 10.0F, () -> this.mode.getValue() == MODE_NORMAL);
   public final FloatProperty normal_vertical_speed = new FloatProperty("normal-vertical-speed", 0.0F, 0.0F, 10.0F, () -> this.mode.getValue() == MODE_NORMAL);
   public final PercentProperty normal_smoothing = new PercentProperty("normal-smoothing", 50, () -> this.mode.getValue() == MODE_NORMAL);
   public final PercentProperty normal_multipoint_horizontal = new PercentProperty("normal-multipoint-horizontal", 0, () -> this.mode.getValue() == MODE_NORMAL);
   public final PercentProperty normal_multipoint_vertical = new PercentProperty("normal-multipoint-vertical", 0, () -> this.mode.getValue() == MODE_NORMAL);
   public final FloatProperty normal_range = new FloatProperty("normal-range", 4.5F, 3.0F, 8.0F, () -> this.mode.getValue() == MODE_NORMAL);
   public final IntProperty normal_fov = new IntProperty("normal-fov", 90, 30, 360, () -> this.mode.getValue() == MODE_NORMAL);
   public final BooleanProperty normal_weapons_only = new BooleanProperty("normal-weapons-only", true, () -> this.mode.getValue() == MODE_NORMAL);
   public final BooleanProperty normal_allow_tools = new BooleanProperty("normal-allow-tools", false, () -> this.mode.getValue() == MODE_NORMAL && this.normal_weapons_only.getValue());
   public final BooleanProperty normal_teams = new BooleanProperty("normal-teams", true, () -> this.mode.getValue() == MODE_NORMAL);

   private final RecoveredRandom random = RecoveredRandom.SHARED;
   private final RecoveredRandom.Smooth pitchDamping = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth pitchNoise = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth horizontalFactor = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth verticalFactor = new RecoveredRandom.Smooth();
   private final RecoveredRandom.Smooth speedFactor = new RecoveredRandom.Smooth();
   private EntityPlayer target;
   private boolean targetActive;
   private boolean silent;
   private final TimerUtil normalTimer = new TimerUtil();
   private Vec3 previousAim = new Vec3(0.0, 0.0, 0.0);
   private Vec3 currentAim = this.previousAim;
   private long tieCounter;
   private double previousDisplaySeconds;

   public AimAssist() {
      super("Aim Assist");
      this.predict.unit("%");
      this.aim_randomization.unit("%");
      this.hor_hitbox_correct.unit("%");
      this.ver_hitbox_correct.unit("%");
      this.range.unit(" blocks");
      this.fov.unit("\u00b0");
   }

   @Override
   public String[] getSuffix() {
      return new String[]{StatusText.label("Aim Assist", this.choice("mode"))};
   }

   @Override
   public void onDisabled() {
      this.exitSilent();
      this.clearTarget();
      SlinkyRotationService.reset();
   }

   @EventTarget
   public void onLoadWorld(LoadWorldEvent event) {
      this.silent = false;
      this.clearTarget();
      this.previousDisplaySeconds = 0.0;
      SlinkyRotationService.reset();
   }

   private boolean focused() {
      return mc.thePlayer != null && mc.theWorld != null && mc.inGameHasFocus && mc.currentScreen == null;
   }

   private void clearTarget() {
      this.target = null;
      this.targetActive = false;
   }

   public EntityPlayer currentTarget() {
      return this.target;
   }

   public boolean targetActive() {
      return this.targetActive;
   }

   private void exitSilent() {
      if (this.silent && mc.thePlayer != null && SlinkyRotationService.isCameraActive()) {
         float yaw = SlinkyRotationService.cameraYaw();
         float pitch = RotationMath.clamp(-90.0F, 90.0F, SlinkyRotationService.cameraPitch());
         mc.thePlayer.rotationYaw = yaw;
         mc.thePlayer.rotationPitch = pitch;
         mc.thePlayer.prevRotationYaw = yaw;
         mc.thePlayer.prevRotationPitch = pitch;
         mc.thePlayer.prevRenderArmYaw = yaw;
         mc.thePlayer.prevRenderArmPitch = pitch;
         mc.thePlayer.renderArmYaw = yaw;
         mc.thePlayer.renderArmPitch = pitch;
      }
      this.silent = false;
      SlinkyRotationService.endCamera();
   }

   @EventTarget
   public void onRender3D(Render3DEvent event) {
      if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
         return;
      }
      if (this.choice("mode") == MODE_NORMAL) {
         return;
      }
      if (!this.silent) {
         SlinkyRotationService.beforeDisplay(mc.thePlayer);
      }
      double now = (double) System.nanoTime() / 1.0E9;
      if (this.previousDisplaySeconds == 0.0) {
         this.previousDisplaySeconds = now;
      }
      double elapsed = now - this.previousDisplaySeconds;
      if (!(0.002 > elapsed)) {
         this.rotate(event.getPartialTicks(), (float) elapsed);
         this.applyPending(false);
         this.previousDisplaySeconds = now;
         SlinkyRotationService.afterDisplay(mc.thePlayer);
      }
   }

   private void rotate(float partialTicks, float time) {
      if (!this.focused()) {
         return;
      }
      if (this.silent && this.choice("mode") != MODE_SILENT) {
         this.exitSilent();
      }
      float currentYaw = mc.thePlayer.rotationYaw + SlinkyRotationService.pendingYaw();
      float currentPitch = mc.thePlayer.rotationPitch + SlinkyRotationService.pendingPitch();
      if (!this.targetActive) {
         if (this.silent) {
            if (SlinkyRotationService.storedRotationValid() && SlinkyRotationService.claim(100)) {
               float yaw = RotationMath.wrap(SlinkyRotationService.storedYaw() - currentYaw);
               float pitch = SlinkyRotationService.storedPitch() - currentPitch;
               if (RotationMath.magnitude(yaw, pitch) < 10.0F) {
                  this.exitSilent();
                  return;
               }
               this.applyStep(yaw, pitch, time);
            } else {
               this.exitSilent();
            }
         }
      } else if (SlinkyRotationService.claim(100)) {
         if (!this.silent && this.choice("mode") == MODE_SILENT) {
            this.silent = true;
         }
         if (!this.silent || SlinkyRotationService.storedRotationValid()) {
            double fraction = (double) partialTicks;
            Vec3 previous = new Vec3(mc.thePlayer.prevPosX, mc.thePlayer.prevPosY, mc.thePlayer.prevPosZ);
            Vec3 current = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            Vec3 eye = RotationMath.interpolate(previous, current, fraction).addVector(0.0, 1.62, 0.0);
            float[] desired = RotationMath.angles(eye, RotationMath.interpolate(this.previousAim, this.currentAim, fraction));
            float yaw = RotationMath.wrap(desired[0] - currentYaw);
            float pitch = desired[1] - currentPitch;
            if (this.silent && !this.flag("silent-ignore-manual-aim")) {
               float restoreYaw = RotationMath.wrap(SlinkyRotationService.storedYaw() - currentYaw);
               float restorePitch = SlinkyRotationService.storedPitch() - currentPitch;
               float targetYaw = RotationMath.wrap(desired[0] - SlinkyRotationService.storedYaw());
               float targetPitch = desired[1] - SlinkyRotationService.storedPitch();
               if (RotationMath.magnitude(restoreYaw, restorePitch) < 10.0F
                     && RotationMath.magnitude(targetYaw, targetPitch) < RotationMath.magnitude(yaw, pitch)) {
                  SlinkyRotationService.add(restoreYaw, restorePitch);
                  return;
               }
            }
            this.applyStep(yaw, pitch, time);
         }
      }
   }

   private void applyStep(float yaw, float pitch, float time) {
      float[] step = this.aimStep(
            yaw, pitch, time, RecoveredRandom.now(),
            SlinkyRotationService.manualYaw(), SlinkyRotationService.manualPitch());
      if (this.flag("require-mouse-moved")) {
         SlinkyRotationService.clearManual();
      }
      SlinkyRotationService.add(step[0], step[1]);
   }

   private void applyPending(boolean alsoArm) {
      float[] step = SlinkyRotationService.consumePending();
      float yawStep = step[0];
      float pitchStep = step[1];
      if (yawStep == 0.0F && pitchStep == 0.0F) {
         return;
      }
      float yaw = yawStep + mc.thePlayer.rotationYaw;
      float pitch = RotationMath.clamp(-90.0F, 90.0F, pitchStep + mc.thePlayer.rotationPitch);
      mc.thePlayer.rotationYaw = yaw;
      mc.thePlayer.rotationPitch = pitch;
      mc.thePlayer.prevRotationYaw = yaw;
      mc.thePlayer.prevRotationPitch = pitch;
      if (alsoArm) {
         mc.thePlayer.prevRenderArmYaw = yaw;
         mc.thePlayer.prevRenderArmPitch = pitch;
         mc.thePlayer.renderArmYaw = yaw;
         mc.thePlayer.renderArmPitch = pitch;
      }
   }

   @EventTarget
   public void onUpdate(UpdateEvent event) {
      if (!this.isEnabled() || event.getType() != EventType.PRE) {
         return;
      }
      if (mc.thePlayer == null || mc.theWorld == null) {
         return;
      }
      if (this.silent) {
         event.setRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, 0);
         event.setPervRotation(mc.thePlayer.rotationYaw, 0);
      }
   }

   @EventTarget
   public void onTick(TickEvent event) {
      if (!this.isEnabled()) {
         return;
      }
      if (event.getType() == EventType.PRE) {
         if (mc.thePlayer != null && mc.theWorld != null) {
            SlinkyRotationService.beginFrame(mc.gameSettings.mouseSensitivity);
            if (this.targetActive && this.target != null) {
               SlinkyRotationService.claim(100);
            }
         }
      } else if (event.getType() == EventType.POST) {
         if (mc.thePlayer == null || mc.theWorld == null) {
            this.clearTarget();
            return;
         }
         if (this.choice("mode") == MODE_NORMAL) {
            if (this.silent) {
               this.exitSilent();
               SlinkyRotationService.reset();
            }
            this.clearTarget();
            this.normalAim();
            return;
         }
         this.updateTarget();
         if (this.silent && !SlinkyRotationService.storedRotationValid()) {
            SlinkyRotationService.beginCamera(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
         }
         this.applyPending(true);
      }
   }

   private boolean consider(
      EntityPlayer candidate, Vec3 eye, float viewYaw, boolean locked, Score best, Candidate result
   ) {
      if (candidate.isDead || candidate.isPlayerSleeping()) {
         return false;
      } else if (this.flag("ignore-invis") && candidate.isInvisibleToPlayer(mc.thePlayer)) {
         return false;
      } else if (TeamUtil.isFriend(candidate) || TeamUtil.isSameTeam(candidate)
            || (this.flag("bot-check") && TeamUtil.isBot(candidate))) {
         return false;
      } else {
         Vec3 position = new Vec3(candidate.posX, candidate.posY, candidate.posZ);
         float prediction = this.fraction("predict");
         if (prediction > 0.0F && candidate instanceof EntityOtherPlayerMP) {
            position = RotationMath.interpolate(position, ServerPosition.read((EntityOtherPlayerMP) candidate), (double) prediction);
         }

         AxisAlignedBB box = candidate.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
         Vec3 nearest = new Vec3(
            RotationMath.clamp(box.minX, box.maxX, eye.xCoord),
            RotationMath.clamp(box.minY, box.maxY, eye.yCoord),
            RotationMath.clamp(box.minZ, box.maxZ, eye.zCoord)
         );
         double distance = RotationMath.distance(eye, nearest);
         boolean enabled = !((double) ((float) this.range("range").lower) > distance);
         if (distance > best.distance) {
            return false;
         } else {
            Vec3 point = aimPoint(
               eye, position, nearest, distance,
               this.fraction("horizontal"), this.fraction("vertical"), this.fraction("randomization")
            );
            float angle = RotationMath.abs(RotationMath.wrap(RotationMath.angles(eye, point)[0] - viewYaw));
            if (angle > best.angle) {
               if (!locked) {
                  return false;
               }

               enabled = this.flag("allow-exceed-fov");
            }

            float health = candidate.getHealth();
            if (Float.isNaN(health)) {
               health = 20.0F;
            }

            if (!(health > best.health) && candidate.hurtTime <= best.hurtTime) {
               if (this.flag("require-line-of-sight") && mc.theWorld.rayTraceBlocks(eye, point, false, true, true) != null) {
                  return false;
               } else {
                  if (this.choice("mode-target") != 0) {
                     switch (this.choice("sort-by")) {
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

   private void updateTarget() {
      SlinkyRotationService.clearManual();
      if (!this.focused()) {
         this.clearTarget();
      } else if (this.flag("require-sprint") && this.choice("mode") != MODE_SILENT && !mc.thePlayer.isSprinting()) {
         this.targetActive = false;
      } else {
         int keyCode = mc.gameSettings.keyBindAttack.getKeyCode();
         boolean attack = Mouse.isButtonDown(keyCode == -99 ? 1 : 0);
         if (this.flag("require-mouse-pressed")) {
            if (!attack) {
               this.clearTarget();
               return;
            }
         }

         MovingObjectPosition hit = mc.objectMouseOver;
         if (this.flag("disable-on-block-break") && attack && hit != null && hit.typeOfHit == MovingObjectType.BLOCK) {
            this.targetActive = false;
         } else if (this.flag("require-weapon") && !this.isHoldingWeapon()) {
            this.clearTarget();
         } else {
            Vec3 eye = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + 1.62, mc.thePlayer.posZ);
            float viewYaw = SlinkyRotationService.storedRotationValid()
                  ? SlinkyRotationService.storedYaw() : mc.thePlayer.rotationYaw;
            Score best = new Score((double) ((float) this.range("range").upper), (float) this.range("fov").upper);
            Candidate selected = new Candidate();
            if (this.choice("mode-target") == 0 && this.target != null) {
               if (this.consider(this.target, eye, viewYaw, true, best, selected)) {
                  this.previousAim = this.currentAim;
                  this.currentAim = selected.point;
                  return;
               }

               this.clearTarget();
            }

            EntityPlayer chosen = null;

            for (int i = 0; i < mc.theWorld.playerEntities.size(); i++) {
               EntityPlayer candidate = (EntityPlayer) mc.theWorld.playerEntities.get(i);
               if (candidate != mc.thePlayer) {
                  Candidate result = new Candidate();
                  if (this.consider(candidate, eye, viewYaw, false, best, result)) {
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

   private boolean isHoldingWeapon() {
      return myau.slinky.WeaponsHelper.isWeapon(mc.thePlayer);
   }

   public static Vec3 aimPoint(Vec3 localEye, Vec3 predicted, Vec3 nearest, double distance, float horizontal, float vertical, float randomization) {
      Vec3 targetEye = new Vec3(predicted.xCoord, predicted.yCoord + 1.62, predicted.zCoord);
      double x = targetEye.xCoord;
      double z = targetEye.zCoord;
      if (horizontal > 0.0F) {
         float fraction = distance < 3.5 ? (float) ((double) horizontal * (distance / 3.5)) : horizontal;
         Vec3 point = RotationMath.interpolate(targetEye, nearest, (double) fraction);
         x = point.xCoord;
         z = point.zCoord;
      }

      double y = targetEye.yCoord + (nearest.yCoord - targetEye.yCoord) * (double) vertical;
      double sum = localEye.xCoord + localEye.yCoord;
      sum += localEye.zCoord;
      sum += x;
      sum += y;
      sum += z;
      float input = (float) (sum * 0.1);
      float noiseX = AimNoise.sample(input);
      float noiseY = AimNoise.sample(10000.0F + input);
      double halfX = (double) noiseX * 0.5;
      double halfY = (double) noiseY * 0.5;
      float noiseZ = AimNoise.sample(20000.0F + input);
      double halfZ = (double) noiseZ * 0.5;
      float randomScale = randomization;
      if (distance < 3.5) {
         randomScale = (float) ((double) randomization * (distance / 3.5 * 0.9 + 0.1));
      }

      double scale = (double) randomScale;
      return new Vec3(x + scale * halfX, y + scale * halfY, z + scale * halfZ);
   }

   public float[] aimStep(float yaw, float pitch, float time, double now, float manualYaw, float manualPitch) {
      float deadZone = (float) this.range("fov").lower;
      yaw = yaw > 0.0F ? RotationMath.max(yaw - deadZone, 0.0F) : RotationMath.min(yaw + deadZone, 0.0F);
      pitch = pitch > 0.0F ? RotationMath.max(pitch - deadZone, 0.0F) : RotationMath.min(deadZone + pitch, 0.0F);
      int mode = this.choice("mode");
      if (mode != MODE_LOCK_ON) {
         float draw = this.pitchDamping.sample(40.0F, 120.0F, 5.0, now, this.random);
         double slope = -1.0 / (double) draw;
         float magnitudeScale = RotationMath.max((float) (slope * (double) RotationMath.abs(yaw) + 1.0), 0.0F);
         pitch *= magnitudeScale;
      }

      if (mode == MODE_LOCK_ON) {
         if (!this.flag("lock-on-vertical")) {
            pitch = 0.0F;
         }
      } else {
         float hSpeed = this.number("horizontal-speed");
         float vSpeed = this.number("vertical-speed");
         hSpeed = RotationMath.max(5.0F, hSpeed);
         if (5.0F > vSpeed) {
            vSpeed = 5.0F;
         }

         float hHalf = hSpeed * 0.5F;
         float hSquared = hHalf * hHalf;
         float yawFactor = this.horizontalFactor.sample(0.9F, 1.1F, 1.0, now, this.random) * hSquared;
         float vHalf = vSpeed * 0.5F;
         float vSquared = vHalf * vHalf;
         float pitchFactor = this.verticalFactor.sample(0.9F, 1.1F, 1.0, now, this.random) * vSquared;
         float global = this.speedFactor.sample(0.95F, 1.05F, 60.0, now, this.random);
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

         float bound = (float) ((double) RotationMath.abs(yaw) * 0.1);
         pitch += this.pitchNoise.sample(-bound, bound, 1.0, now, this.random);
      }

      if (this.flag("require-mouse-moved")) {
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

   private boolean isValidNormalTarget(EntityPlayer entityPlayer) {
      if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
         if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
            return false;
         } else if (entityPlayer.deathTime > 0) {
            return false;
         } else if (RotationUtil.distanceToEntity(entityPlayer) > (double) this.normal_range.getValue()) {
            return false;
         } else if (RotationUtil.angleToEntity(entityPlayer) > (float) this.normal_fov.getValue()) {
            return false;
         } else if (RotationUtil.rayTrace(entityPlayer) != null) {
            return false;
         } else if (TeamUtil.isFriend(entityPlayer)) {
            return false;
         } else {
            return (!this.normal_teams.getValue() || !TeamUtil.isSameTeam(entityPlayer))
                  && (!this.bot_check.getValue() || !TeamUtil.isBot(entityPlayer));
         }
      } else {
         return false;
      }
   }

   private boolean isInNormalReach(EntityPlayer entityPlayer) {
      Reach reach = (Reach) Myau.moduleManager.modules.get(Reach.class);
      double distance = reach.isEnabled() ? (double) reach.range.getValue() : 3.0;
      return RotationUtil.distanceToEntity(entityPlayer) <= distance;
   }

   private boolean isLookingAtBlock() {
      return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
   }

   private void normalAim() {
      if (mc.currentScreen != null) {
         return;
      }
      if (this.normal_weapons_only.getValue()
            && !ItemUtil.hasRawUnbreakingEnchant()
            && !(this.normal_allow_tools.getValue() && ItemUtil.isHoldingTool())) {
         return;
      }
      boolean attacking = PlayerUtil.isAttacking();
      if (attacking && this.isLookingAtBlock()) {
         return;
      }
      if (!attacking && this.normalTimer.hasTimeElapsed(350L)) {
         return;
      }
      List<EntityPlayer> inRange = mc.theWorld
            .loadedEntityList
            .stream()
            .filter(entity -> entity instanceof EntityPlayer)
            .map(entity -> (EntityPlayer) entity)
            .filter(this::isValidNormalTarget)
            .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
            .collect(Collectors.toList());
      if (inRange.isEmpty()) {
         return;
      }
      if (inRange.stream().anyMatch(this::isInNormalReach)) {
         inRange.removeIf(entityPlayer -> !this.isInNormalReach(entityPlayer));
      }
      EntityPlayer player = inRange.get(0);
      if (RotationUtil.distanceToEntity(player) <= 0.0) {
         return;
      }
      AxisAlignedBB axisAlignedBB = player.getEntityBoundingBox();
      double collisionBorderSize = player.getCollisionBorderSize();
      float[] rotation = RotationUtil.getRotationsToBox(
            axisAlignedBB.expand(collisionBorderSize, collisionBorderSize, collisionBorderSize),
            mc.thePlayer.rotationYaw,
            mc.thePlayer.rotationPitch,
            180.0F,
            (float) this.normal_smoothing.getValue() / 100.0F,
            (float) this.normal_multipoint_horizontal.getValue(),
            (float) this.normal_multipoint_vertical.getValue()
      );
      float yaw = Math.min(Math.abs(this.normal_horizontal_speed.getValue()), 10.0F);
      float pitch = Math.min(Math.abs(this.normal_vertical_speed.getValue()), 10.0F);
      Myau.rotationManager.setRotation(
            mc.thePlayer.rotationYaw + (rotation[0] - mc.thePlayer.rotationYaw) * 0.1F * yaw,
            mc.thePlayer.rotationPitch + (rotation[1] - mc.thePlayer.rotationPitch) * 0.1F * pitch,
            0,
            false
      );
   }

   @EventTarget
   public void onPress(KeyEvent event) {
      if (event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode()
            && !Myau.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
         this.normalTimer.reset();
      }
   }

   private static final class Candidate {
      Vec3 point;
      boolean enabled;

      private Candidate() {
      }
   }

   public static final class Score {
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
      static final Field X = find("otherPlayerMPX", "field_71185_c");
      static final Field Y = find("otherPlayerMPY", "field_71182_d");
      static final Field Z = find("otherPlayerMPZ", "field_71183_e");

      static Field find(String mcp, String srg) {
         try {
            Field field = EntityOtherPlayerMP.class.getDeclaredField(mcp);
            field.setAccessible(true);
            return field;
         } catch (NoSuchFieldException ignored) {
         }
         try {
            Field field = EntityOtherPlayerMP.class.getDeclaredField(srg);
            field.setAccessible(true);
            return field;
         } catch (NoSuchFieldException ignored) {
         }
         return null;
      }

      static Vec3 read(EntityOtherPlayerMP player) {
         if (X == null || Y == null || Z == null) {
            return new Vec3(player.posX, player.posY, player.posZ);
         }
         try {
            return new Vec3(X.getDouble(player), Y.getDouble(player), Z.getDouble(player));
         } catch (IllegalAccessException | IllegalArgumentException e) {
            return new Vec3(player.posX, player.posY, player.posZ);
         }
      }
   }
}
