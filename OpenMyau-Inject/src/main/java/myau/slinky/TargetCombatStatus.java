package myau.slinky;

import myau.Myau;
import myau.module.modules.AimAssist;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;

public final class TargetCombatStatus {
   private static final Minecraft mc = Minecraft.getMinecraft();
   private static EntityPlayer target;
   private static double distanceSquared;
   private static boolean valid;
   private static boolean reducing;

   private TargetCombatStatus() {
   }

   public static EntityPlayer target() {
      return target;
   }

   public static double distanceSquared() {
      return distanceSquared;
   }

   public static boolean reducing() {
      return reducing;
   }

   private static boolean hostile(EntityPlayer player) {
      return !TeamUtil.isFriend(player) && !TeamUtil.isSameTeam(player) && !TeamUtil.isBot(player);
   }

   public static void select() {
      valid = false;
      if (mc.thePlayer == null || mc.theWorld == null) {
         target = null;
         return;
      }
      Vec3 eye = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + 1.62, mc.thePlayer.posZ);
      EntityPlayer chosen = null;
      MovingObjectPosition hit = mc.objectMouseOver;
      if (hit != null && hit.typeOfHit == MovingObjectType.ENTITY && hit.entityHit instanceof EntityOtherPlayerMP) {
         EntityPlayer p = (EntityPlayer) hit.entityHit;
         if (hostile(p) && !(0.0F >= p.getHealth())) {
            chosen = p;
         }
      }

      if (chosen == null) {
         AimAssist aim = (AimAssist) Myau.moduleManager.modules.get(AimAssist.class);
         if (aim != null) {
            chosen = aim.currentTarget();
         }
      }

      if (chosen != null) {
         target = chosen;
         distanceSquared = boxDistance(eye, chosen);
      } else {
         double best = 225.0;

         for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p != mc.thePlayer && hostile(p) && !(0.0F >= p.getHealth()) && !p.isPlayerSleeping()) {
               double distance = boxDistance(eye, p);
               if (!(distance > best)) {
                  float[] angle = RotationMath.angles(eye, new Vec3(p.posX, p.posY, p.posZ));
                  float yaw = RotationMath.wrap(angle[0] - mc.thePlayer.rotationYaw);
                  float pitch = angle[1] - mc.thePlayer.rotationPitch;
                  float metric = yaw * yaw;
                  metric += pitch;
                  metric += pitch;
                  if (!(metric > 4900.0F)) {
                     best = distance;
                     chosen = p;
                  }
               }
            }
         }

         target = chosen;
         if (chosen != null) {
            distanceSquared = best;
         }
      }
   }

   public static void prepare() {
      if (!valid) {
         MovingObjectPosition hit = mc.objectMouseOver;
         if (mc.thePlayer != null && mc.theWorld != null && hit != null && hit.typeOfHit == MovingObjectType.ENTITY) {
            sample(
               true,
               new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + 1.62, mc.thePlayer.posZ),
               hit.hitVec,
               mc.thePlayer.motionX,
               mc.thePlayer.motionY,
               mc.thePlayer.motionZ,
               mc.thePlayer.rotationYaw,
               mc.thePlayer.moveForward,
               mc.thePlayer.moveStrafing,
               mc.thePlayer.onGround ? mc.thePlayer.getAIMoveSpeed() : 0.026F
            );
         } else {
            sample(false, null, null, 0.0, 0.0, 0.0, 0.0F, 0.0F, 0.0F, 0.0F);
         }
      }
   }

   public static void sample(boolean entityHit, Vec3 eye, Vec3 hit, double x, double y, double z, float yaw, float forward, float sideways, float speed) {
      if (!valid) {
         reducing = true;
         if (entityHit) {
            double[] movement = RotationMath.movement(yaw, forward, sideways, speed);
            double normalX = x + movement[0];
            double normalY = y + 0.0;
            double normalZ = movement[1] + z;
            double reducedX = x * 0.6 + movement[0];
            double reducedY = y + 0.0;
            double reducedZ = movement[1] + 0.6 * z;
            double reducedDistance = squared(eye.xCoord + reducedX, eye.yCoord + reducedY, reducedZ + eye.zCoord, hit);
            double normalDistance = squared(eye.xCoord + normalX, eye.yCoord + normalY, normalZ + eye.zCoord, hit);
            reducing = normalDistance > reducedDistance;
         }

         valid = true;
      }
   }

   private static double squared(double x, double y, double z, Vec3 hit) {
      x -= hit.xCoord;
      y -= hit.yCoord;
      z -= hit.zCoord;
      return z * z + (x * x + y * y);
   }

   private static double boxDistance(Vec3 eye, Entity entity) {
      AxisAlignedBB box = entity.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
      double x = RotationMath.clamp(box.minX, box.maxX, eye.xCoord) - eye.xCoord;
      double y = RotationMath.clamp(box.minY, box.maxY, eye.yCoord) - eye.yCoord;
      double z = RotationMath.clamp(box.minZ, box.maxZ, eye.zCoord) - eye.zCoord;
      return z * z + (x * x + y * y);
   }
}
