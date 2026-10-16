package myau.slinky;

import java.security.SecureRandom;

public final class RecoveredRandom {
   public static final RecoveredRandom SHARED = fresh();
   private int a;
   private int b;
   private int c;
   private int d;

   public RecoveredRandom(int a, int b, int c, int d) {
      if ((a | b | c | d) == 0) {
         throw new IllegalArgumentException("zero random state");
      } else {
         this.a = a;
         this.b = b;
         this.c = c;
         this.d = d;
      }
   }

   private static RecoveredRandom fresh() {
      SecureRandom seed = new SecureRandom();
      int a = seed.nextInt();
      int b = seed.nextInt();
      int c = seed.nextInt();
      int d = seed.nextInt();
      if ((a | b | c | d) == 0) {
         d = 1;
      }
      return new RecoveredRandom(a, b, c, d);
   }

   public static double now() {
      return (double) System.nanoTime() / 1.0E9;
   }

   private int next() {
      int sum = this.d + this.a;
      int rotated = Integer.rotateLeft(this.b ^ this.d, 11);
      int nextB = this.c ^ this.a ^ this.b;
      int nextA = this.a ^ this.b ^ this.d;
      int nextC = this.c ^ this.a ^ this.b << 9;
      this.a = nextA;
      this.b = nextB;
      this.c = nextC;
      this.d = rotated;
      return sum;
   }

   public float interval(float low, float high) {
      float width = high - low;
      float value = (float) (this.next() >>> 8);
      value *= 5.9604645E-8F;
      value *= width;
      return value + low;
   }

   public boolean chance(float threshold) {
      return threshold > this.interval(0.0F, 1.0F);
   }

   public static final class Smooth {
      private double timestamp;
      private float left;
      private float right;

      public float sample(float low, float high, double duration, double now, RecoveredRandom random) {
         if (now > this.timestamp + duration) {
            this.timestamp = now;
            if (this.left == 0.0F && this.right == 0.0F) {
               do {
                  this.left = random.interval(0.0F, 1.0F);
                  this.right = random.interval(0.0F, 1.0F);
               } while (0.2 > (double) RotationMath.abs(this.left - this.right));
            } else {
               this.left = this.right;

               do {
                  this.right = random.interval(0.0F, 1.0F);
               } while (0.2 > (double) RotationMath.abs(this.left - this.right));
            }
         }

         double phase = (now - this.timestamp) / duration;
         double curve = (double) (this.right - this.left) * phase + (double) this.left;
         return (float) ((double) (high - low) * curve + (double) low);
      }
   }

   public static final class Gaussian {
      private boolean valid;
      private float cached;

      public float sample(RecoveredRandom random) {
         if (this.valid) {
            this.valid = false;
            return this.cached;
         } else {
            float x;
            float y;
            float radius;
            do {
               float value = (float) (random.next() >>> 8) * 5.9604645E-8F;
               x = value + value + -1.0F;
               value = (float) (random.next() >>> 8) * 5.9604645E-8F;
               y = value + value + -1.0F;
               radius = RecoveredMath.floatDistance(x, y);
            } while (radius >= 1.0F || radius == 0.0F);

            float ratio = (float) Math.log((double) radius) * -2.0F / radius;
            float factor = (float) Math.sqrt((double) ratio);
            this.cached = y * factor;
            this.valid = true;
            return x * factor;
         }
      }
   }
}
