package dev.havoc.spawners.feature;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.spawner.SpawnerData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named groups of spawners.
 * <p>
 * A network is the unit the aggregate dialog works on: one button sells, collects or drains every
 * spawner in it, and the analytics panel rolls their numbers up.
 */
public final class NetworkService {

    private final HavocSpawners plugin;
    private final Map<UUID, Set<String>> networks = new ConcurrentHashMap<>();

    public NetworkService(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    public void load() {
        networks.clear();
        for (String[] row : plugin.storage().loadNetworks()) {
            try {
                UUID owner = UUID.fromString(row[0]);
                networks.computeIfAbsent(owner, key -> new LinkedHashSet<>()).add(row[1]);
            } catch (IllegalArgumentException ignored) {
                // malformed row, skip
            }
        }
    }

    public Set<String> of(UUID owner) {
        return new LinkedHashSet<>(networks.getOrDefault(owner, Set.of()));
    }

    public boolean exists(UUID owner, String name) {
        Set<String> set = networks.get(owner);
        if (set == null) {
            return false;
        }
        for (String value : set) {
            if (value.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public String create(UUID owner, String rawName) {
        String name = sanitize(rawName);
        if (name.isEmpty()) {
            return null;
        }
        Set<String> set = networks.computeIfAbsent(owner, key -> new LinkedHashSet<>());
        if (set.size() >= plugin.settings().maxNetworksPerPlayer && !exists(owner, name)) {
            return null;
        }
        set.add(name);
        plugin.sched().async(() -> plugin.storage().saveNetwork(owner, name));
        return name;
    }

    public void delete(UUID owner, String name) {
        Set<String> set = networks.get(owner);
        if (set != null) {
            set.removeIf(value -> value.equalsIgnoreCase(name));
        }
        for (SpawnerData spawner : plugin.spawners().all()) {
            if (name.equalsIgnoreCase(spawner.network()) && owner.equals(spawner.owner())) {
                spawner.network(null);
                plugin.storage().queueSave(spawner);
            }
        }
        plugin.sched().async(() -> plugin.storage().deleteNetwork(owner, name));
    }

    public boolean assign(SpawnerData spawner, UUID owner, String name) {
        if (name == null) {
            spawner.network(null);
            plugin.storage().queueSave(spawner);
            return true;
        }
        if (!exists(owner, name)) {
            return false;
        }
        if (members(owner, name).size() >= plugin.settings().maxSpawnersPerNetwork) {
            return false;
        }
        spawner.network(name);
        plugin.storage().queueSave(spawner);
        return true;
    }

    public List<SpawnerData> members(UUID owner, String name) {
        return plugin.spawners().inNetwork(owner, name);
    }

    public List<String> namesFor(UUID owner) {
        return new ArrayList<>(of(owner));
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim().replaceAll("[^A-Za-z0-9 _-]", "");
        if (cleaned.length() > 24) {
            cleaned = cleaned.substring(0, 24);
        }
        return cleaned.trim();
    }

    public static String normalise(String raw) {
        return raw == null ? null : raw.toLowerCase(Locale.ROOT);
    }
}
