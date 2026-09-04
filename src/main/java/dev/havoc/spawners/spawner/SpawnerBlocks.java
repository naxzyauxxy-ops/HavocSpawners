package dev.havoc.spawners.spawner;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;

/**
 * Writes the vanilla spawner block's own state from our data.
 * <p>
 * The spawner item carries its type in persistent data, but the *block* gets its spinning mob from
 * vanilla {@code block_entity_data}. That data does not survive for every type: an item spawner has
 * no entity to put there at all, and some entity types do not round-trip through an item's block
 * state. When it is missing the client falls back to the default spawn data - a pig - which is why
 * bone block and shulker spawners looked like pig spawners even though the plugin knew better.
 * <p>
 * Rather than trusting the item, this applies the type to the block directly after we already know
 * what the spawner is. That fixes every type at once instead of special-casing the ones that broke.
 */
public final class SpawnerBlocks {

    private SpawnerBlocks() {
    }

    /**
     * Forces the block to display the spawner's real type.
     *
     * @return true when the block was updated
     */
    public static boolean apply(Block block, SpawnerData spawner) {
        if (block == null || spawner == null || block.getType() != Material.SPAWNER) {
            return false;
        }
        BlockState state = block.getState();
        if (!(state instanceof CreatureSpawner cage)) {
            return false;
        }
        try {
            if (spawner.isItemSpawner()) {
                // No mob to show: clear it so the cage renders empty instead of defaulting to a pig.
                cage.setSpawnedType(null);
            } else if (spawner.entityType() != null) {
                cage.setSpawnedType(spawner.entityType());
            } else {
                return false;
            }
            // Loot is virtual, so vanilla must never tick this cage itself.
            cage.setRequiredPlayerRange(0);
            cage.setDelay(Integer.MAX_VALUE);
            return state.update(true, false);
        } catch (Throwable ex) {
            return false;
        }
    }

    /** The mob the block itself claims to hold, or null when it holds none. Never guesses. */
    public static EntityType blockType(Block block) {
        if (block == null || block.getType() != Material.SPAWNER) {
            return null;
        }
        try {
            return block.getState(false) instanceof CreatureSpawner cage ? cage.getSpawnedType() : null;
        } catch (Throwable ex) {
            return null;
        }
    }

    /** True when the block's displayed type disagrees with the spawner's real type. */
    public static boolean mismatched(Block block, SpawnerData spawner) {
        if (block == null || spawner == null || block.getType() != Material.SPAWNER) {
            return false;
        }
        BlockState state = block.getState();
        if (!(state instanceof CreatureSpawner cage)) {
            return false;
        }
        try {
            return spawner.isItemSpawner()
                    ? cage.getSpawnedType() != null
                    : cage.getSpawnedType() != spawner.entityType();
        } catch (Throwable ex) {
            return false;
        }
    }
}
