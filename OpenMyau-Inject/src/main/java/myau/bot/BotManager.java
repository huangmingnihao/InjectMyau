package myau.bot;

import net.minecraft.entity.Entity;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BotManager {
    private final Map<Object, Set<Integer>> flagged = new ConcurrentHashMap<Object, Set<Integer>>();
    private final Set<String> realNames = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<UUID> realUuids = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    public boolean isBot(Entity entity) {
        if (entity == null) {
            return false;
        }
        Integer id = Integer.valueOf(entity.getEntityId());
        for (Set<Integer> ids : this.flagged.values()) {
            if (ids.contains(id)) {
                return true;
            }
        }
        return false;
    }
    public boolean isBot(Object check, Entity entity) {
        Set<Integer> ids = this.flagged.get(check);
        return ids != null && entity != null && ids.contains(Integer.valueOf(entity.getEntityId()));
    }
    public void mark(Object check, Entity entity) {
        if (entity == null) {
            return;
        }
        setFor(check).add(Integer.valueOf(entity.getEntityId()));
    }
    public void unmark(Object check, Entity entity) {
        if (entity == null) {
            return;
        }
        Set<Integer> ids = this.flagged.get(check);
        if (ids != null) {
            ids.remove(Integer.valueOf(entity.getEntityId()));
        }
    }
    public void clear(Object check) {
        Set<Integer> ids = this.flagged.get(check);
        if (ids != null) {
            ids.clear();
        }
    }

    public void confirmReal(String name, UUID uuid) {
        if (name != null && !name.isEmpty()) {
            this.realNames.add(name);
        }
        if (uuid != null) {
            this.realUuids.add(uuid);
        }
    }

    public boolean isConfirmedReal(String name, UUID uuid) {
        return name != null && this.realNames.contains(name)
                || uuid != null && this.realUuids.contains(uuid);
    }

    public void clear() {
        this.flagged.clear();
    }
    private Set<Integer> setFor(Object check) {
        Set<Integer> ids = this.flagged.get(check);
        if (ids == null) {
            ids = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
            Set<Integer> raced = this.flagged.putIfAbsent(check, ids);
            if (raced != null) {
                ids = raced;
            }
        }
        return ids;
    }
}
