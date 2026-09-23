package dev.havoc.spawners.spawner;

import dev.havoc.spawners.HavocSpawners;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Puts real mobs into the world for a spawner in {@link SpawnMode#REAL}.
 * <p>
 * The dangerous part of "just spawn them for real" is that a stacked spawner's simulated mob count
 * is enormous by design - a ×26,000 stack asks for tens of thousands of mobs a cycle, which would
 * end the server. So the real count is capped twice over: never more than
 * {@code spawner.real.max-per-cycle} at once, and never past {@code spawner.real.max-nearby} living
 * entities already around the block. Stacking a REAL spawner therefore makes it spawn *up to* the
 * cap faster and more reliably, not without limit.
 * <p>
 * Everything here touches the world, so it must run on the region thread that owns the block.
 */
public final class RealSpawner {

    private RealSpawner() {
    }

    /**
     * Spawns up to the configured cap.
     *
     * @return how many entities (or dropped items) were actually created
     */
    public static int run(HavocSpawners plugin, SpawnerData spawner, int cycles) {
        Location origin = spawner.position() == null ? null : spawner.position().toLocation();
        if (origin == null || origin.getWorld() == null) {
            return 0;
        }
        World world = origin.getWorld();
        if (!world.isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) {
            return 0;
        }

        int wanted = wanted(spawner, cycles, plugin.settings().realMaxPerCycle);
        if (wanted <= 0) {
            return 0;
        }
        int radius = Math.max(1, plugin.settings().realSpawnRadius);

        if (spawner.isItemSpawner()) {
            return dropItems(spawner, origin, world, wanted);
        }
        EntityType type = spawner.entityType();
        if (type == null || !type.isSpawnable() || !type.isAlive()) {
            return 0;
        }
        // Count what is already standing around, so a spawner in an unlit cave cannot keep piling
        // mobs up while nobody kills them.
        int nearby = 0;
        int limit = Math.max(1, plugin.settings().realMaxNearby);
        try {
            for (Entity entity : world.getNearbyEntities(origin, radius + 4, radius + 4, radius + 4)) {
                if (entity.getType() == type && ++nearby >= limit) {
                    return 0;
                }
            }
        } catch (Throwable ex) {
            return 0;
        }

        int spawned = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < wanted && nearby + spawned < limit; i++) {
            Location at = scatter(origin, radius, random);
            if (!isFree(at)) {
                continue;
            }
            try {
                world.spawnEntity(at, type);
                spawned++;
            } catch (Throwable ex) {
                // An entity type the server refuses to place here - stop rather than spin.
                break;
            }
        }
        return spawned;
    }

    /** An item spawner in REAL mode throws its item on the floor instead of banking it. */
    private static int dropItems(SpawnerData spawner, Location origin, World world, int wanted) {
        Material material = spawner.itemMaterial();
        if (material == null || material.isAir() || !material.isItem()) {
            return 0;
        }
        int amount = Math.min(wanted, material.getMaxStackSize());
        try {
            Item dropped = world.dropItemNaturally(origin.clone().add(0.5D, 1.0D, 0.5D),
                    new ItemStack(material, amount));
            dropped.setPickupDelay(10);
            return 1;
        } catch (Throwable ex) {
            return 0;
        }
    }

    /**
     * How many to spawn this pass.
     * <p>
     * The simulated count scales with the stack; the real one deliberately does not. Stack size
     * still matters - a bigger stack reaches the cap every time rather than sometimes - but the cap
     * is what the server actually has to pay for.
     */
    private static int wanted(SpawnerData spawner, int cycles, int cap) {
        if (cap <= 0) {
            return 0;
        }
        int min = Math.max(0, spawner.minMobs());
        int max = Math.max(min, spawner.maxMobs());
        long rolled = max <= min ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        rolled = rolled * Math.max(1, cycles);
        return (int) Math.min(cap, rolled);
    }

    /** Vanilla scatters spawns around the block; so do we. */
    private static Location scatter(Location origin, int radius, ThreadLocalRandom random) {
        double x = origin.getBlockX() + 0.5D + random.nextDouble(-radius, radius + 1.0D);
        double y = origin.getBlockY() + random.nextInt(-1, 2);
        double z = origin.getBlockZ() + 0.5D + random.nextDouble(-radius, radius + 1.0D);
        return new Location(origin.getWorld(), x, y, z);
    }

    /** Two blocks of clear space, so mobs do not appear inside the wall behind the spawner. */
    private static boolean isFree(Location location) {
        try {
            return location.getBlock().isPassable() && location.clone().add(0.0D, 1.0D, 0.0D)
                    .getBlock().isPassable();
        } catch (Throwable ex) {
            return false;
        }
    }
}
