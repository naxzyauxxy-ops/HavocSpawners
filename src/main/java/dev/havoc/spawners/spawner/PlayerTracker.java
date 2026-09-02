package dev.havoc.spawners.spawner;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lock-free cache of where every online player is.
 * <p>
 * The spawner tick needs proximity checks but must never touch the world from a foreign region
 * thread. Movement events write here from whichever region owns the player; the tick only reads.
 */
public final class PlayerTracker {

    public record Pos(String world, double x, double y, double z) {
    }

    private final Map<UUID, Pos> positions = new ConcurrentHashMap<>();

    public void update(Player player) {
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            return;
        }
        positions.put(player.getUniqueId(),
                new Pos(location.getWorld().getName(), location.getX(), location.getY(), location.getZ()));
    }

    public void remove(UUID uuid) {
        positions.remove(uuid);
    }

    public void clear() {
        positions.clear();
    }

    public int size() {
        return positions.size();
    }

    public boolean anyWithin(BlockKey key, int range) {
        if (positions.isEmpty()) {
            return false;
        }
        double limit = (double) range * range;
        double bx = key.x() + 0.5D;
        double by = key.y() + 0.5D;
        double bz = key.z() + 0.5D;
        for (Pos pos : positions.values()) {
            if (!pos.world().equals(key.world())) {
                continue;
            }
            double dx = pos.x() - bx;
            if (dx * dx > limit) {
                continue;
            }
            double dz = pos.z() - bz;
            if (dz * dz > limit) {
                continue;
            }
            double dy = pos.y() - by;
            if (dx * dx + dy * dy + dz * dz <= limit) {
                return true;
            }
        }
        return false;
    }
}
