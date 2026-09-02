package dev.havoc.spawners.feature;

import dev.havoc.spawners.HavocSpawners;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Reads upgrades.yml into an ordered list of tiers. Level 1 is always the stock spawner. */
public final class UpgradeTree {

    private final List<UpgradeTier> tiers = new ArrayList<>();

    public void reload(HavocSpawners plugin) {
        tiers.clear();
        File file = new File(plugin.getDataFolder(), "upgrades.yml");
        if (!file.exists()) {
            plugin.saveResource("upgrades.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("levels");
        if (root == null) {
            tiers.add(UpgradeTier.base());
            return;
        }
        List<String> keys = new ArrayList<>(root.getKeys(false));
        keys.sort((a, b) -> Integer.compare(parse(a), parse(b)));
        for (String key : keys) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            int level = parse(key);
            tiers.add(new UpgradeTier(
                    level,
                    section.getString("name", "Tier " + level),
                    section.getDouble("cost", 0.0D),
                    Math.max(0.05D, section.getDouble("delay-multiplier", 1.0D)),
                    Math.max(0.0D, section.getDouble("loot-multiplier", 1.0D)),
                    Math.max(0, section.getInt("bonus-pages", 0)),
                    Math.max(0L, section.getLong("bonus-exp-capacity", 0L)),
                    Math.max(0, section.getInt("bonus-range", 0))));
        }
        if (tiers.isEmpty()) {
            tiers.add(UpgradeTier.base());
        }
    }

    private static int parse(String key) {
        try {
            return Integer.parseInt(key.trim());
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    public UpgradeTier tier(int level) {
        UpgradeTier best = tiers.isEmpty() ? UpgradeTier.base() : tiers.get(0);
        for (UpgradeTier tier : tiers) {
            if (tier.level() <= level) {
                best = tier;
            }
        }
        return best;
    }

    public UpgradeTier next(int level) {
        for (UpgradeTier tier : tiers) {
            if (tier.level() > level) {
                return tier;
            }
        }
        return null;
    }

    public int maxLevel() {
        int max = 1;
        for (UpgradeTier tier : tiers) {
            max = Math.max(max, tier.level());
        }
        return max;
    }

    public List<UpgradeTier> tiers() {
        return List.copyOf(tiers);
    }
}
