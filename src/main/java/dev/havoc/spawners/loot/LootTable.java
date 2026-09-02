package dev.havoc.spawners.loot;

import org.bukkit.Material;

import java.util.List;

/**
 * Drops for one spawner type.
 *
 * @param key       entity type name, or material name for item spawners
 * @param exp       experience granted per simulated mob
 * @param entries   possible drops
 * @param headIcon  material used to represent this spawner in dialogs
 */
public record LootTable(String key, int exp, List<LootEntry> entries, Material headIcon) {

    public static LootTable empty(String key, Material icon) {
        return new LootTable(key, 0, List.of(), icon);
    }
}
