package myau.events;

import myau.event.events.Event;

/**
 */
public class StuckMoveInputEvent implements Event {
    private float moveForward;
    private float moveStrafe;
    private boolean jump;
    private boolean sneak;
    private double sneakSlowdown = 0.3D;

    public StuckMoveInputEvent(float moveForward, float moveStrafe, boolean jump, boolean sneak) {
        this.moveForward = moveForward;
        this.moveStrafe = moveStrafe;
        this.jump = jump;
        this.sneak = sneak;
    }

    public float getForward() {
        return this.moveForward;
    }

    public void setForward(float forward) {
        this.moveForward = forward;
    }

    public float getStrafe() {
        return this.moveStrafe;
    }

    public void setStrafe(float strafe) {
        this.moveStrafe = strafe;
    }

    public boolean isJump() {
        return this.jump;
    }

    public void setJump(boolean jump) {
        this.jump = jump;
    }

    public boolean isSneak() {
        return this.sneak;
    }

    public void setSneak(boolean sneak) {
        this.sneak = sneak;
    }

    public double getSneakSlowdown() {
        return this.sneakSlowdown;
    }

    public void setSneakSlowdown(double slowdown) {
        this.sneakSlowdown = slowdown;
    }
}
