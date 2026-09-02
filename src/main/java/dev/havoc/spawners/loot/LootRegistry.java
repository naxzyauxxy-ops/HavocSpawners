package dev.havoc.spawners.loot;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.spawner.SpawnerData;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads mob_drops.yml and item_spawners.yml. */
public final class LootRegistry {

    private final Map<String, LootTable> mobTables = new HashMap<>();
    private final Map<String, LootTable> itemTables = new HashMap<>();
    private Material defaultIcon = Material.SPAWNER;

    public void reload(HavocSpawners plugin) {
        mobTables.clear();
        itemTables.clear();
        load(plugin, "mob_drops.yml", mobTables, false);
        load(plugin, "item_spawners.yml", itemTables, true);
    }

    private void load(HavocSpawners plugin, String name, Map<String, LootTable> target, boolean itemMode) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (itemMode) {
            Material icon = Material.matchMaterial(config.getString("default_material", "SPAWNER"));
            if (icon != null) {
                defaultIcon = icon;
            }
        }
        for (String key : config.getKeys(false)) {
            if (key.equalsIgnoreCase("default_material")) {
                continue;
            }
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            int exp = section.getInt("experience", 0);
            Material icon = Material.matchMaterial(section.getString("material", key));
            if (icon == null) {
                icon = defaultIcon;
            }
            List<LootEntry> entries = new ArrayList<>();
            ConfigurationSection loot = section.getConfigurationSection("loot");
            if (loot != null) {
                for (String dropKey : loot.getKeys(false)) {
                    ConfigurationSection drop = loot.getConfigurationSection(dropKey);
                    if (drop == null) {
                        continue;
                    }
                    Material material = Material.matchMaterial(dropKey.toUpperCase(Locale.ROOT));
                    if (material == null || material.isAir()) {
                        plugin.getLogger().warning("Unknown drop material '" + dropKey + "' in " + name);
                        continue;
                    }
                    int[] range = parseRange(drop.getString("amount", "1"));
                    double chance = drop.getDouble("chance", 100.0D);
                    entries.add(new LootEntry(material, range[0], range[1], chance));
                }
            }
            target.put(key.toUpperCase(Locale.ROOT), new LootTable(key.toUpperCase(Locale.ROOT), exp, entries, icon));
        }
        plugin.getLogger().info("Loaded " + target.size() + " loot tables from " + name + ".");
    }

    private static int[] parseRange(String raw) {
        if (raw == null || raw.isBlank()) {
            return new int[]{1, 1};
        }
        String trimmed = raw.trim();
        int dash = trimmed.indexOf('-');
        try {
            if (dash < 0) {
                int value = Integer.parseInt(trimmed);
                return new int[]{value, value};
            }
            int min = Integer.parseInt(trimmed.substring(0, dash).trim());
            int max = Integer.parseInt(trimmed.substring(dash + 1).trim());
            return new int[]{Math.min(min, max), Math.max(min, max)};
        } catch (NumberFormatException ex) {
            return new int[]{1, 1};
        }
    }

    public LootTable tableFor(SpawnerData spawner) {
        String key = spawner.typeKey().toUpperCase(Locale.ROOT);
        LootTable table = spawner.isItemSpawner() ? itemTables.get(key) : mobTables.get(key);
        return table == null ? LootTable.empty(key, defaultIcon) : table;
    }

    public boolean hasItemTable(Material material) {
        return material != null && itemTables.containsKey(material.name());
    }

    public Material defaultIcon() {
        return defaultIcon;
    }

    public Map<String, LootTable> itemTables() {
        return Map.copyOf(itemTables);
    }

    public Map<String, LootTable> mobTables() {
        return Map.copyOf(mobTables);
    }
}
