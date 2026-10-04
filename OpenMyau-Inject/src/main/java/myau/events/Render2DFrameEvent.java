package myau.events;

import myau.event.events.Event;

/** Screen overlays drawn after each camera frame, independently of HUD caching. */
public final class Render2DFrameEvent implements Event {
    private final float partialTicks;

    public Render2DFrameEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public float getPartialTicks() {
        return partialTicks;
    }
}
