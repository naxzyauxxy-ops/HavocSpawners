package dev.havoc.spawners.spawner;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Immutable, hashable block coordinate. Cheaper and safer to key maps on than {@link Location}. */
public record BlockKey(String world, int x, int y, int z) {

    public static BlockKey of(Location location) {
        World world = location.getWorld();
        return new BlockKey(world == null ? "" : world.getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(this.world);
        if (world == null) {
            return null;
        }
        return new Location(world, x + 0.5D, y + 0.5D, z + 0.5D);
    }

    public Location toBlockLocation() {
        World world = Bukkit.getWorld(this.world);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    public boolean isLoaded() {
        World w = Bukkit.getWorld(world);
        return w != null && w.isChunkLoaded(x >> 4, z >> 4);
    }

    public String serialize() {
        return world + "," + x + "," + y + "," + z;
    }

    public static BlockKey deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length < 4) {
            return null;
        }
        try {
            // The world name itself may contain commas; rebuild it from the leading segments.
            int len = parts.length;
            int z = Integer.parseInt(parts[len - 1].trim());
            int y = Integer.parseInt(parts[len - 2].trim());
            int x = Integer.parseInt(parts[len - 3].trim());
            StringBuilder world = new StringBuilder();
            for (int i = 0; i < len - 3; i++) {
                if (i > 0) {
                    world.append(',');
                }
                world.append(parts[i]);
            }
            return new BlockKey(world.toString(), x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public String toString() {
        return world + " " + x + ", " + y + ", " + z;
    }
}
