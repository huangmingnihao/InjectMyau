package myau.slinky.silent;

/** Only the reference rotation/frame event fields used by Silent AimAssist. */
public final class SilentAimFrame {
    public boolean active;
    public boolean overrideRotation;
    public boolean remapMovement;
    public float yaw, pitch;
    public float timerRenderPartialTicks;
    public double elapsedSeconds;
}
