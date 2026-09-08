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
    /** Tables invented for types nobody configured. Concurrent: read from region threads. */
    private final Map<String, LootTable> autoTables = new java.util.concurrent.ConcurrentHashMap<>();
    private Material defaultIcon = Material.SPAWNER;

    private boolean autoEnabled = true;
    private int autoItemExp = 1;
    private int autoMobExp = 3;
    private int autoSamples = 40;
    private java.util.logging.Logger logger;

    public void reload(HavocSpawners plugin) {
        mobTables.clear();
        itemTables.clear();
        // Auto tables are derived from the configured ones, so a reload has to forget them too.
        autoTables.clear();
        logger = plugin.getLogger();
        autoEnabled = plugin.getConfig().getBoolean("loot.auto-generate", true);
        autoItemExp = Math.max(0, plugin.getConfig().getInt("loot.auto-item-exp", 1));
        autoMobExp = Math.max(0, plugin.getConfig().getInt("loot.auto-mob-exp", 3));
        autoSamples = Math.max(1, Math.min(500, plugin.getConfig().getInt("loot.auto-samples", 40)));
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

    /**
     * The loot table for a spawner, inventing one when nothing is configured.
     * <p>
     * Every spawner type has to produce <em>something</em> without an admin writing a table for it
     * first, because a spawner that silently drops nothing reads as a broken plugin rather than as a
     * missing config entry. So an unconfigured type is auto-generated once and cached:
     * <ul>
     *   <li>an <b>item spawner</b> drops its own material - a chest spawner spawns chests;</li>
     *   <li>a <b>mob spawner</b> is learned from the mob's own vanilla loot table.</li>
     * </ul>
     * Anything written in {@code mob_drops.yml} or {@code item_spawners.yml} always wins, so a
     * configured type is never second-guessed.
     */
    public LootTable tableFor(SpawnerData spawner) {
        String key = spawner.typeKey().toUpperCase(Locale.ROOT);
        LootTable table = spawner.isItemSpawner() ? itemTables.get(key) : mobTables.get(key);
        if (table != null) {
            return table;
        }
        if (!autoEnabled) {
            return LootTable.empty(key, defaultIcon);
        }
        LootTable cached = autoTables.get(key);
        if (cached != null) {
            return cached;
        }
        LootTable built = build(spawner, key);
        autoTables.put(key, built);
        return built;
    }

    /** Invents a table for a type nobody configured. Called once per type, then cached. */
    private LootTable build(SpawnerData spawner, String key) {
        if (spawner.isItemSpawner()) {
            Material material = spawner.itemMaterial();
            if (material == null || material.isAir() || !material.isItem()) {
                return LootTable.empty(key, defaultIcon);
            }
            log("item spawner " + key + " drops itself");
            return new LootTable(key, autoItemExp,
                    List.of(new LootEntry(material, 1, 1, 100.0D)), material);
        }
        List<LootEntry> learned = VanillaLoot.sample(spawner, autoSamples);
        if (learned.isEmpty()) {
            // The mob has no vanilla drops we could read (or the world is not loaded yet). It still
            // gives experience, and the type is named in the log so it can be configured by hand.
            log("mob " + key + " has no readable vanilla drops - experience only");
            return new LootTable(key, autoMobExp, List.of(), defaultIcon);
        }
        log("mob " + key + " learned " + learned.size() + " drops from its vanilla loot table");
        return new LootTable(key, autoMobExp, learned, defaultIcon);
    }

    private void log(String message) {
        if (logger != null) {
            logger.info("[auto-loot] " + message);
        }
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
