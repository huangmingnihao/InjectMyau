package myau.slinky;

public final class RecoveredMath {
   private RecoveredMath() {
   }

   public static float floatDistance(float first, float second) {
      int a = Float.floatToRawIntBits(first) & 2147483647;
      int b = Float.floatToRawIntBits(second) & 2147483647;
      if (a == 2139095040 || b == 2139095040) {
         return Float.POSITIVE_INFINITY;
      } else if (a <= 2139095040 && b <= 2139095040) {
         double x = (double) first;
         double y = (double) second;
         return (float) Math.sqrt(x * x + y * y);
      } else {
         return Float.NaN;
      }
   }

   public static int truncateInt(float value) {
      return !Float.isNaN(value) && !(value >= 2.1474836E9F) && !(value < -2.1474836E9F) ? (int) value : Integer.MIN_VALUE;
   }

   public static int truncateInt(double value) {
      return !Double.isNaN(value) && !(value >= 2.1474836E9F) && !(value < -2.1474836E9F) ? (int) value : Integer.MIN_VALUE;
   }

   public static long truncateLong(double value) {
      return !Double.isNaN(value) && !(value >= 9.223372E18F) && !(value < -9.223372E18F) ? (long) value : Long.MIN_VALUE;
   }
}
