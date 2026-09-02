package dev.havoc.spawners.loot;

import org.bukkit.Material;

/** A single weighted drop line. {@code chance} is a percentage (0-100). */
public record LootEntry(Material material, int min, int max, double chance) {

    public double averageAmount() {
        return (min + max) / 2.0D;
    }
}
