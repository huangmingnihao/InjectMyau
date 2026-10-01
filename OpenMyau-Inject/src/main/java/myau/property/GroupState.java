package myau.property;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GroupState {
    private final String label;
    private boolean opened;
    public GroupState(String label, boolean opened) {
        this.label = label;
        this.opened = opened;
    }
    public String getLabel() {
        return this.label;
    }
    public boolean isOpened() {
        return this.opened;
    }
    public void setOpened(boolean opened) {
        this.opened = opened;
    }
    public void toggle() {
        this.opened = !this.opened;
    }
    public String getMarker() {
        return this.opened ? "[v]" : "[>]";
    }

    public static final class Registry<K> {
        private final Map<K, GroupState> groups = new LinkedHashMap<K, GroupState>();

        public GroupState get(K key, String label, boolean openByDefault) {
            GroupState existing = this.groups.get(key);
            if (existing != null) {
                return existing;
            }
            GroupState created = new GroupState(label, openByDefault);
            this.groups.put(key, created);
            return created;
        }
    }
}
