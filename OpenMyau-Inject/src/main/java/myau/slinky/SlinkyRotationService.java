package myau.slinky;

import net.minecraft.entity.player.EntityPlayer;

public final class SlinkyRotationService {
   private static final float[] ZERO = new float[]{0.0F, 0.0F};

   private static float quantum;
   private static int deltaAge;
   private static float pendingYaw;
   private static float pendingPitch;
   private static float manualYaw;
   private static float manualPitch;
   private static float displayYaw = Float.NaN;
   private static float displayPitch = Float.NaN;
   private static boolean active;
   private static float cameraYaw;
   private static float cameraPitch;
   private static int priority;

   private SlinkyRotationService() {
   }

   public static void beginFrame(float rawMouseSensitivity) {
      float sensitivity = rawMouseSensitivity * 0.6F + 0.2F;
      quantum = sensitivity * sensitivity * sensitivity * 8.0F * 0.15F;
      int previousAge = deltaAge++;
      if (previousAge > 0) {
         pendingYaw = 0.0F;
         pendingPitch = 0.0F;
      }
      priority = 0;
   }

   public static boolean claim(int requested) {
      if (Integer.compareUnsigned(requested, priority) < 0) {
         return false;
      }
      priority = requested;
      return true;
   }

   public static boolean allows(int requested) {
      return Integer.compareUnsigned(requested, priority) >= 0;
   }

   public static boolean storedRotationValid() {
      return active;
   }

   public static float storedYaw() {
      return cameraYaw;
   }

   public static float storedPitch() {
      return cameraPitch;
   }

   public static void beginCamera(float yaw, float pitch) {
      active = true;
      cameraYaw = yaw;
      cameraPitch = pitch;
   }

   public static void endCamera() {
      active = false;
   }

   public static boolean isCameraActive() {
      return active;
   }

   public static float cameraYaw() {
      return cameraYaw;
   }

   public static float cameraPitch() {
      return cameraPitch;
   }

   public static void consumeMouse(float dx, float dy) {
      float yawStep = dx * 0.15F;
      float pitchStep = dy * 0.15F;
      cameraYaw += yawStep;
      cameraPitch = RotationMath.clamp(-90.0F, 90.0F, cameraPitch - pitchStep);
      manualDelta(yawStep, pitchStep);
   }

   public static void add(float yaw, float pitch) {
      pendingYaw += yaw;
      pendingPitch += pitch;
      deltaAge = 0;
   }

   public static float pendingYaw() {
      return pendingYaw;
   }

   public static float pendingPitch() {
      return pendingPitch;
   }

   public static void manualDelta(float yaw, float pitch) {
      manualYaw += yaw;
      manualPitch += pitch;
   }

   public static float manualYaw() {
      return manualYaw;
   }

   public static float manualPitch() {
      return manualPitch;
   }

   public static void clearManual() {
      manualYaw = 0.0F;
      manualPitch = 0.0F;
   }

   public static void beforeDisplay(EntityPlayer player) {
      if (player == null) {
         return;
      }
      if (Float.isNaN(displayYaw)) {
         displayYaw = player.rotationYaw;
         displayPitch = player.rotationPitch;
         return;
      }
      float yaw = player.rotationYaw - displayYaw;
      float pitch = player.rotationPitch - displayPitch;
      if (((Float.floatToRawIntBits(yaw) | Float.floatToRawIntBits(pitch)) & 0x7FFFFFFF) != 0) {
         manualDelta(yaw, pitch);
      }
   }

   public static void afterDisplay(EntityPlayer player) {
      if (player != null) {
         displayYaw = player.rotationYaw;
         displayPitch = player.rotationPitch;
      }
   }

   public static float quantize(float value) {
      if (quantum != 0.0F && (-quantum >= value || value >= quantum)) {
         float quotient = value / quantum;
         return (float) (int) quotient * quantum;
      }
      return 0.0F;
   }

   public static float[] consumePending() {
      if (quantum == 0.0F || (pendingYaw == 0.0F && pendingPitch == 0.0F)) {
         return ZERO;
      }
      float yawStep = quantize(pendingYaw);
      float pitchStep = quantize(pendingPitch);
      pendingYaw -= yawStep;
      pendingPitch -= pitchStep;
      if (yawStep == 0.0F && pitchStep == 0.0F) {
         return ZERO;
      }
      return new float[]{yawStep, pitchStep};
   }

   public static void reset() {
      quantum = 0.0F;
      deltaAge = 0;
      priority = 0;
      pendingYaw = 0.0F;
      pendingPitch = 0.0F;
      manualYaw = 0.0F;
      manualPitch = 0.0F;
      displayYaw = Float.NaN;
      displayPitch = Float.NaN;
      active = false;
      cameraYaw = 0.0F;
      cameraPitch = 0.0F;
   }
}
