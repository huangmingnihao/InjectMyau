package myau.events;

import myau.event.events.Event;
import myau.event.types.EventType;

/** Phase information required by the existing independent Swap+ listener. */
public final class SwapPlusEvent implements Event {
    private final EventType type;

    public SwapPlusEvent(EventType type) {
        this.type = type;
    }

    public EventType getType() {
        return type;
    }
}
