package myau.slinky;

import net.minecraft.util.Vec3;

public final class RotationMath {
   private RotationMath() {
   }

   public static float abs(float value) {
      return Float.intBitsToFloat(Float.floatToRawIntBits(value) & 2147483647);
   }

   public static float min(float a, float b) {
      return a < b ? a : b;
   }

   public static float max(float a, float b) {
      return a > b ? a : b;
   }

   public static double min(double a, double b) {
      return a < b ? a : b;
   }

   public static double max(double a, double b) {
      return a > b ? a : b;
   }

   public static float clamp(float low, float high, float value) {
      return value < low ? low : min(high, value);
   }

   public static double clamp(double low, double high, double value) {
      return value < low ? low : min(high, value);
   }

   public static float wrap(float angle) {
      float result = angle % 360.0F;
      if (result >= 180.0F) {
         result += -360.0F;
      } else if (result < -180.0F) {
         result += 360.0F;
      }
      return result;
   }

   public static float[] angles(Vec3 from, Vec3 to) {
      double x = to.xCoord - from.xCoord;
      double y = to.yCoord - from.yCoord;
      double z = to.zCoord - from.zCoord;
      double horizontal = Math.sqrt(x * x + z * z);
      float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI + -90.0);
      float pitch = (float) (Math.atan2(y, horizontal) * -180.0 / Math.PI);
      return new float[]{yaw, pitch};
   }

   public static Vec3 interpolate(Vec3 a, Vec3 b, double fraction) {
      return new Vec3(fraction * (b.xCoord - a.xCoord) + a.xCoord, fraction * (b.yCoord - a.yCoord) + a.yCoord, (b.zCoord - a.zCoord) * fraction + a.zCoord);
   }

   public static double distance(Vec3 a, Vec3 b) {
      double x = a.xCoord - b.xCoord;
      double y = a.yCoord - b.yCoord;
      double z = a.zCoord - b.zCoord;
      return Math.sqrt(z * z + (x * x + y * y));
   }

   public static float magnitude(float yaw, float pitch) {
      return RecoveredMath.floatDistance(yaw, pitch);
   }

   public static double[] movement(float yaw, float forward, float sideways, float speed) {
      float f = forward * 0.98F;
      float s = sideways * 0.98F;
      float squared = s * s + f * f;
      if (1.0E-4F > squared) {
         return new double[]{0.0, 0.0};
      } else {
         float scale = speed * Float.intBitsToFloat(1065353213);
         float length = max(1.0F, (float) Math.sqrt((double) squared));
         scale /= length;
         s *= scale;
         f *= scale;
         float angle = (float) ((double) yaw * Math.PI / 180.0);
         float sin = (float) Math.sin((double) angle);
         float cos = (float) Math.cos((double) angle);
         return new double[]{(double) (s * cos - f * sin), (double) (f * cos + s * sin)};
      }
   }

   public static int tieHash(int entityId, long counter) {
      int x = entityId + (int) counter;
      x ^= x >>> 16;
      x *= 73244475;
      x ^= x >>> 16;
      x *= 73244475;
      return x ^ x >>> 16;
   }
}
