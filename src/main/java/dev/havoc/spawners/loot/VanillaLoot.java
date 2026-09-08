package dev.havoc.spawners.loot;

import dev.havoc.spawners.spawner.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Learns a mob's drops from the server's own vanilla loot table.
 * <p>
 * This is what lets an unconfigured mob spawner still produce the right things. Rather than shipping
 * a hand-written table for every entity in the game - which would be wrong the day Mojang changes one
 * - the mob's real {@code minecraft:entities/<mob>} table is rolled a few dozen times and the results
 * are turned into the plugin's own drop entries: which materials appeared, how often, and in what
 * amounts. The answer is cached per entity type, so this runs once.
 * <p>
 * It is entirely best-effort. A loot table that needs a real killed entity, a world that is not
 * loaded, an entity with no table at all - each just yields nothing, and the caller falls back to
 * experience only rather than failing.
 */
final class VanillaLoot {

    private VanillaLoot() {
    }

    /** Rolls the mob's vanilla table {@code samples} times and turns the results into drop entries. */
    static List<LootEntry> sample(SpawnerData spawner, int samples) {
        EntityType type = spawner.entityType();
        if (type == null) {
            return List.of();
        }
        Location location = location(spawner);
        if (location == null) {
            // No loaded world to roll against. Returning empty here would be cached as "no drops",
            // so the caller treats an unloaded world as a reason to fall back, not a final answer.
            return List.of();
        }
        org.bukkit.loot.LootTable vanilla = vanillaTableFor(type);
        if (vanilla == null) {
            return List.of();
        }

        Map<Material, Tally> tallies = new LinkedHashMap<>();
        Random random = new Random();
        int rolls = 0;
        for (int i = 0; i < samples; i++) {
            Collection<ItemStack> loot = roll(vanilla, random, location);
            if (loot == null) {
                // The table refused - almost always because it wants a real killed entity. One
                // failure means every roll will fail, so stop rather than burn the whole budget.
                break;
            }
            rolls++;
            Map<Material, Integer> perRoll = new LinkedHashMap<>();
            for (ItemStack stack : loot) {
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                perRoll.merge(stack.getType(), stack.getAmount(), Integer::sum);
            }
            for (Map.Entry<Material, Integer> entry : perRoll.entrySet()) {
                tallies.computeIfAbsent(entry.getKey(), k -> new Tally()).record(entry.getValue());
            }
        }
        if (rolls == 0 || tallies.isEmpty()) {
            return List.of();
        }

        List<LootEntry> entries = new ArrayList<>();
        for (Map.Entry<Material, Tally> entry : tallies.entrySet()) {
            Tally tally = entry.getValue();
            double chance = 100.0D * tally.hits / rolls;
            entries.add(new LootEntry(entry.getKey(), tally.min, tally.max, chance));
        }
        return entries;
    }

    private static org.bukkit.loot.LootTable vanillaTableFor(EntityType type) {
        try {
            NamespacedKey typeKey = type.getKey();
            return Bukkit.getLootTable(NamespacedKey.minecraft("entities/" + typeKey.getKey()));
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Collection<ItemStack> roll(org.bukkit.loot.LootTable table, Random random,
                                              Location location) {
        try {
            return table.populateLoot(random, new LootContext.Builder(location).build());
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Location location(SpawnerData spawner) {
        try {
            return spawner.position() == null ? null : spawner.position().toLocation();
        } catch (Throwable ex) {
            return null;
        }
    }

    /** How often one material showed up, and the amount range it showed up in. */
    private static final class Tally {

        private int hits;
        private int min = Integer.MAX_VALUE;
        private int max;

        void record(int amount) {
            hits++;
            min = Math.min(min, amount);
            max = Math.max(max, amount);
        }
    }
}
